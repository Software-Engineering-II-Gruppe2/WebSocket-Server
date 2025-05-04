package at.aau.serg.monopoly.websoket;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import model.GameHistory;
import model.Player;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class GameHistoryService {

    private static final Logger logger = Logger.getLogger(GameHistoryService.class.getName());
    private static final String COLLECTION_NAME = "users";
    private static final String SUBCOLLECTION_NAME = "gameHistory";

    /**
     * Speichert die Spielhistorie für einen bestimmten Spieler
     *
     * @param userId          Die ID des Spielers
     * @param durationMinutes Die Dauer des Spiels in Minuten
     * @param endMoney        Das Geld des Spielers am Ende
     * @param levelGained     Die gewonnenen Level
     * @param playersCount    Die Anzahl der Spieler im Spiel
     * @param won             Ob der Spieler gewonnen hat
     * @return true, wenn das Speichern erfolgreich war, sonst false
     */
    public boolean saveGameHistory(String userId, int durationMinutes, int endMoney,
                                   int levelGained, int playersCount, boolean won) {
        try {
            Firestore firestore = FirestoreClient.getFirestore();

            GameHistory gameHistory = new GameHistory();
            gameHistory.setId(UUID.randomUUID().toString());
            gameHistory.setUserId(userId);
            gameHistory.setDurationMinutes(durationMinutes);
            gameHistory.setEndMoney(endMoney);
            gameHistory.setLevelGained(levelGained);
            gameHistory.setPlayersCount(playersCount);
            gameHistory.setTimestamp(new Date());
            gameHistory.setWon(won);

            // Pfad: users/UID/gameHistory/ID
            ApiFuture<WriteResult> result = firestore.collection(COLLECTION_NAME)
                    .document(userId)
                    .collection(SUBCOLLECTION_NAME)
                    .document(gameHistory.getId())
                    .set(gameHistory);

            result.get(); // Warten auf das Ergebnis
            logger.info("Spielhistorie für Benutzer " + userId + " erfolgreich gespeichert");
            return true;
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Fehler beim Speichern der Spielhistorie für Benutzer " + userId, e);
            Thread.currentThread().interrupt(); // Guter Umgang mit InterruptedException
            return false;
        }
    }

    /**
     * Speichert die Spielhistorie für alle Spieler am Ende eines Spiels
     *
     * @param players         Die Liste der Spieler
     * @param durationMinutes Die Dauer des Spiels in Minuten
     * @param winnerId        Die ID des Gewinners (oder null, wenn kein Gewinner)
     * @param levelGained     Die gewonnenen Level
     */
    public void saveGameHistoryForAllPlayers(java.util.List<Player> players, int durationMinutes,
                                             String winnerId, int levelGained) {
        if (players == null || players.isEmpty()) {
            logger.warning("Keine Spieler zum Speichern der Spielhistorie vorhanden");
            return;
        }

        int playersCount = players.size();

        for (Player player : players) {
            boolean won = player.getId().equals(winnerId);
            saveGameHistory(
                    player.getId(),
                    durationMinutes,
                    player.getMoney(),
                    levelGained,
                    playersCount,
                    won
            );
        }

        logger.info("Spielhistorie für alle Spieler gespeichert");
    }

    /**
     * Speichert einen Spielabbruch (Give Up) als verlorenes Spiel für einen Spieler
     */
    public void markPlayerAsLoser(String userId) {
        try {
            Firestore firestore = FirestoreClient.getFirestore();

            GameHistory gameHistory = new GameHistory();
            gameHistory.setId(UUID.randomUUID().toString());
            gameHistory.setUserId(userId);
            gameHistory.setDurationMinutes(0); // Spiel wurde aufgegeben, Dauer optional
            gameHistory.setEndMoney(0); // evtl. Geldstand, hier 0 als Platzhalter
            gameHistory.setLevelGained(0); // kein Levelgewinn
            gameHistory.setPlayersCount(0); // nicht relevant
            gameHistory.setTimestamp(new Date());
            gameHistory.setWon(false); // Spieler hat verloren

            ApiFuture<WriteResult> result = firestore.collection(COLLECTION_NAME)
                    .document(userId)
                    .collection(SUBCOLLECTION_NAME)
                    .document(gameHistory.getId())
                    .set(gameHistory);

            result.get(); // warten auf Abschluss
            logger.info("Spielabbruch als Niederlage für " + userId + " gespeichert.");
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Fehler beim Speichern des Spielabbruchs für " + userId, e);
            Thread.currentThread().interrupt();
        }
    }
}