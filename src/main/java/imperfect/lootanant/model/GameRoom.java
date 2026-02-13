package imperfect.lootanant.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

public class GameRoom {
    private String roomCode;
    private String hostId;
    private List<Player> players = new ArrayList<>();
    private boolean started = false;
    private boolean finished = false;
    private int currentGoldBarPurity = 0;
    private int currentHighBid = 0;
    private String currentHighBidderId = null;
    private int currentPlayerIndex = 0;
    private int startingPlayerIndex = 0;
    private transient ScheduledFuture<?> turnTimer;
    private String winnerId = null;
    private int winNetWorth = 50;
    private int startingCents = 12;
    private List<String> spectatorIds = new ArrayList<>();

    // Rage mode fields
    private String gameMode = "classic"; // "classic" or "rage"
    private int roundNumber = 0;
    private int kingsVault = 0;
    private boolean taxationPhaseActive = false;
    private boolean waitingForTaxConfirmation = false;
    private Set<String> taxConfirmedPlayerIds = new HashSet<>();

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

    public boolean isRageMode() {
        return "rage".equalsIgnoreCase(gameMode);
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
    public int getCurrentGoldBarPurity() { return currentGoldBarPurity; }
    public void setCurrentGoldBarPurity(int currentGoldBarPurity) { this.currentGoldBarPurity = currentGoldBarPurity; }
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
    public int getStartingCents() { return startingCents; }
    public void setStartingCents(int startingCents) { this.startingCents = startingCents; }
    public List<String> getSpectatorIds() { return spectatorIds; }
    public void setSpectatorIds(List<String> spectatorIds) { this.spectatorIds = spectatorIds; }
    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
    public int getKingsVault() { return kingsVault; }
    public void setKingsVault(int kingsVault) { this.kingsVault = kingsVault; }
    public boolean isTaxationPhaseActive() { return taxationPhaseActive; }
    public void setTaxationPhaseActive(boolean taxationPhaseActive) { this.taxationPhaseActive = taxationPhaseActive; }
    public boolean isWaitingForTaxConfirmation() { return waitingForTaxConfirmation; }
    public void setWaitingForTaxConfirmation(boolean waitingForTaxConfirmation) { this.waitingForTaxConfirmation = waitingForTaxConfirmation; }
    public Set<String> getTaxConfirmedPlayerIds() { return taxConfirmedPlayerIds; }
    public void setTaxConfirmedPlayerIds(Set<String> taxConfirmedPlayerIds) { this.taxConfirmedPlayerIds = taxConfirmedPlayerIds; }
}
