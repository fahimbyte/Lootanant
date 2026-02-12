package imperfect.lootanant.service;

import imperfect.lootanant.model.GameRoom;
import imperfect.lootanant.model.Player;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class GameService {

    private final ConcurrentHashMap<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Random random = new Random();

    public GameService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public GameRoom createRoom(String hostName) {
        String code = generateRoomCode();
        String hostId = UUID.randomUUID().toString();
        GameRoom room = new GameRoom(code, hostId);
        Player host = new Player(hostId, hostName, false);
        room.getPlayers().add(host);
        rooms.put(code, room);
        return room;
    }

    public GameRoom getRoom(String code) {
        return rooms.get(code);
    }

    public Player joinRoom(String code, String displayName) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isStarted() || room.getPlayers().size() >= 8) return null;
        String playerId = UUID.randomUUID().toString();
        Player player = new Player(playerId, displayName, false);
        room.getPlayers().add(player);
        broadcastState(room);
        return player;
    }

    public boolean renamePlayer(String code, String playerId, String newName) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isStarted()) return false;
        Player player = room.getPlayerById(playerId);
        if (player == null || player.isCpu()) return false;
        player.setDisplayName(newName);
        broadcastState(room);
        return true;
    }

    public Player addCpu(String code, String hostId) {
        GameRoom room = rooms.get(code);
        if (room == null || !room.getHostId().equals(hostId) || room.getPlayers().size() >= 8) return null;
        int cpuNum = (int) room.getPlayers().stream().filter(Player::isCpu).count() + 1;
        String cpuId = "cpu-" + UUID.randomUUID();
        Player cpu = new Player(cpuId, "CPU " + cpuNum, true);
        room.getPlayers().add(cpu);
        broadcastState(room);
        return cpu;
    }

    public boolean updateSettings(String code, String hostId, int winNetWorth, int startingAntCents) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isStarted() || !room.getHostId().equals(hostId)) return false;
        if (winNetWorth < 10 || winNetWorth > 200) return false;
        if (startingAntCents < 1 || startingAntCents > 100) return false;
        room.setWinNetWorth(winNetWorth);
        room.setStartingAntCents(startingAntCents);
        broadcastState(room);
        return true;
    }

    public boolean startGame(String code, String hostId) {
        GameRoom room = rooms.get(code);
        if (room == null || !room.getHostId().equals(hostId) || room.getPlayers().size() < 2) return false;
        // Apply starting ant-cents to all players
        for (Player p : room.getPlayers()) {
            p.setAntCents(room.getStartingAntCents());
        }
        room.setStarted(true);
        room.setStartingPlayerIndex(0);
        // Notify all players that game has started
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/gameStarted",
                (Object) Map.of("started", true));
        startNewRound(room);
        return true;
    }

    public synchronized boolean placeBid(String code, String playerId, int bidAmount) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isFinished() || !room.isStarted()) return false;

        Player current = room.getPlayers().get(room.getCurrentPlayerIndex());
        if (!current.getId().equals(playerId)) return false;
        if (bidAmount <= room.getCurrentHighBid() || bidAmount > current.getAntCents()) return false;

        cancelTimer(room);

        // Refund previous high bidder
        refundHighBidder(room);

        // Deduct from current bidder
        current.setAntCents(current.getAntCents() - bidAmount);
        room.setCurrentHighBid(bidAmount);
        room.setCurrentHighBidderId(playerId);

        broadcastState(room);
        advanceToNextBidder(room);
        return true;
    }

    public synchronized boolean pass(String code, String playerId) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isFinished() || !room.isStarted()) return false;

        Player current = room.getPlayers().get(room.getCurrentPlayerIndex());
        if (!current.getId().equals(playerId)) return false;

        cancelTimer(room);
        current.setPassedThisRound(true);

        // Check if only one active bidder remains
        if (room.activeBiddersCount() <= 1) {
            resolveRound(room);
        } else {
            broadcastState(room);
            advanceToNextBidder(room);
        }
        return true;
    }

    private void refundHighBidder(GameRoom room) {
        if (room.getCurrentHighBidderId() != null) {
            Player prev = room.getPlayerById(room.getCurrentHighBidderId());
            if (prev != null) {
                prev.setAntCents(prev.getAntCents() + room.getCurrentHighBid());
            }
        }
    }

    private void resolveRound(GameRoom room) {
        cancelTimer(room);
        String winnerId = room.getCurrentHighBidderId();
        Map<String, Object> roundResult = new HashMap<>();

        if (winnerId != null) {
            Player winner = room.getPlayerById(winnerId);
            // Winner already paid (money deducted during bid); add deed value to net worth
            winner.setNetWorth(winner.getNetWorth() + room.getCurrentDeedValue());
            roundResult.put("roundWinner", winner.getDisplayName());
            roundResult.put("deedValue", room.getCurrentDeedValue());
            roundResult.put("bidPaid", room.getCurrentHighBid());

            // Check win condition
            if (winner.getNetWorth() >= room.getWinNetWorth()) {
                room.setFinished(true);
                room.setWinnerId(winnerId);
                broadcastState(room);
                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/winner",
                        (Object) Map.of("winnerId", winnerId, "winnerName", winner.getDisplayName()));
                return;
            }
        } else {
            // No one bid - card discarded
            roundResult.put("roundWinner", "none");
            roundResult.put("deedValue", room.getCurrentDeedValue());
            roundResult.put("discarded", true);
        }

        // Income phase: +1 ant-cent for every player
        for (Player p : room.getPlayers()) {
            p.setAntCents(p.getAntCents() + 1);
        }

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/roundResult", (Object) roundResult);

        // Advance starting player clockwise
        room.setStartingPlayerIndex((room.getStartingPlayerIndex() + 1) % room.getPlayers().size());

        // Small delay before next round
        scheduler.schedule(() -> startNewRound(room), 2, TimeUnit.SECONDS);
    }

    private void startNewRound(GameRoom room) {
        if (room.isFinished()) return;

        // Reset round state
        int deedValue = random.nextInt(11) + 1;
        room.setCurrentDeedValue(deedValue);
        room.setCurrentHighBid(0);
        room.setCurrentHighBidderId(null);
        room.setCurrentPlayerIndex(room.getStartingPlayerIndex());
        for (Player p : room.getPlayers()) {
            p.setPassedThisRound(false);
        }

        broadcastState(room);
        startTurnTimer(room);
        handleCpuTurnIfNeeded(room);
    }

    private void advanceToNextBidder(GameRoom room) {
        int size = room.getPlayers().size();
        int idx = room.getCurrentPlayerIndex();
        for (int i = 0; i < size; i++) {
            idx = (idx + 1) % size;
            Player p = room.getPlayers().get(idx);
            if (!p.isPassedThisRound()) {
                room.setCurrentPlayerIndex(idx);
                broadcastState(room);
                startTurnTimer(room);
                handleCpuTurnIfNeeded(room);
                return;
            }
        }
        // Everyone passed
        resolveRound(room);
    }

    private void handleCpuTurnIfNeeded(GameRoom room) {
        Player current = room.getPlayers().get(room.getCurrentPlayerIndex());
        if (!current.isCpu()) return;

        // CPU AI: Greedy - bid if card value is high (>=5) and has 30% more than current bid
        scheduler.schedule(() -> {
            synchronized (this) {
                if (room.isFinished()) return;
                Player cpu = room.getPlayers().get(room.getCurrentPlayerIndex());
                if (!cpu.getId().equals(current.getId())) return;

                int minBid = room.getCurrentHighBid() + 1;
                boolean shouldBid = room.getCurrentDeedValue() >= 5
                        && cpu.getAntCents() >= minBid
                        && cpu.getAntCents() >= (int) (room.getCurrentHighBid() * 1.3) + 1;

                // Also bid on lower cards if very cheap
                if (!shouldBid && room.getCurrentHighBid() == 0 && cpu.getAntCents() >= 1) {
                    shouldBid = random.nextInt(3) == 0; // 33% chance to bid 1 on any card
                }

                if (shouldBid) {
                    int bid = minBid;
                    placeBid(room.getRoomCode(), cpu.getId(), bid);
                } else {
                    pass(room.getRoomCode(), cpu.getId());
                }
            }
        }, 1, TimeUnit.SECONDS);
    }

    private void startTurnTimer(GameRoom room) {
        cancelTimer(room);
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            synchronized (this) {
                if (room.isFinished()) return;
                Player current = room.getPlayers().get(room.getCurrentPlayerIndex());
                // Auto-pass on timeout
                pass(room.getRoomCode(), current.getId());
            }
        }, 20, TimeUnit.SECONDS);
        room.setTurnTimer(timer);
    }

    private void cancelTimer(GameRoom room) {
        if (room.getTurnTimer() != null) {
            room.getTurnTimer().cancel(false);
            room.setTurnTimer(null);
        }
    }

    public Map<String, Object> getPublicState(GameRoom room, String playerId) {
        Map<String, Object> state = new HashMap<>();
        state.put("roomCode", room.getRoomCode());
        state.put("started", room.isStarted());
        state.put("finished", room.isFinished());
        state.put("currentDeedValue", room.getCurrentDeedValue());
        state.put("currentHighBid", room.getCurrentHighBid());
        state.put("currentHighBidderId", room.getCurrentHighBidderId());
        state.put("winnerId", room.getWinnerId());

        Player currentPlayer = room.isStarted() && !room.isFinished()
                ? room.getPlayers().get(room.getCurrentPlayerIndex()) : null;
        state.put("currentTurnPlayerId", currentPlayer != null ? currentPlayer.getId() : null);

        List<Map<String, Object>> playerList = new ArrayList<>();
        for (Player p : room.getPlayers()) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", p.getId());
            pm.put("displayName", p.getDisplayName());
            pm.put("netWorth", p.getNetWorth());
            pm.put("cpu", p.isCpu());
            pm.put("passed", p.isPassedThisRound());
            // Only show own ant-cents
            if (p.getId().equals(playerId)) {
                pm.put("antCents", p.getAntCents());
                pm.put("isYou", true);
            } else {
                pm.put("antCents", "???");
                pm.put("isYou", false);
            }
            playerList.add(pm);
        }
        state.put("players", playerList);
        state.put("hostId", room.getHostId());
        state.put("winNetWorth", room.getWinNetWorth());
        state.put("startingAntCents", room.getStartingAntCents());
        return state;
    }

    private void broadcastState(GameRoom room) {
        for (Player p : room.getPlayers()) {
            if (!p.isCpu()) {
                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/state/" + p.getId(),
                        (Object) getPublicState(room, p.getId()));
            }
        }
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        String code = sb.toString();
        return rooms.containsKey(code) ? generateRoomCode() : code;
    }
}
