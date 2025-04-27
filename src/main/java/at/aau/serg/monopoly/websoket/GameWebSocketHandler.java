package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.ObjectMapper;
import data.DiceRollMessage;
import lombok.NonNull;
import model.DiceManager;
import model.DiceManagerInterface;
import model.Game;
import model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final Logger logger = Logger.getLogger(GameWebSocketHandler.class.getName());
    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Game game = new Game();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DiceManagerInterface diceManager;

    @Autowired
    private PropertyTransactionService propertyTransactionService;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
        game.addPlayer(session.getId(), "Player " + sessions.size());
        broadcastMessage("Player joined: " + session.getId() + " (Total: " + sessions.size() + ")");

        diceManager = new DiceManager();
        diceManager.initializeStandardDices();

        if (sessions.size() >= 2 && sessions.size() <= 4) {
            startGame();
        }

        System.out.println("Player connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String playerId = session.getId();

        try {
            if (payload.contains("\"type\":\"CHAT_MESSAGE\"")) {
                logger.log(Level.INFO, "Received chat message from player {0}", playerId);
                broadcastMessage(payload);
                return;
            }

            if (payload.trim().equalsIgnoreCase("Roll")) {
                int roll = diceManager.rollDices();
                logger.log(Level.INFO, "Player {0} rolled {1}", new Object[]{playerId, roll});

                DiceRollMessage drm = new DiceRollMessage(playerId, roll);
                String json = objectMapper.writeValueAsString(drm);
                broadcastMessage(json);

                // Update Position and broadcast Game-State:
                game.updatePlayerPosition(roll, playerId);
                broadcastGameState();

            } else if (payload.startsWith("UPDATE_MONEY:")) {
                try {
                    int amount = Integer.parseInt(payload.substring("UPDATE_MONEY:".length()));
                    game.updatePlayerMoney(playerId, amount);
                    broadcastGameState();
                } catch (NumberFormatException e) {
                    System.err.println("Invalid money update format: " + sanitizeForLog(payload));
                }
            } else if (payload.startsWith("BUY_PROPERTY:")) {
                handleBuyProperty(session, payload);
            } else {
                String safePayload = sanitizeForLog(payload);
                logger.log(Level.INFO, "Received unknown message format: {0} from player {1}", new Object[]{safePayload, playerId});
                broadcastMessage("Player " + playerId + ": " + safePayload);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling message from player {0}: {1}", new Object[]{playerId, e.getMessage()});
            sendMessageToSession(session, createJsonError("Server error processing your request."));
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessions.remove(session);
        broadcastMessage("Player left: " + session.getId() + " (Total: " + sessions.size() + ")");
        System.out.println("Player disconnected: " + session.getId());
        game.removePlayer(session.getId());
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending game state: {0}", e.getMessage());
        }
    }

    private void handleBuyProperty(WebSocketSession session, String payload) {
        String playerId = session.getId();
        try {
            int propertyId = Integer.parseInt(payload.substring("BUY_PROPERTY:".length()));
            logger.log(Level.INFO, "Player {0} attempts to buy property {1}", new Object[]{playerId, propertyId});

            Optional<Player> playerOpt = game.getPlayerById(playerId);
            if (playerOpt.isEmpty()) {
                logger.log(Level.WARNING, "Player {0} not found in game state during buy attempt.", playerId);
                sendMessageToSession(session, createJsonError("Player not found."));
                return;
            }
            Player player = playerOpt.get();

            if (propertyTransactionService.canBuyProperty(player, propertyId)) {
                boolean success = propertyTransactionService.buyProperty(player, propertyId);
                if (success) {
                    logger.log(Level.INFO, "Property {0} bought successfully by player {1}", new Object[]{propertyId, playerId});
                    broadcastMessage(createJsonMessage("Player " + playerId + " bought property " + propertyId));
                    broadcastGameState();
                } else {
                    logger.log(Level.WARNING, "Property purchase failed for player {0}, property {1} after canBuy check.", new Object[]{playerId, propertyId});
                    sendMessageToSession(session, createJsonError("Failed to buy property due to server error."));
                }
            } else {
                if (!game.isPlayerTurn(playerId)) {
                    logger.log(Level.WARNING, "Player {0} attempted to buy property {1} when it's not their turn", new Object[]{playerId, propertyId});
                    sendMessageToSession(session, createJsonError("Cannot buy property - it's not your turn."));
                } else {
                    logger.log(Level.WARNING, "Property purchase failed for player {0}, property {1} after canBuy check.", new Object[]{playerId, propertyId});
                    sendMessageToSession(session, createJsonError("Cannot buy property (insufficient funds or already owned)."));
                }
            }

        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid property ID in payload from player {0}: {1}", new Object[]{playerId, sanitizeForLog(payload)});
            sendMessageToSession(session, createJsonError("Invalid property ID format."));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling BUY_PROPERTY for player {0}: {1}", new Object[]{playerId, e.getMessage()});
            sendMessageToSession(session, createJsonError("Server error handling buy property request."));
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
}