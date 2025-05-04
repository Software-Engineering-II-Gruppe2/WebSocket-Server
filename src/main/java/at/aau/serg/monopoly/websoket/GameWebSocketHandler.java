package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.DiceRollMessage;
import data.TaxPaymentMessage;
import lombok.NonNull;
import model.DiceManager;
import model.DiceManagerInterface;
import model.Game;
import model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;


@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final String PLAYER_PREFIX = "Player ";
    private final Logger logger = Logger.getLogger(GameWebSocketHandler.class.getName());
    protected final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    public final Map<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final Game game = new Game();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DiceManagerInterface diceManager;
    @Autowired
    private GameHistoryService gameHistoryService;

    @Autowired
    private PropertyTransactionService propertyTransactionService;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);

        diceManager = new DiceManager();
        diceManager.initializeStandardDices();
    }

    protected void handleInitMessage(WebSocketSession session, JsonNode jsonNode) {
        try {
            String userId = jsonNode.get("userId").asText();
            String name = jsonNode.get("name").asText();

            if (userId == null || sessionToUserId.containsValue(userId)) {
                sendMessageToSession(session, createJsonError("Invalid user"));
                return;
            }

            // Spieler mit Firebase-ID hinzufügen
            game.addPlayer(userId, name);
            sessionToUserId.put(session.getId(), userId);

            System.out.println("Player connected: " + userId + " | Name: " + name);
            broadcastMessage("SYSTEM: " + name + " (" + userId + ") joined the game");

            // Spielstart-Logik anpassen
            if (sessionToUserId.size() >= 2 && sessionToUserId.size() <= 4) {
                startGame();
            }

            broadcastGameState();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing INIT: {0}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String sessionId = session.getId();

        try {
            // Zuerst INIT-Check
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode.has("type")) {
                String type = jsonNode.get("type").asText();
                if ("INIT".equals(type)) {
                    handleInitMessage(session, jsonNode);
                    return;
                } else if ("END_GAME".equals(type)) {
                    handleEndGame(jsonNode);
                    return;
                } else if ("GIVE_UP".equals(type)) {
                    handleGiveUp(session, jsonNode);
                    return;
                }
            }
        } catch (IOException e) {
            // Kein JSON, normal weiter
        }

        String userId = sessionToUserId.get(sessionId);
        if (userId == null) {
            sendMessageToSession(session, createJsonError("Send INIT message first"));
            return;
        }


        try {

            if (payload.contains("\"type\":\"CHAT_MESSAGE\"")) {
                logger.log(Level.INFO, "Received chat message from player {0}", userId);
                broadcastMessage(payload);
                return;
            }
            if (payload.contains("\"type\":\"TAX_PAYMENT\"")) {
                try {
                    TaxPaymentMessage taxMsg = objectMapper.readValue(payload, TaxPaymentMessage.class);
                    if (taxMsg.getPlayerId().equals(userId)) {
                        game.updatePlayerMoney(userId, -taxMsg.getAmount());
                        broadcastMessage(payload);
                        broadcastGameState();
                    }
                    return;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error processing tax payment message: {0}", e.getMessage());
                }
            }
            if (payload.trim().equalsIgnoreCase("Roll")) {
                int roll = diceManager.rollDices();
                logger.log(Level.INFO, "Player {0} rolled {1}", new Object[]{userId, roll});

                DiceRollMessage drm = new DiceRollMessage(userId, roll);
                String json = objectMapper.writeValueAsString(drm);
                broadcastMessage(json);

                if (roll != 12) {
                    game.nextPlayer();
                }

                // Update Position and broadcast Game-State:
                if (game.updatePlayerPosition(roll, userId)) {
                    broadcastMessage(PLAYER_PREFIX + userId + " passed GO and collected €200");
                }

                // Check for tax fields and send appropriate messages
                Player player = game.getPlayerById(userId).orElse(null);
                if (player != null) {
                    int position = player.getPosition();
                    if (position == 4) {  // Einkommensteuer
                        game.updatePlayerMoney(userId, -200);  // Deduct money first
                        TaxPaymentMessage taxMsg = new TaxPaymentMessage(userId, 200, "EINKOMMENSTEUER");
                        String jsonTax = objectMapper.writeValueAsString(taxMsg);
                        broadcastMessage(jsonTax);
                    } else if (position == 38) {  // Zusatzsteuer
                        game.updatePlayerMoney(userId, -100);  // Deduct money first
                        TaxPaymentMessage taxMsg = new TaxPaymentMessage(userId, 100, "ZUSATZSTEUER");
                        String jsonTax = objectMapper.writeValueAsString(taxMsg);
                        broadcastMessage(jsonTax);
                    }
                }

                broadcastGameState();
            } else if (payload.startsWith("MANUAL_ROLL:")) {
                try {
                    int manualRoll = Integer.parseInt(payload.substring("MANUAL_ROLL:".length()));
                    if (manualRoll < 1 || manualRoll > 39) {
                        sendMessageToSession(session, createJsonError("Invalid roll value. Must be between 1 and 39."));
                        return;
                    }

                    logger.log(Level.INFO, "Player {0} manually rolled {1}", new Object[]{userId, manualRoll});

                    DiceRollMessage drm = new DiceRollMessage(userId, manualRoll, true);
                    String json = objectMapper.writeValueAsString(drm);
                    broadcastMessage(json);

                    if (manualRoll != 12) {
                        game.nextPlayer();
                    }

                    if (game.updatePlayerPosition(manualRoll, userId)) {
                        broadcastMessage(PLAYER_PREFIX + userId + " passed GO and collected €200");
                    }
                    broadcastGameState();
                } catch (NumberFormatException e) {
                    sendMessageToSession(session, createJsonError("Invalid manual roll format. Please provide a number between 1 and 39."));
                }
            } else if (payload.startsWith("UPDATE_MONEY:")) {
                try {
                    int amount = Integer.parseInt(payload.substring("UPDATE_MONEY:".length()));
                    game.updatePlayerMoney(userId, amount);
                    broadcastGameState();
                } catch (NumberFormatException e) {
                    System.err.println("Invalid money update format: " + sanitizeForLog(payload));
                }
            } else if (payload.startsWith("BUY_PROPERTY:")) {
                handleBuyProperty(session, userId, payload);
            } else {
                String safePayload = sanitizeForLog(payload);
                logger.log(Level.INFO, "Received unknown message format: {0} from player {1}", new Object[]{safePayload, userId});
                broadcastMessage(PLAYER_PREFIX + userId + ": " + safePayload);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling message from player {0}: {1}", new Object[]{userId, e.getMessage()});
            sendMessageToSession(session, createJsonError("Server error processing your request."));
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String userId = sessionToUserId.get(session.getId());
        if (userId != null) {
            game.removePlayer(userId);
            sessionToUserId.remove(session.getId());
            broadcastMessage("Player left: " + userId + " (Total: " + sessions.size() + ")");
            System.out.println("Player disconnected: " + userId);
        }
        sessions.remove(session);
    }

    private void broadcastMessage(String message) {
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                } else {
                    sessions.remove(session);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error sending message: {0}", e.getMessage());
            }
        }
    }

    private void broadcastGameState() {
        try {
            String gameState = objectMapper.writeValueAsString(game.getPlayerInfo());
            broadcastMessage("GAME_STATE:" + gameState);
            broadcastMessage("PLAYER_TURN:" + game.getCurrentPlayer().getId());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error broadcasting game state: {0}", e.getMessage());
        }
    }

    private void startGame() {
        try {
            String gameState = objectMapper.writeValueAsString(game.getPlayerInfo());
            broadcastMessage("GAME_STATE:" + gameState);
            broadcastMessage("Game started! " + sessions.size() + " players are connected.");
            System.out.println("Game started with " + sessions.size() + " players!");
            game.start();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending game state: {0}", e.getMessage());
        }
    }

    private void handleBuyProperty(WebSocketSession session, String userId, String payload) {
        try {
            int propertyId = Integer.parseInt(payload.substring("BUY_PROPERTY:".length()));
            logger.log(Level.INFO, "Player {0} attempts to buy property {1}", new Object[]{userId, propertyId});

            Optional<Player> playerOpt = game.getPlayerById(userId);
            if (playerOpt.isEmpty()) {
                logger.log(Level.WARNING, "Player {0} not found in game state during buy attempt.", userId);
                sendMessageToSession(session, createJsonError("Player not found."));
                return;
            }
            Player player = playerOpt.get();

            if (propertyTransactionService.canBuyProperty(player, propertyId)) {
                boolean success = propertyTransactionService.buyProperty(player, propertyId);
                if (success) {
                    logger.log(Level.INFO, "Property {0} bought successfully by player {1}", new Object[]{propertyId, userId});
                    broadcastMessage(createJsonMessage(PLAYER_PREFIX + userId + " bought property " + propertyId));
                    broadcastGameState();
                } else {
                    logger.log(Level.WARNING, "Property purchase failed for player {0}, property {1} after canBuy check.", new Object[]{userId, propertyId});
                    sendMessageToSession(session, createJsonError("Failed to buy property due to server error."));
                }
            } else {
                if (!game.isPlayerTurn(userId)) {
                    logger.log(Level.WARNING, "Player {0} attempted to buy property {1} when it's not their turn", new Object[]{userId, propertyId});
                    sendMessageToSession(session, createJsonError("Cannot buy property - it's not your turn."));
                } else {
                    logger.log(Level.WARNING, "Property purchase failed for player {0}, property {1} after canBuy check.", new Object[]{userId, propertyId});
                    sendMessageToSession(session, createJsonError("Cannot buy property (insufficient funds or already owned)."));
                }
            }

        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid property ID in payload from player {0}: {1}", new Object[]{userId, sanitizeForLog(payload)});
            sendMessageToSession(session, createJsonError("Invalid property ID format."));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling BUY_PROPERTY for player {0}: {1}", new Object[]{userId, e.getMessage()});
            sendMessageToSession(session, createJsonError("Server error handling buy property request."));
        }
    }

    /**
     * Behandelt die Beendigung eines Spiels und speichert die Spielhistorie
     */
    private void handleEndGame(JsonNode jsonNode) {
        try {
            String winnerId = game.determineWinner();

            // Beende das Spiel und erhalte die Spieldauer
            int durationMinutes = game.endGame(winnerId);

            // Standard Level-Gewinn für alle Spieler
            int levelGained = 1;

            // Speichere die Spielhistorie für alle Spieler
            gameHistoryService.saveGameHistoryForAllPlayers(
                    game.getPlayers(),
                    durationMinutes,
                    winnerId,
                    levelGained
            );

            // Informiere alle Spieler über das Spielende
            broadcastMessage(createJsonMessage("Das Spiel wurde beendet. Der Gewinner ist " +
                    game.getPlayerById(winnerId).map(Player::getName).orElse("unbekannt")));

            logger.info("Spiel beendet und Spielhistorie gespeichert");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Beenden des Spiels", e);
        }
    }


    private void sendMessageToSession(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending message to session {0}: {1}", new Object[]{session.getId(), e.getMessage()});
        }
    }

    private String createJsonError(String errorMessage) {
        return "{\"type\":\"ERROR\", \"message\":\"" + escapeJson(errorMessage) + "\"}";
    }

    private String createJsonMessage(String message) {
        return "{\"type\":\"PROPERTY_BOUGHT\", \"message\":\"" + escapeJson(message) + "\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sanitizeForLog(String input) {
        String sanitized = input.replaceAll("[\\r\\n]", "_");
        return sanitized.length() > 100 ? sanitized.substring(0, 100) + "..." : sanitized;
    }

    private void handleGiveUp(WebSocketSession session, JsonNode jsonNode) {
        try {
            String userId = jsonNode.get("userId").asText();
            if (userId == null || !sessionToUserId.containsValue(userId)) {
                sendMessageToSession(session, createJsonError("Invalid user"));
                return;
            }

            game.removePlayer(userId);
            sessionToUserId.remove(session.getId());
            broadcastMessage("Player gave up: " + userId);
            System.out.println("Player gave up: " + userId);

            // Optional: Trage in Datenbank als "verloren" ein
            gameHistoryService.markPlayerAsLoser(userId); // ← wenn du das hast

            session.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing GIVE_UP message", e);
            sendMessageToSession(session, createJsonError("Error processing give up."));
        }
    }
}