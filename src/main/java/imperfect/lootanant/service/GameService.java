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

    public GameRoom createRoom(String hostName, String gameMode) {
        String code = generateRoomCode();
        String hostId = UUID.randomUUID().toString();
        GameRoom room = new GameRoom(code, hostId);
        if ("rage".equalsIgnoreCase(gameMode)) {
            room.setGameMode("rage");
        }
        Player host = new Player(hostId, hostName, false);
        room.getPlayers().add(host);
        rooms.put(code, room);
        return room;
    }

    public GameRoom getRoom(String code) {
        return rooms.get(code);
    }

    public List<Map<String, String>> getAvailableRooms() {
        List<Map<String, String>> available = new ArrayList<>();
        rooms.forEach((code, room) -> {
            if (!room.isFinished()) {
                available.add(Map.of(
                        "roomCode", code,
                        "hostName", room.getPlayers().get(0).getDisplayName(),
                        "gameMode", room.getGameMode()
                ));
            }
        });
        return available;
    }

    public boolean discardRoom(String code, String hostId) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isStarted()) return false;
        if (!room.getHostId().equals(hostId)) return false;
        rooms.remove(code);
        // Notify all players in the room that it's been discarded
        messagingTemplate.convertAndSend("/topic/room/" + code + "/roomDiscarded",
                (Object) Map.of("discarded", true));
        return true;
    }

    public Player joinRoom(String code, String displayName) {
        GameRoom room = rooms.get(code);
        if (room == null || room.getPlayers().size() >= 8) return null;
        if (room.isStarted()) return null;
        String playerId = UUID.randomUUID().toString();
        Player player = new Player(playerId, displayName, false);
        room.getPlayers().add(player);
        broadcastState(room);
        return player;
    }

    public String joinAsSpectator(String code) {
        GameRoom room = rooms.get(code);
        if (room == null) return null;
        String spectatorId = "spec-" + UUID.randomUUID();
        room.getSpectatorIds().add(spectatorId);
        broadcastState(room);
        return spectatorId;
    }

    public Player reconnect(String code, String playerId) {
        GameRoom room = rooms.get(code);
        if (room == null) return null;
        Player p = room.getPlayerById(playerId);
        if (p != null) {
            p.setConnected(true);
            p.setCpu(false); // Take back control from CPU if it was playing
            broadcastState(room);
            return p;
        }
        return null;
    }

    public void leaveRoom(String code, String playerId) {
        GameRoom room = rooms.get(code);
        if (room == null) return;
        Player p = room.getPlayerById(playerId);
        if (p != null) {
            p.setCents(0);
            p.setNetWorth(0);
            p.setConnected(false);
            p.setCpu(true); // Let CPU take over
            broadcastState(room);
        }
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

    public boolean removeCpu(String code, String hostId, String cpuId) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isStarted() || !room.getHostId().equals(hostId)) return false;
        Player cpu = room.getPlayerById(cpuId);
        if (cpu == null || !cpu.isCpu()) return false;
        room.getPlayers().remove(cpu);
        broadcastState(room);
        return true;
    }

    public boolean updateSettings(String code, String hostId, int winNetWorth, int startingCents) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isStarted() || !room.getHostId().equals(hostId)) return false;
        if (winNetWorth < 10 || winNetWorth > 200) return false;
        if (startingCents < 1 || startingCents > 100) return false;
        room.setWinNetWorth(winNetWorth);
        room.setStartingCents(startingCents);
        broadcastState(room);
        return true;
    }

    public boolean startGame(String code, String hostId) {
        GameRoom room = rooms.get(code);
        if (room == null || !room.getHostId().equals(hostId) || room.getPlayers().size() < 2) return false;
        // Apply starting cents to all players
        for (Player p : room.getPlayers()) {
            p.setCents(room.getStartingCents());
        }
        room.setStarted(true);
        room.setStartingPlayerIndex(0);
        room.setRoundNumber(0);
        // Notify all players that game has started
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/gameStarted",
                (Object) Map.of("started", true, "gameMode", room.getGameMode()));
        startNewRound(room);
        return true;
    }

    public synchronized boolean placeBid(String code, String playerId, int bidAmount) {
        GameRoom room = rooms.get(code);
        if (room == null || room.isFinished() || !room.isStarted()) return false;

        Player current = room.getPlayers().get(room.getCurrentPlayerIndex());
        if (!current.getId().equals(playerId)) return false;
        if (bidAmount <= room.getCurrentHighBid() || bidAmount > current.getCents()) return false;

        cancelTimer(room);

        // Refund previous high bidder
        refundHighBidder(room);

        // Deduct from current bidder
        current.setCents(current.getCents() - bidAmount);
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

    // ── Rage Mode: Bribe ──
    public synchronized Map<String, Object> bribePlayer(String code, String briberId, String targetId, int amount) {
        GameRoom room = rooms.get(code);
        if (room == null || !room.isRageMode() || room.isFinished() || !room.isStarted()) {
            return Map.of("error", "Cannot bribe in this room");
        }
        Player briber = room.getPlayerById(briberId);
        Player target = room.getPlayerById(targetId);
        if (briber == null || target == null || briber.getId().equals(target.getId())) {
            return Map.of("error", "Invalid bribe target");
        }
        if (amount < 1 || amount > 4) {
            return Map.of("error", "Bribe amount must be between 1 and 4");
        }
        if (briber.getCents() < amount) {
            return Map.of("error", "Not enough Ant-cents to bribe");
        }
        // Cap: target's bribe tax can't exceed 40% (each cent = +10%)
        int maxAdditional = (40 - target.getBribeTaxPercent()) / 10;
        if (maxAdditional <= 0) {
            return Map.of("error", "Target already at max bribe tax (40%)");
        }
        int effectiveAmount = Math.min(amount, maxAdditional);

        // Deduct cents from briber, add to vault
        briber.setCents(briber.getCents() - effectiveAmount);
        room.setKingsVault(room.getKingsVault() + effectiveAmount);

        // Add 10% tax per cent to target (capped at 40%)
        target.setBribeTaxPercent(target.getBribeTaxPercent() + effectiveAmount * 10);

        // Anonymous notification
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/rageEvent",
                (Object) Map.of(
                        "type", "bribe",
                        "message", "Someone whispered to the King... " + target.getDisplayName() + "'s tax has increased by +" + (effectiveAmount * 10) + "%!",
                        "targetId", targetId
                ));

        broadcastState(room);
        return Map.of("status", "bribed", "effectiveAmount", effectiveAmount);
    }

    // ── Rage Mode: King's Loan ──
    public synchronized Map<String, Object> takeLoan(String code, String playerId) {
        GameRoom room = rooms.get(code);
        if (room == null || !room.isRageMode() || room.isFinished() || !room.isStarted()) {
            return Map.of("error", "Cannot take loan in this room");
        }
        Player player = room.getPlayerById(playerId);
        if (player == null) {
            return Map.of("error", "Player not found");
        }
        if (player.getCents() >= 3) {
            return Map.of("error", "You must have fewer than 3 Ant-cents to take a loan");
        }
        int loanAmount = 5;
        if (room.getKingsVault() < loanAmount) {
            return Map.of("error", "Not enough Ant-cents in the King's Vault (need 5¢)");
        }

        // Transfer 5¢ from vault to player
        room.setKingsVault(room.getKingsVault() - loanAmount);
        player.setCents(player.getCents() + loanAmount);

        // Penalty: 35% of net worth (rounded to nearest whole number, minimum 3 karats)
        int penalty = Math.max(3, (int) Math.round(player.getNetWorth() * 0.35));
        player.setNetWorth(Math.max(0, player.getNetWorth() - penalty));

        // Public notification
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/rageEvent",
                (Object) Map.of(
                        "type", "loan",
                        "message", player.getDisplayName() + " took a desperate loan! Their net worth dropped by " + penalty + "!",
                        "playerId", playerId,
                        "penalty", penalty,
                        "amount", loanAmount
                ));

        broadcastState(room);
        return Map.of("status", "loan_taken", "penalty", penalty, "amount", loanAmount);
    }

    private void refundHighBidder(GameRoom room) {
        if (room.getCurrentHighBidderId() != null) {
            Player prev = room.getPlayerById(room.getCurrentHighBidderId());
            if (prev != null) {
                prev.setCents(prev.getCents() + room.getCurrentHighBid());
            }
        }
    }

    private void resolveRound(GameRoom room) {
        cancelTimer(room);
        String winnerId = room.getCurrentHighBidderId();
        Map<String, Object> roundResult = new HashMap<>();

        // Check if this is a Jackpot Round (every 10th round in Rage mode)
        boolean isJackpotRound = room.isRageMode() && room.getRoundNumber() > 0 && room.getRoundNumber() % 11 == 0;
        roundResult.put("jackpotRound", isJackpotRound);

        if (winnerId != null) {
            Player winner = room.getPlayerById(winnerId);
            // Winner already paid (money deducted during bid); add purity to net worth
            winner.setNetWorth(winner.getNetWorth() + room.getCurrentGoldBarPurity());
            roundResult.put("roundWinner", winner.getDisplayName());
            roundResult.put("purity", room.getCurrentGoldBarPurity());
            roundResult.put("bidPaid", room.getCurrentHighBid());

            // Vault Jackpot: winner gets vault cents on every 11th round (max 20)
            if (isJackpotRound && room.getKingsVault() > 0) {
                int jackpotAmount = Math.min(room.getKingsVault(), 20);
                winner.setCents(winner.getCents() + jackpotAmount);
                room.setKingsVault(room.getKingsVault() - jackpotAmount);
                roundResult.put("jackpotAmount", jackpotAmount);
            }

            // Check win condition
            if (winner.getNetWorth() >= room.getWinNetWorth()) {
                room.setFinished(true);
                room.setWinnerId(winnerId);
                cancelTimer(room); // Ensure timer is cancelled
                broadcastState(room);
                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/winner",
                        (Object) Map.of("winnerId", winnerId, "winnerName", winner.getDisplayName()));
                return;
            }
        } else {
            // No one bid - gold bar discarded
            roundResult.put("roundWinner", "none");
            roundResult.put("purity", room.getCurrentGoldBarPurity());
            roundResult.put("discarded", true);
            // Jackpot round with no bids: vault stays, no jackpot awarded
        }

        // Income phase: +1 cent for every player
        for (Player p : room.getPlayers()) {
            p.setCents(p.getCents() + 1);
        }

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/roundResult", (Object) roundResult);

        // Broadcast income phase event for coin animation (after round result banner disappears)
        scheduler.schedule(() -> {
            if (room.isFinished()) return;
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/incomePhase",
                    (Object) Map.of("amount", 1, "message", "You Got: +1¢"));
            broadcastState(room);
        }, 5, TimeUnit.SECONDS);

        // Advance starting player clockwise
        room.setStartingPlayerIndex((room.getStartingPlayerIndex() + 1) % room.getPlayers().size());

        // Rage mode: Check if taxation phase should trigger (every 5 rounds)
        if (room.isRageMode() && room.getRoundNumber() % 5 == 0 && room.getRoundNumber() > 0) {
            // Delay taxation phase to show after round result + income animation
            scheduler.schedule(() -> executeTaxationPhase(room), 8, TimeUnit.SECONDS);
        } else {
            // Delay before next round for players to read results + income animation
            scheduler.schedule(() -> startNewRound(room), 8, TimeUnit.SECONDS);
        }
    }

    // ── Rage Mode: Taxation Phase ──
    private synchronized void executeTaxationPhase(GameRoom room) {
        if (room.isFinished()) return;

        room.setTaxationPhaseActive(true);
        Map<String, Object> taxResult = new HashMap<>();
        List<Map<String, Object>> taxDetails = new ArrayList<>();
        int totalTaxCollected = 0;

        for (Player p : room.getPlayers()) {
            int baseTaxPercent = 25;
            int bribeTax = p.getBribeTaxPercent();
            int totalTaxPercent = baseTaxPercent + bribeTax;
            // Tax is calculated based on net worth, then deducted from cents
            int taxAmount = (int) Math.floor(p.getNetWorth() * totalTaxPercent / 100.0);

            // Cannot deduct more cents than the player has
            if (taxAmount > p.getCents()) {
                taxAmount = p.getCents();
            }

            p.setCents(p.getCents() - taxAmount);
            totalTaxCollected += taxAmount;

            Map<String, Object> detail = new HashMap<>();
            detail.put("playerId", p.getId());
            detail.put("playerName", p.getDisplayName());
            detail.put("taxPercent", totalTaxPercent);
            detail.put("taxAmount", taxAmount);
            detail.put("hadBribe", bribeTax > 0);
            taxDetails.add(detail);

            // Reset bribe tax after taxation
            p.setBribeTaxPercent(0);
        }

        room.setKingsVault(room.getKingsVault() + totalTaxCollected);
        room.setTaxationPhaseActive(false);

        taxResult.put("type", "taxation");
        taxResult.put("details", taxDetails);
        taxResult.put("totalCollected", totalTaxCollected);
        taxResult.put("vaultTotal", room.getKingsVault());

        // Set waiting for confirmation BEFORE sending events so frontend doesn't close overlay
        room.setWaitingForTaxConfirmation(true);
        room.getTaxConfirmedPlayerIds().clear();
        // Auto-confirm for CPU players
        for (Player p : room.getPlayers()) {
            if (p.isCpu()) {
                room.getTaxConfirmedPlayerIds().add(p.getId());
            }
        }

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/rageEvent", (Object) taxResult);
        broadcastState(room);
        // Fallback: auto-continue after 30 seconds if not all confirmed
        scheduler.schedule(() -> {
            synchronized (this) {
                if (room.isWaitingForTaxConfirmation() && !room.isFinished()) {
                    room.setWaitingForTaxConfirmation(false);
                    room.getTaxConfirmedPlayerIds().clear();
                    broadcastState(room);
                    startNewRound(room);
                }
            }
        }, 30, TimeUnit.SECONDS);
    }

    // ── Rage Mode: Confirm Tax ──
    public synchronized Map<String, Object> confirmTax(String code, String playerId) {
        GameRoom room = rooms.get(code);
        if (room == null || !room.isWaitingForTaxConfirmation()) {
            return Map.of("error", "Not waiting for tax confirmation");
        }
        room.getTaxConfirmedPlayerIds().add(playerId);
        broadcastState(room);

        // Check if all human players confirmed
        long humanCount = room.getPlayers().stream().filter(p -> !p.isCpu()).count();
        long confirmedHumans = room.getPlayers().stream()
                .filter(p -> !p.isCpu() && room.getTaxConfirmedPlayerIds().contains(p.getId()))
                .count();
        if (confirmedHumans >= humanCount) {
            room.setWaitingForTaxConfirmation(false);
            room.getTaxConfirmedPlayerIds().clear();
            broadcastState(room);
            startNewRound(room);
        }
        return Map.of("status", "confirmed");
    }

    private synchronized void startNewRound(GameRoom room) {
        if (room.isFinished()) return;

        // Increment round number
        room.setRoundNumber(room.getRoundNumber() + 1);

        // Reset round state
        int purity = random.nextInt(24) + 1;
        room.setCurrentGoldBarPurity(purity);
        room.setCurrentHighBid(0);
        room.setCurrentHighBidderId(null);
        room.setCurrentPlayerIndex(room.getStartingPlayerIndex());
        for (Player p : room.getPlayers()) {
            p.setPassedThisRound(false);
        }

        // CPU Rage mode actions (bribing & loans) at start of each round
        executeCpuRageActions(room);

        broadcastState(room);
        startTurnTimer(room);
        handleCpuTurnIfNeeded(room);
    }

    private void advanceToNextBidder(GameRoom room) {
        int size = room.getPlayers().size();
        int idx = room.getCurrentPlayerIndex();
        String highBidderId = room.getCurrentHighBidderId();
        for (int i = 0; i < size; i++) {
            idx = (idx + 1) % size;
            Player p = room.getPlayers().get(idx);
            if (!p.isPassedThisRound()) {
                // Skip the current highest bidder — they can't outbid themselves
                if (p.getId().equals(highBidderId)) {
                    // If this is the only active bidder left, resolve
                    if (room.activeBiddersCount() <= 1 || isOnlyNonPassedBesideHighBidder(room, highBidderId)) {
                        resolveRound(room);
                        return;
                    }
                    continue;
                }
                room.setCurrentPlayerIndex(idx);
                broadcastState(room);
                startTurnTimer(room);
                handleCpuTurnIfNeeded(room);
                return;
            }
        }
        // Everyone passed (or only high bidder remains)
        resolveRound(room);
    }

    private boolean isOnlyNonPassedBesideHighBidder(GameRoom room, String highBidderId) {
        for (Player p : room.getPlayers()) {
            if (!p.isPassedThisRound() && !p.getId().equals(highBidderId)) {
                return false;
            }
        }
        return true;
    }

    private void handleCpuTurnIfNeeded(GameRoom room) {
        Player current = room.getPlayers().get(room.getCurrentPlayerIndex());
        if (!current.isCpu()) return;

        // Safety check: if this CPU has already passed, skip immediately
        if (current.isPassedThisRound()) {
            advanceToNextBidder(room);
            return;
        }

        final String cpuId = current.getId();
        final int expectedIndex = room.getCurrentPlayerIndex();
        scheduler.schedule(() -> {
            synchronized (this) {
                if (room.isFinished()) return;
                // Verify the turn hasn't moved to a different player
                if (room.getCurrentPlayerIndex() != expectedIndex) return;
                Player cpu = room.getPlayers().get(room.getCurrentPlayerIndex());
                if (!cpu.getId().equals(cpuId)) return;
                if (cpu.isPassedThisRound()) return;
                executeCpuTurn(room, cpu);
            }
        }, 2 + random.nextInt(3), TimeUnit.SECONDS);
    }

    // ── Advanced CPU AI ──
    private void executeCpuTurn(GameRoom room, Player cpu) {
        int purity = room.getCurrentGoldBarPurity();
        int minBid = room.getCurrentHighBid() + 1;
        int myCents = cpu.getCents();
        int myNW = cpu.getNetWorth();
        int targetNW = room.getWinNetWorth();
        int activeBidders = room.activeBiddersCount();

        // Find the leading human player and closest competitor
        Player leadingHuman = null;
        Player closestRival = null;
        int maxHumanNW = -1;
        int maxRivalNW = -1;
        for (Player p : room.getPlayers()) {
            if (!p.isCpu() && p.getNetWorth() > maxHumanNW) {
                maxHumanNW = p.getNetWorth();
                leadingHuman = p;
            }
            if (!p.getId().equals(cpu.getId()) && p.getNetWorth() > maxRivalNW) {
                maxRivalNW = p.getNetWorth();
                closestRival = p;
            }
        }

        // === STRATEGY 1: Win condition — if this bar wins the game, go all-in ===
        if (myNW + purity >= targetNW && myCents >= minBid) {
            int aggressiveBid = Math.min(myCents, minBid + (int)(myCents * 0.6));
            placeBid(room.getRoomCode(), cpu.getId(), Math.max(minBid, aggressiveBid));
            return;
        }

        // === STRATEGY 2: Block human from winning ===
        if (leadingHuman != null && leadingHuman.getNetWorth() + purity >= targetNW) {
            // Human could win this round — block aggressively
            if (myCents >= minBid) {
                // Willing to spend up to 80% of cents to block
                int blockBid = Math.min(myCents, minBid + (int)(myCents * 0.5));
                placeBid(room.getRoomCode(), cpu.getId(), Math.max(minBid, blockBid));
                return;
            }
        }

        // === STRATEGY 3: Block any rival close to winning ===
        if (closestRival != null && closestRival.getNetWorth() + purity >= targetNW && myCents >= minBid) {
            int blockBid = Math.min(myCents, minBid + (int)(myCents * 0.4));
            placeBid(room.getRoomCode(), cpu.getId(), Math.max(minBid, blockBid));
            return;
        }

        // === Value assessment ===
        // How valuable is this card relative to cost?
        double valueRatio = (double) purity / Math.max(1, minBid);
        // How close am I to winning? (urgency factor)
        double progressRatio = (double) myNW / targetNW;
        // How much of my money would this cost?
        double costRatio = (minBid > 0) ? (double) minBid / Math.max(1, myCents) : 0;

        // Jackpot round bonus: vault cents make the round much more valuable
        boolean isJackpotRound = room.isRageMode() && room.getRoundNumber() > 0 && room.getRoundNumber() % 11 == 0;
        int effectiveValue = purity;
        if (isJackpotRound && room.getKingsVault() > 0) {
            // Jackpot rounds are extremely valuable — factor in vault cents
            effectiveValue += room.getKingsVault();
            valueRatio = (double) effectiveValue / Math.max(1, minBid);
        }

        // === STRATEGY 4: High-value cards — bid aggressively ===
        if (purity >= 18 && myCents >= minBid && costRatio < 0.85) {
            // Premium cards: bid with strategic increment
            int increment = Math.max(1, (int)(myCents * 0.15));
            int bid = Math.min(myCents, minBid + increment);
            placeBid(room.getRoomCode(), cpu.getId(), bid);
            return;
        }

        // === STRATEGY 5: Good value — bid if price is right ===
        if (purity >= 10 && valueRatio >= 1.5 && myCents >= minBid && costRatio < 0.7) {
            int increment = Math.max(1, (int)(myCents * 0.1));
            int bid = Math.min(myCents, minBid + increment);
            placeBid(room.getRoomCode(), cpu.getId(), bid);
            return;
        }

        // === STRATEGY 6: Cheap steals — always contest low bids ===
        if (room.getCurrentHighBid() == 0 && myCents >= 1) {
            // Opening bid: bid on anything worth 5+ purity
            if (purity >= 5) {
                placeBid(room.getRoomCode(), cpu.getId(), 1);
                return;
            }
            // Low purity: still bid sometimes to drain opponents
            if (random.nextInt(3) == 0) {
                placeBid(room.getRoomCode(), cpu.getId(), 1);
                return;
            }
        }

        // === STRATEGY 7: Mid-game pressure — outbid if affordable ===
        if (purity >= 8 && minBid <= 3 && myCents >= minBid && costRatio < 0.5) {
            placeBid(room.getRoomCode(), cpu.getId(), minBid);
            return;
        }

        // === STRATEGY 8: Endgame aggression — when close to winning, bid more ===
        if (progressRatio >= 0.6 && purity >= 6 && myCents >= minBid && costRatio < 0.6) {
            int bid = Math.min(myCents, minBid + 1);
            placeBid(room.getRoomCode(), cpu.getId(), bid);
            return;
        }

        // === STRATEGY 9: Force opponents to overpay ===
        // If only 2 bidders left and bid is still low relative to card value, push the price up
        if (activeBidders == 2 && purity >= 10 && valueRatio >= 2.0 && myCents >= minBid && costRatio < 0.5) {
            placeBid(room.getRoomCode(), cpu.getId(), minBid);
            return;
        }

        // === Default: Pass ===
        pass(room.getRoomCode(), cpu.getId());
    }

    // ── CPU Rage Mode Actions (bribing & loans) — called periodically ──
    private void executeCpuRageActions(GameRoom room) {
        if (!room.isRageMode() || room.isFinished() || !room.isStarted()) return;

        for (Player cpu : room.getPlayers()) {
            if (!cpu.isCpu()) continue;

            // === Bribe Strategy: target the leading human player before tax rounds ===
            int roundsUntilTax = room.getRoundNumber() % 5;
            int roundsLeft = (roundsUntilTax == 0) ? 0 : 5 - roundsUntilTax;
            if (roundsLeft <= 2 && roundsLeft > 0 && cpu.getCents() >= 2) {
                // Find leading human
                Player target = null;
                int maxNW = -1;
                for (Player p : room.getPlayers()) {
                    if (!p.isCpu() && !p.getId().equals(cpu.getId()) && p.getNetWorth() > maxNW && p.getBribeTaxPercent() < 40) {
                        maxNW = p.getNetWorth();
                        target = p;
                    }
                }
                if (target != null && random.nextInt(3) == 0) {
                    int maxBribe = Math.min(cpu.getCents(), Math.min(4, (40 - target.getBribeTaxPercent()) / 10));
                    if (maxBribe >= 1) {
                        int bribeAmt = Math.min(maxBribe, 1 + random.nextInt(Math.min(3, maxBribe)));
                        bribePlayer(room.getRoomCode(), cpu.getId(), target.getId(), bribeAmt);
                    }
                }
            }

            // === Loan Strategy: take loans when broke and vault has money ===
            if (cpu.getCents() < 2 && room.getKingsVault() >= 5 && cpu.getNetWorth() > 10) {
                takeLoan(room.getRoomCode(), cpu.getId());
            }
        }
    }

    private void startTurnTimer(GameRoom room) {
        cancelTimer(room);
        // Capture the current player's ID AND round number so stale timers from previous rounds are ignored
        final String timerPlayerId = room.getPlayers().get(room.getCurrentPlayerIndex()).getId();
        final int timerRound = room.getRoundNumber();
        final int timerIndex = room.getCurrentPlayerIndex();
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            synchronized (this) {
                if (room.isFinished()) return;
                // Verify round hasn't changed (prevents stale timer from previous round)
                if (room.getRoundNumber() != timerRound) return;
                // Verify the turn index and player haven't changed
                if (room.getCurrentPlayerIndex() != timerIndex) return;
                Player current = room.getPlayers().get(room.getCurrentPlayerIndex());
                if (!current.getId().equals(timerPlayerId)) return;
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
        state.put("currentGoldBarPurity", room.getCurrentGoldBarPurity());
        state.put("currentHighBid", room.getCurrentHighBid());
        state.put("currentHighBidderId", room.getCurrentHighBidderId());
        state.put("winnerId", room.getWinnerId());
        state.put("gameMode", room.getGameMode());
        state.put("roundNumber", room.getRoundNumber());

        // Rage mode fields
        if (room.isRageMode()) {
            state.put("kingsVault", room.getKingsVault());
            int taxRemainder = room.getRoundNumber() % 5;
            state.put("nextTaxRound", taxRemainder == 0 ? 0 : 5 - taxRemainder);
            state.put("waitingForTaxConfirmation", room.isWaitingForTaxConfirmation());
            state.put("taxConfirmedCount", room.getTaxConfirmedPlayerIds().size());
            state.put("taxTotalPlayers", room.getPlayers().size());
            // Jackpot round indicator
            boolean isJackpot = room.getRoundNumber() > 0 && room.getRoundNumber() % 11 == 0;
            state.put("isJackpotRound", isJackpot);
            int jackpotRemainder = room.getRoundNumber() % 11;
            state.put("nextJackpotRound", jackpotRemainder == 0 ? 0 : 11 - jackpotRemainder);
        }

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
            pm.put("connected", p.isConnected());
            // Rage mode: show bribe indicator
            if (room.isRageMode()) {
                pm.put("bribed", p.getBribeTaxPercent() > 0);
                pm.put("bribeTaxPercent", p.getBribeTaxPercent());
            }
            // Only show own cents
            if (p.getId().equals(playerId)) {
                pm.put("cents", p.getCents());
                pm.put("isYou", true);
            } else {
                pm.put("cents", "???");
                pm.put("isYou", false);
            }
            playerList.add(pm);
        }
        state.put("players", playerList);
        state.put("hostId", room.getHostId());
        state.put("winNetWorth", room.getWinNetWorth());
        state.put("startingCents", room.getStartingCents());
        state.put("isSpectator", room.getSpectatorIds().contains(playerId));
        return state;
    }

    private void broadcastState(GameRoom room) {
        // Broadcast to players
        for (Player p : room.getPlayers()) {
            if (!p.isCpu()) {
                messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/state/" + p.getId(),
                        (Object) getPublicState(room, p.getId()));
            }
        }
        // Broadcast to spectators
        for (String specId : room.getSpectatorIds()) {
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode() + "/state/" + specId,
                    (Object) getPublicState(room, specId));
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
