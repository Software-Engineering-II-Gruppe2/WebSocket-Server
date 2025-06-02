package model;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class Player {
    private String id;
    private String name;
    private int money;
    private static final int STARTING_MONEY = 1500; // Standard Monopoly starting money
    private int position = 0; // Starting position
    @Getter
    private boolean connected= true;
    @Setter
    private boolean hasRolledThisTurn = false;
    private boolean inJail = false;
    private int jailTurns = 2;
    private boolean isBot= false;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.money = STARTING_MONEY;
    }

    public void addMoney(int amount) {
        this.money += amount;
    }

    public void subtractMoney(int amount) {
        this.money -= amount;
    }

    public boolean hasRolledThisTurn() {
        return hasRolledThisTurn;
    }

    public void sendToJail() {
        this.inJail = true;
        this.jailTurns = 2;
    }

    public void reduceJailTurns() {
        if (inJail && jailTurns > 0) {
            jailTurns--;
            if (jailTurns == 0) {
                inJail = false;
            }
        }
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    public boolean isBot() {
        return isBot;
    }

    public void setBot(boolean bot) {
        this.isBot = bot;
    }
}