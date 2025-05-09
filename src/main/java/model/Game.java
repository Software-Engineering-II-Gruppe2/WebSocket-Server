package model;

import java.util.Date;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class Game {
    private List<Player> players;
    private boolean isStarted;
    private int currentPlayerIndex;
    private Date startTime;
    private String winnerId;


    public Game() {
        this.players = new CopyOnWriteArrayList<>();
        this.isStarted = false;
        this.currentPlayerIndex = 0;
    }

    public void start() {
        this.isStarted = true;
        this.startTime = new Date();
        if (!players.isEmpty()) {
            currentPlayerIndex = 0;
        } else {
            currentPlayerIndex = -1; // kein Spieler verfügbar
        }
    }

    public void addPlayer(String id, String name) {
        if (players.stream().noneMatch(p -> p.getId().equals(id))) {
            players.add(new Player(id, name));
        }
    }

    public void removePlayer(String id) {
        players.removeIf(player -> player.getId().equals(id));
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
        if (players.isEmpty() || currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        players.get(currentPlayerIndex).setHasRolledThisTurn(false);
    }

    public List<PlayerInfo> getPlayerInfo() {
        List<PlayerInfo> info = new ArrayList<>();
        for (Player player : players) {
            info.add(new PlayerInfo(player.getId(), player.getName(), player.getMoney(), player.getPosition()));
        }
        return info;
    }

    /**
     * Finds a player by their unique ID.
     *
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
     *
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
     * @param roll The result of roll dice.
     * @param id   The ID of the player to find.
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

    /**
     * Beendet das Spiel und setzt den Gewinner
     *
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
}