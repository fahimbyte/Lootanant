package imperfect.lootanant.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

public class GameRoom {
    private String roomCode;
    private String hostId;
    private List<Player> players = new ArrayList<>();
    private boolean started = false;
    private boolean finished = false;
    private int currentDeedValue = 0;
    private int currentHighBid = 0;
    private String currentHighBidderId = null;
    private int currentPlayerIndex = 0;
    private int startingPlayerIndex = 0;
    private transient ScheduledFuture<?> turnTimer;
    private String winnerId = null;
    private int winNetWorth = 50;
    private int startingAntCents = 12;

    public GameRoom(String roomCode, String hostId) {
        this.roomCode = roomCode;
        this.hostId = hostId;
    }

    public Player getPlayerById(String id) {
        return players.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    public int activeBiddersCount() {
        return (int) players.stream().filter(p -> !p.isPassedThisRound()).count();
    }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }
    public boolean isStarted() { return started; }
    public void setStarted(boolean started) { this.started = started; }
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public int getCurrentDeedValue() { return currentDeedValue; }
    public void setCurrentDeedValue(int currentDeedValue) { this.currentDeedValue = currentDeedValue; }
    public int getCurrentHighBid() { return currentHighBid; }
    public void setCurrentHighBid(int currentHighBid) { this.currentHighBid = currentHighBid; }
    public String getCurrentHighBidderId() { return currentHighBidderId; }
    public void setCurrentHighBidderId(String currentHighBidderId) { this.currentHighBidderId = currentHighBidderId; }
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public void setCurrentPlayerIndex(int currentPlayerIndex) { this.currentPlayerIndex = currentPlayerIndex; }
    public int getStartingPlayerIndex() { return startingPlayerIndex; }
    public void setStartingPlayerIndex(int startingPlayerIndex) { this.startingPlayerIndex = startingPlayerIndex; }
    public ScheduledFuture<?> getTurnTimer() { return turnTimer; }
    public void setTurnTimer(ScheduledFuture<?> turnTimer) { this.turnTimer = turnTimer; }
    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
    public int getWinNetWorth() { return winNetWorth; }
    public void setWinNetWorth(int winNetWorth) { this.winNetWorth = winNetWorth; }
    public int getStartingAntCents() { return startingAntCents; }
    public void setStartingAntCents(int startingAntCents) { this.startingAntCents = startingAntCents; }
}
