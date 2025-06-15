package model;
import java.util.Date;

import at.aau.serg.monopoly.websoket.PropertyService;
import at.aau.serg.monopoly.websoket.PropertyTransactionService;
import data.PlayerInfo;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class Game {
    private List<Player> players;
    private boolean isStarted;
    private int currentPlayerIndex;
    private Date startTime;
    private final ReentrantLock turnLock = new ReentrantLock();
    private String winnerId;
    @Getter
    private final DiceManagerInterface diceManager = new DiceManager();
    @Getter
    private PropertyService propertyService;
    @Getter
    private PropertyTransactionService propertyTransactionService;


    public Game() {
        this.players = new CopyOnWriteArrayList<>();
        this.isStarted = false;
        this.currentPlayerIndex = 0;
    }

    public void start() {
        this.isStarted = true;
        this.startTime = new Date();
    }

    public void addPlayer(String id, String name) {
        if (players.stream().noneMatch(p -> p.getId().equals(id))) {
            players.add(new Player(id, name));
        }
    }

    public void removePlayer(String id) {
        // Very similar implementation to giveUp(...) - see that for more details
        int idx = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(id)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;

        players.remove(idx);

        if (idx < currentPlayerIndex) {
            currentPlayerIndex--;
        } else if (idx == currentPlayerIndex) {

            if (currentPlayerIndex >= players.size()) {
                currentPlayerIndex = 0;
            }
        }

        if (!players.isEmpty()) {
            players.get(currentPlayerIndex).setHasRolledThisTurn(false);
        }
    }

    public void updatePlayerMoney(String playerId, int amount) {
        for (Player player : players) {
            if (player.getId().equals(playerId)) {
                if (amount > 0) {
                    player.addMoney(amount);
                } else {
                    player.subtractMoney(Math.abs(amount));
                }
                break;
            }
        }
    }

    public Player getCurrentPlayer() {
        if (players.isEmpty()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        if (players.isEmpty()) return;

        int originalIndex = currentPlayerIndex;
        for (int i = 1; i <= players.size(); i++) {
            int nextIndex = (currentPlayerIndex + i) % players.size();
            Player next = players.get(nextIndex);

            if (next.isConnected() || next.isBot()) {
                currentPlayerIndex = nextIndex;
                next.setHasRolledThisTurn(false);
                return;
            }
        }

        // Falls kein gültiger Spieler gefunden wurde → Spiel beenden
        System.out.println("No connected players or bots found – ending game.");
    }


    public List<PlayerInfo> getPlayerInfo() {
        List<PlayerInfo> info = new ArrayList<>();
        for (Player player : players) {
            info.add(new PlayerInfo(player.getId(), player.getName(), player.getMoney(), player.getPosition(), player.isInJail(), player.getJailTurns(), player.isBot()));
        }
        return info;
    }

    /**
     * Finds a player by their unique ID.
     * @param id The ID of the player to find.
     * @return An Optional containing the Player if found, otherwise empty.
     */
    public Optional<Player> getPlayerById(String id) {
        return players.stream()
                .filter(player -> player.getId().equals(id))
                .findFirst();
    }

    /**
     * Checks if it's the specified player's turn
     * @param playerId The ID of the player to check
     * @return true if it's the player's turn, false otherwise
     */
    public boolean isPlayerTurn(String playerId) {
        if (players.isEmpty() || currentPlayerIndex >= players.size()) {
            return false;
        }
        return players.get(currentPlayerIndex).getId().equals(playerId);
    }

    /**
     *
     * @param roll The result of roll dice.
     * @param id The ID of the player to find.
     * @return If the player passes the Start field the method returns true, otherwise false.
     */
    public boolean updatePlayerPosition(int roll, String id) {
        for (Player player : players) {
            if (player.getId().equals(id)) {
                int oldPos = player.getPosition();
                int newPos = (oldPos + roll) % 40;   // ensures 0–39
                player.setPosition(newPos);

                if (oldPos + roll >= 40) {
                    player.addMoney(200);  // Add $200 for passing GO
                    return true;
                }
            }
        }
        return false;
    }

    public void giveUp(String playerId) {
        // Find the index of the player who sent GIVE_UP
        int idx = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(playerId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;

        players.remove(idx);

        // If we remove the current player than the next turn is still the same index
        if (idx == currentPlayerIndex) {

            // If we were at the end -> back to 0
            if (currentPlayerIndex >= players.size()) {
                currentPlayerIndex = 0;
            }
        } else if (idx < currentPlayerIndex) {
            currentPlayerIndex--;
        }

        // Reset hasRolled
        if (!players.isEmpty()) {
            players.get(currentPlayerIndex).setHasRolledThisTurn(false);
        }
    }

    /**
     * Beendet das Spiel und setzt den Gewinner
     * @param winnerId Die ID des Gewinners
     * @return Die Dauer des Spiels in Minuten
     */
    public int endGame(String winnerId) {
        this.isStarted = false;
        this.winnerId = winnerId;

        if (startTime == null) {
            return 0;
        }

        // Berechnung der Spielzeit in Minuten
        long durationMs = new Date().getTime() - startTime.getTime();
        return (int) (durationMs / (60 * 1000));
    }

    public int getDurationPlayed(){
        long durationMs = new Date().getTime() - startTime.getTime();
        return (int) (durationMs / (60 * 1000));
    }


    public String determineWinner() {
        if (players.isEmpty()) {
            return null;
        }

        Player winner = players.get(0);
        for (Player player : players) {
            if (player.getMoney() > winner.getMoney()) {
                winner = player;
            }
        }

        return winner.getId();
    }

    public void sendToJail(String playerId) {
        getPlayerById(playerId).ifPresent(player -> {
            player.setInJail(true);
            player.setJailTurns(2);
            player.setPosition(10);
        });
    }

    public void markPlayerDisconnected(String userId) {
        getPlayerById(userId).ifPresent(player -> player.setConnected(false));
    }

    public void markPlayerConnected(String userId) {
        getPlayerById(userId).ifPresent(player -> player.setConnected(true));
    }


    public void replaceDisconnectedWithBot(String userId) {
        getPlayerById(userId).ifPresent(player -> {
            if (!player.isConnected() && !player.isBot()) {
                player.setBot(true);
                player.setConnected(true);
                System.out.println("Replaced disconnected player with bot: " + player.getId());
            }
        });
    }


    public void setPropertyService(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    public void setPropertyTransactionService(PropertyTransactionService propertyTransactionService) {
        this.propertyTransactionService = propertyTransactionService;
    }

    public int handleDiceRoll(String playerId) {
        Optional<Player> opt = getPlayerById(playerId);
        if (opt.isEmpty()) return -1;

        Player p = opt.get();
        int roll = diceManager.rollDices();
        p.setHasRolledThisTurn(true);   //  ←  wichtig
        updatePlayerPosition(roll, playerId);

        return roll;
    }
    public void resetForNewMatch() {
        players.forEach(p -> {
            p.setPosition(0);
            p.setMoney(1500);
            p.setInJail(false);
            p.setHasRolledThisTurn(false);
            // … weitere Flags
        });
        currentPlayerIndex = 0;
        isStarted = true;
    }

    public void reset() {
        currentPlayerIndex = 0;
        winnerId = null;
        isStarted = false;
        players.forEach(p -> {
            p.setPosition(0);
            p.setMoney(1500);
            p.setInJail(false);
            p.setJailTurns(0);
            p.setConnected(true);   //jeder aktive Socket zählt wieder
            p.setBot(false);
            p.setHasRolledThisTurn(false);
        });

        // Alle Grundstücke freimachen


        // Würfel zurücksetzen, Karten mischen, …
        diceManager.initializeStandardDices();
    }

    public Player getNextPlayer() {
        if (players.isEmpty()) {
            throw new IllegalStateException("Keine Spieler im Spiel");
        }
        int size = players.size();
        for (int i = 1; i <= size; i++) {
            int nextIndex = (currentPlayerIndex + i) % size;
            Player candidate = players.get(nextIndex);
            if (candidate.isConnected() || candidate.isBot()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Kein gültiger nächster Spieler gefunden");
    }
}





