package at.aau.serg.monopoly.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.java.Log;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@EnableScheduling
@Log
public class LeaderboardService {

    private static final String USERS_COLLECTION = "users";
    private static final String GAME_HISTORY_COLLECTION = "gameHistory";
    private static final String LEADERBOARD_WINS = "leaderboard_wins";
    private static final String LEADERBOARD_LEVEL = "leaderboard_level";
    private static final String LEADERBOARD_MONEY = "leaderboard_averageMoney";
    private static final String LEADERBOARD_HIGH_MONEY = "leaderboard_highestMoney";
    private static final String LEADERBOARD_GAMES_PLAYED = "leaderboard_gamesPlayed";

    private static final int LEADERBOARD_SIZE = 50;


    Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    /**
     * Aktualisiert alle Leaderboards ... einmal am Tag - firebase-free sonst sofort die reqeusts verbraucht
     */
    @Scheduled(fixedRate = 86400000) // wegen begrenzter Requestanzahl in firebase-free
    public void updateAllLeaderboards() {
        log.info("Starte Leaderboard-Aktualisierung: " + new Date());
        try {
            Firestore firestore = getFirestore();
            if (firestore == null) {
                log.severe("Firestore ist nicht initialisiert");
                return;
            }

            updateAllUserStats();
            updateWinsLeaderboard();
            updateLevelLeaderboard();
            updateMoneyLeaderboard();
            updateHighMoneyLeaderboard();
            updateGamesPlayedLeaderboard();

            log.info("Leaderboard-Aktualisierung erfolgreich abgeschlossen: " + new Date());
        } catch (Exception e) {
            log.severe("Fehler bei Leaderboard-Aktualisierung: " + e.getMessage());
        }
    }
    /**
     * Aktualisiert  Statistiken für alle Benutzer basierend auf ihrer GameHistory
     */
    void updateAllUserStats() {
        try {
            Firestore firestore = getFirestore();
            CollectionReference usersCollection = firestore.collection(USERS_COLLECTION);
            ApiFuture<QuerySnapshot> querySnapshot = usersCollection.get();

            for (DocumentSnapshot userDoc : querySnapshot.get().getDocuments()) {
                String userId = userDoc.getId();
                updateUserStats(userId, firestore);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.severe("Fehler beim Aktualisieren der Benutzerstatistiken: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    // fixme split in smaller methods
    /**
     * Aktualisiert die Statistiken für einen einzelnen Benutzer
     */
    void updateUserStats(String userId, Firestore firestore) {
        try {
            // Spielhistorie des Benutzers abrufen
            CollectionReference historyRef = firestore.collection(USERS_COLLECTION)
                    .document(userId).collection(GAME_HISTORY_COLLECTION);
            ApiFuture<QuerySnapshot> historySnapshot = historyRef.get();
            List<QueryDocumentSnapshot> games = historySnapshot.get().getDocuments();

            if (games.isEmpty()) {
                return; // Keine Spielhistorie vorhanden
            }

            // Statistiken berechnen
            int wins = 0;
            int totalGames = games.size();
            int totalMoney = 0;
            int highestMoney = 0;

            for (DocumentSnapshot game : games) {
                Map<String, Object> gameData = game.getData();
                if (gameData == null) continue;

                // Gewonnene Spiele zählen
                if (Boolean.TRUE.equals(gameData.get("won"))) {
                    wins++;
                }

                // Geld tracken
                if (gameData.get("endMoney") != null) {
                    int endMoney = ((Number) gameData.get("endMoney")).intValue();
                    totalMoney += endMoney;
                    highestMoney = Math.max(highestMoney, endMoney);
                }

            }

            // Durchschnittliches Geld berechnen
            int averageMoney = totalGames > 0 ? totalMoney / totalGames : 0;

            int level = totalGames / 2;

            // Aktualisierte Statistiken in Firebase speichern
            DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
            Map<String, Object> updates = new HashMap<>();
            updates.put("gamesPlayed", totalGames);
            updates.put("wins", wins);
            updates.put("level", level);
            updates.put("averageMoney", averageMoney);
            updates.put("highestMoney", highestMoney);

            // Name aus vorhandenem Dokument beibehalten
            DocumentSnapshot userDoc = userRef.get().get();
            if (userDoc.exists() && userDoc.contains("name")) {
                updates.put("name", userDoc.getString("name"));
            }

            userRef.set(updates, SetOptions.merge());

        } catch (InterruptedException | ExecutionException e) {
            log.severe("Fehler beim Aktualisieren der Statistiken für Benutzer " + userId + ": " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * sort based on gewonnenen Spielen
     */
    void updateWinsLeaderboard() {
        updateLeaderboard("wins", LEADERBOARD_WINS);
    }

    /**
     * sort based on erreichten Levels
     */
    void updateLevelLeaderboard() {
        updateLeaderboard("level", LEADERBOARD_LEVEL);
    }

    /**
     * sort based on durchschnittlichem Geld
     */
    void updateMoneyLeaderboard() {
        updateLeaderboard("averageMoney", LEADERBOARD_MONEY);
    }

    /**
     * sort based on meistes Geld
     */
    void updateHighMoneyLeaderboard() {
        updateLeaderboard("highestMoney", LEADERBOARD_HIGH_MONEY);
    }

    /**
     * sort based on gespielten Spielen
     */
    void updateGamesPlayedLeaderboard() {
        updateLeaderboard("gamesPlayed", LEADERBOARD_GAMES_PLAYED);
    }

    /**
     * Generische Methode zum aktualisieren eines Leaderboards
     */
    void updateLeaderboard(String fieldName, String leaderboardCollection) {
        try {
            Firestore firestore = FirestoreClient.getFirestore();

            // Player nach dem angegebenen Feld absteigend sortieren
            ApiFuture<QuerySnapshot> query = firestore.collection(USERS_COLLECTION)
                    .orderBy(fieldName, Query.Direction.DESCENDING)
                    .limit(LEADERBOARD_SIZE)
                    .get();

            List<QueryDocumentSnapshot> users = query.get().getDocuments();

            // Leaderboard-Sammlung leeren
            deleteCollection(firestore, leaderboardCollection);

            // Neue Leaderboard-Einträge erstellen
            int rank = 1;
            for (DocumentSnapshot user : users) {
                Map<String, Object> userData = user.getData();
                if (userData == null) continue;

                Map<String, Object> leaderboardEntry = new HashMap<>();
                leaderboardEntry.put("userId", user.getId());
                leaderboardEntry.put("name", userData.getOrDefault("name", "Unbekannt"));
                leaderboardEntry.put("rank", rank);
                leaderboardEntry.put(fieldName, userData.getOrDefault(fieldName, 0));


                // In Leaderboard-Sammlung speichern
                firestore.collection(leaderboardCollection)
                        .document(String.valueOf(rank))
                        .set(leaderboardEntry);

                rank++;
            }

            log.info(leaderboardCollection + " wurde aktualisiert mit " + (rank-1) + " Einträgen");

        } catch (InterruptedException | ExecutionException e) {
            log.severe("Fehler beim Aktualisieren des " + leaderboardCollection + ": " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Löscht alle Dokumente in einer Sammlung
     */
    void deleteCollection(Firestore firestore, String collectionPath) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(collectionPath);
        ApiFuture<QuerySnapshot> future = collection.limit(100).get();
        int deleted = 0;

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        for (DocumentSnapshot document : documents) {
            document.getReference().delete();
            deleted++;
        }

        if (deleted >= 100) {
            deleteCollection(firestore, collectionPath);
        }
    }
}