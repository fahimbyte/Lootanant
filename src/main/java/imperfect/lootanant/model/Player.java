package imperfect.lootanant.model;

public class Player {
    private String id;
    private String displayName;
    private int cents = 12;
    private int netWorth = 0;
    private boolean cpu = false;
    private boolean passedThisRound = false;
    private boolean connected = true;

    // Rage mode fields
    private int bribeTaxPercent = 0; // extra tax % from bribes (capped at 40)
    private boolean hasActiveLoan = false;

    public Player(String id, String displayName, boolean cpu) {
        this.id = id;
        this.displayName = displayName;
        this.cpu = cpu;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getCents() { return cents; }
    public void setCents(int cents) { this.cents = cents; }
    public int getNetWorth() { return netWorth; }
    public void setNetWorth(int netWorth) { this.netWorth = netWorth; }
    public boolean isCpu() { return cpu; }
    public void setCpu(boolean cpu) { this.cpu = cpu; }
    public boolean isPassedThisRound() { return passedThisRound; }
    public void setPassedThisRound(boolean passedThisRound) { this.passedThisRound = passedThisRound; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public int getBribeTaxPercent() { return bribeTaxPercent; }
    public void setBribeTaxPercent(int bribeTaxPercent) { this.bribeTaxPercent = Math.min(bribeTaxPercent, 40); }
    public boolean isHasActiveLoan() { return hasActiveLoan; }
    public void setHasActiveLoan(boolean hasActiveLoan) { this.hasActiveLoan = hasActiveLoan; }
}
