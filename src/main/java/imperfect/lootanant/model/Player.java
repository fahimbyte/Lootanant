package imperfect.lootanant.model;

public class Player {
    private String id;
    private String displayName;
    private int antCents = 12;
    private int netWorth = 0;
    private boolean cpu = false;
    private boolean passedThisRound = false;
    private boolean connected = true;

    public Player(String id, String displayName, boolean cpu) {
        this.id = id;
        this.displayName = displayName;
        this.cpu = cpu;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getAntCents() { return antCents; }
    public void setAntCents(int antCents) { this.antCents = antCents; }
    public int getNetWorth() { return netWorth; }
    public void setNetWorth(int netWorth) { this.netWorth = netWorth; }
    public boolean isCpu() { return cpu; }
    public void setCpu(boolean cpu) { this.cpu = cpu; }
    public boolean isPassedThisRound() { return passedThisRound; }
    public void setPassedThisRound(boolean passedThisRound) { this.passedThisRound = passedThisRound; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
}
