package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.*;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import model.DiceManager;
import model.DiceManagerInterface;
import model.Game;
import model.Player;
import model.properties.BaseProperty;
import data.Deals.DealProposalMessage;
import data.Deals.DealResponseMessage;
import data.Deals.DealResponseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
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
    final Map<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final Game game = new Game();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DiceManagerInterface diceManager;

    @Autowired
    private GameHistoryService gameHistoryService;
    @Autowired
    private CardDeckService cardDeckService;
    @Autowired
    private PropertyTransactionService propertyTransactionService;
    @Autowired
    PropertyService propertyService;
    @Autowired
    RentCollectionService rentCollectionService;
    @Autowired
    RentCalculationService rentCalculationService;
    @Autowired
    private CheatService cheatService;
    @Autowired
    private DealService dealService;

    @PostConstruct
    public void init() {
        dealService.setGame(game);
    }

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

            logger.log(Level.INFO, "Player connected: {0} | Name: {1}", new Object[]{userId, name}); //bewusst geloggt aktuell
            broadcastMessage("SYSTEM: " + name + " (" + userId + ") joined the game");

            // Spielstart-Logik anpassen
            if (sessionToUserId.size() >= 2 && sessionToUserId.size() <= 4) {
                startGame();
            }

            broadcastGameState();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing INIT: {0}", e.getMessage()); //bewusst geloggt aktuell
        }
    }

    private void handleTaxPayment(String payload, String userId) {
        try {
            TaxPaymentMessage taxMsg = objectMapper.readValue(payload, TaxPaymentMessage.class);
            logger.info("Player " + taxMsg.getPlayerId()
                    + " has to pay taxes"); //bewusst geloggt aktuell

            if (taxMsg.getPlayerId().equals(userId)) {
                game.updatePlayerMoney(userId, -taxMsg.getAmount());
                broadcastMessage(payload);
                broadcastGameState();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing tax payment message: {0}", e.getMessage());//bewusst geloggt aktuell
        }
    }

    public class JsonDeserializationException extends RuntimeException {

        public JsonDeserializationException(String message, Throwable cause) {
            super(message, cause);
        }

        public JsonDeserializationException(String message) {
            super(message);
        }
    }

    private void handleManualRoll(String payload, String userId, WebSocketSession session) {
        try {
            int manualRoll = Integer.parseInt(payload.substring("MANUAL_ROLL:".length()));
            if (manualRoll < 1 || manualRoll > 39) {
                sendMessageToSession(session, createJsonError("Invalid roll value. Must be between 1 and 39."));
                return;
            }

            logger.log(Level.INFO, "Player {0} manually rolled {1}", new Object[]{userId, manualRoll});//bewusst geloggt aktuell

            DiceRollMessage drm = new DiceRollMessage(userId, manualRoll, true, false);
            String json = objectMapper.writeValueAsString(drm);
            broadcastMessage(json);


            if (game.updatePlayerPosition(manualRoll, userId)) {
                broadcastMessage(PLAYER_PREFIX + userId + " passed GO and collected €200");
            }

            broadcastGameState();
        } catch (NumberFormatException e) {
            sendMessageToSession(session, createJsonError("Invalid manual roll format. Please provide a number between 1 and 39."));
        } catch (JsonProcessingException e) {
            throw new JsonDeserializationException("Fehler beim Deserialisieren des JSON-Objekts", e);
        }
    }

    private void handleUpdateMoney(String payload, String userId) {
        try {
            int amount = Integer.parseInt(payload.substring("UPDATE_MONEY:".length()));
            game.updatePlayerMoney(userId, amount);
            broadcastGameState();
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "Invalid money update format: {0}", sanitizeForLog(payload));//bewusst geloggt aktuell
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
                    handleEndGame();
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
            if (payload.contains("\"type\":\"CHEAT_MESSAGE\"")) {
                logger.log(Level.INFO, "Received cheat message from player {0}", userId);//bewusst geloggt aktuell
                broadcastMessage(payload);
                handleCheatMessage(payload, userId);
                return;
            }
            if (payload.contains("\"type\":\"CHAT_MESSAGE\"")) {
                logger.log(Level.INFO, "Received chat message from player {0}", userId);//bewusst geloggt aktuell
                broadcastMessage(payload);
                return;
            }
            if (payload.contains("\"type\":\"TAX_PAYMENT\"")) {
                handleTaxPayment(payload, userId);
                return;
            }
            if (payload.contains("\"type\":\"RENT_PAYMENT\"")) {
                try {
                    RentPaymentMessage rentMsg = objectMapper.readValue(payload, RentPaymentMessage.class);
                    logger.info("Processing rent payment for property " + rentMsg.getPropertyId());
                    
                    // Get the property
                    BaseProperty property = propertyTransactionService.findPropertyById(rentMsg.getPropertyId());
                    if (property == null) {
                        logger.warning("Property not found for ID: " + rentMsg.getPropertyId());
                        return;
                    }

                    // Get the players involved
                    Player renter = game.getPlayerById(rentMsg.getPlayerId()).orElse(null);
                    if (renter == null) {
                        logger.warning("Renter not found: " + rentMsg.getPlayerId());
                        return;
                    }

                    // Get the property owner
                    Player owner = game.getPlayerById(property.getOwnerId()).orElse(null);
                    if (owner == null) {
                        logger.warning("Property owner not found for property: " + property.getName());
                        return;
                    }

                    // Calculate rent amount
                    int rentAmount = rentCalculationService.calculateRent(property, owner, renter);
                    logger.info("Calculated rent amount: " + rentAmount + " for property " + property.getName());

                    // Create complete rent payment message
                    RentPaymentMessage completeRentMsg = new RentPaymentMessage(
                        renter.getId(),
                        owner.getId(),
                        property.getId(),
                        property.getName(),
                        rentAmount
                    );
                    String jsonRent = objectMapper.writeValueAsString(completeRentMsg);
                    broadcastMessage(jsonRent);

                    // Process the rent collection
                    boolean rentCollected = rentCollectionService.collectRent(renter, property, owner);
                    if (rentCollected) {
                        logger.info("Rent of " + rentAmount + " collected from player " + renter.getId() + 
                            " for property " + property.getName());
                        broadcastGameState();
                    } else {
                        logger.warning("Failed to collect rent for property " + property.getName());
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error processing rent payment message: {0}", e.getMessage());
                }
                return;
            }
            if (payload.contains("\"type\":\"PULL_CARD\"")) {
                PullCardMessage pull = objectMapper.readValue(payload, PullCardMessage.class);
                logger.info("Player " + pull.getPlayerId()
                        + " requested a " + pull.getCardType() + " card");//bewusst geloggt aktuell

                model.cards.CardType deckType = model.cards.CardType.valueOf(pull.getCardType());
                model.cards.Card card = cardDeckService.drawCard(deckType);

                if (pull.getPlayerId().equals(userId)) {
                    card.apply(game, pull.getPlayerId());

                    DrawnCardMessage reply = new DrawnCardMessage(
                            pull.getPlayerId(),
                            pull.getCardType(),
                            card
                    );
                    String jsonReply = objectMapper.writeValueAsString(reply);
                    sendMessageToSession(session, jsonReply);
                    logger.info("Player " + pull.getPlayerId() + " received a drawn card");//bewusst geloggt aktuell
                    broadcastGameState();
                }
                return;
            }
            if (payload.contains("\"type\":\"DEAL_PROPOSAL\"")) {
                DealProposalMessage deal = objectMapper.readValue(payload, DealProposalMessage.class);
                logger.info("Received deal proposal from " + deal.getFromPlayerId());

                WebSocketSession targetSession = findSessionByPlayerId(deal.getToPlayerId());
                if (targetSession != null) {
                    sendMessageToSession(targetSession, payload);
                } else {
                    logger.warning("Target player session not found for deal proposal");
                }
                return;
            }

            if (payload.contains("\"type\":\"DEAL_RESPONSE\"")) {
                DealResponseMessage response = objectMapper.readValue(payload, DealResponseMessage.class);
                logger.info("Received deal response: " + response.getResponseType()
                        + " from " + response.getFromPlayerId()
                        + " to " + response.getToPlayerId());

                if (response.getResponseType() == DealResponseType.ACCEPT) {
                    dealService.executeTrade(response);
                    broadcastGameState();
                }

                WebSocketSession targetSession = findSessionByPlayerId(response.getToPlayerId());
                if (targetSession != null) {
                    sendMessageToSession(targetSession, payload);
                } else {
                    logger.warning("Target player session not found for deal response");
                }
                return;
            }
            if (payload.trim().equalsIgnoreCase("Roll")) {
                if (!game.isPlayerTurn(userId)) {
                    sendMessageToSession(session, createJsonError("Not your turn!"));
                    return;
                }
                Player player = game.getPlayerById(userId).orElse(null);
                if (player == null) return;

                if (player.isInJail()) {
                    sendMessageToSession(session,
                            createJsonError("You are in jail and cannot roll. End your turn."));
                    return;
                }

                if (player.hasRolledThisTurn()) {
                    sendMessageToSession(session, createJsonError("You already rolled this turn."));
                    return;
                }

                int roll = diceManager.rollDices();
                boolean isPasch = diceManager.isPasch();
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("Spieler %s hat geworfen: %s | Pasch: %s",
                            userId,
                            diceManager.getLastRollValues().toString(),
                            isPasch));
                }
                player.setHasRolledThisTurn(!isPasch);
                logger.log(Level.INFO, "Player {0} rolled {1}", new Object[]{userId, roll});//bewusst geloggt aktuell

                DiceRollMessage drm = new DiceRollMessage(userId, roll, false, isPasch);
                String json = objectMapper.writeValueAsString(drm);
                broadcastMessage(json);

                // Update Position and broadcast Game-State:
                if (game.updatePlayerPosition(roll, userId)) {
                    broadcastMessage(PLAYER_PREFIX + userId + " passed GO and collected €200");
                }
                handlePlayerLanding(player);

            } else if ("NEXT_TURN".equals(payload)) {
                logger.log(Level.INFO, "Received NEXT_TURN from {0}", userId);

                if (!game.isPlayerTurn(userId)) {
                    sendMessageToSession(session, createJsonError("Not your turn!"));
                    return;
                }

                Optional<Player> playerOpt = game.getPlayerById(userId);
                if (playerOpt.isPresent()) {
                    Player player = playerOpt.get();
                    if (player.isInJail()) {
                        player.reduceJailTurns();
                        if (!player.isInJail()) {
                            broadcastMessage("Player " + userId + " is released from jail!");
                        }
                        // Always advance to next player after jail turn
                        game.nextPlayer();
                    } else {
                        game.nextPlayer(); // Normal turn advancement
                    }
                }

                broadcastGameState();

            } else if (payload.startsWith("MANUAL_ROLL:")) {
                handleManualRoll(payload, userId, session);
            } else if (payload.startsWith("UPDATE_MONEY:")) {
                handleUpdateMoney(payload, userId);
            } else if (payload.startsWith("BUY_PROPERTY:")) {
                handleBuyProperty(session, userId, payload);
            } else {
                String safePayload = sanitizeForLog(payload);
                logger.log(Level.INFO, "Received unknown message format: {0} from player {1}", new Object[]{safePayload, userId});//bewusst geloggt aktuell
                broadcastMessage(PLAYER_PREFIX + userId + ": " + safePayload);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling message from player {0}: {1}", new Object[]{userId, e.getMessage()});//bewusst geloggt aktuell
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
            broadcastGameState();
            logger.log(Level.INFO, "Player disconnected: {0}", userId);//bewusst geloggt aktuell
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
                logger.log(Level.SEVERE, "Error sending message: {0}", e.getMessage());//bewusst geloggt aktuell
            }
        }
    }

    void broadcastGameState() {
        try {
            String gameState = objectMapper.writeValueAsString(game.getPlayerInfo());
            broadcastMessage("GAME_STATE:" + gameState);
            broadcastMessage("PLAYER_TURN:" + game.getCurrentPlayer().getId());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error broadcasting game state: {0}", e.getMessage());//bewusst geloggt aktuell
        }
    }

    private void startGame() {
        try {
            String gameState = objectMapper.writeValueAsString(game.getPlayerInfo());
            broadcastMessage("GAME_STATE:" + gameState);
            broadcastMessage("Game started! " + sessions.size() + " players are connected.");
            logger.log(Level.INFO, "Game started with {0} players!", sessions.size());//bewusst geloggt aktuell
            game.start();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending game state: {0}", e.getMessage());//bewusst geloggt aktuell
        }
    }

    private void handleBuyProperty(WebSocketSession session, String userId, String payload) {
        try {
            int propertyId = Integer.parseInt(payload.substring("BUY_PROPERTY:".length()));
            logger.log(Level.INFO, "Player {0} attempts to buy property {1}", new Object[]{userId, propertyId});//bewusst geloggt aktuell

            Optional<Player> playerOpt = game.getPlayerById(userId);
            if (playerOpt.isEmpty()) {
                logger.log(Level.WARNING, "Player {0} not found in game state during buy attempt.", userId);//bewusst geloggt aktuell
                sendMessageToSession(session, createJsonError("Player not found."));
                return;
            }
            Player player = playerOpt.get();

            if (propertyTransactionService.canBuyProperty(player, propertyId)) {
                boolean success = propertyTransactionService.buyProperty(player, propertyId);
                if (success) {
                    logger.log(Level.INFO, "Property {0} bought successfully by player {1}", new Object[]{propertyId, userId});//bewusst geloggt aktuell
                    broadcastMessage(createJsonMessage(PLAYER_PREFIX + userId + " bought property " + propertyId));
                    broadcastGameState();
                } else {
                    logger.log(Level.WARNING, "Property purchase failed for player {0}, property {1} after canBuy check.", new Object[]{userId, propertyId});//bewusst geloggt aktuell
                    sendMessageToSession(session, createJsonError("Failed to buy property due to server error."));
                }
            } else {
                if (!game.isPlayerTurn(userId)) {
                    logger.log(Level.WARNING, "Player {0} attempted to buy property {1} when it is not their turn", new Object[]{userId, propertyId});//bewusst geloggt aktuell
                    sendMessageToSession(session, createJsonError("Cannot buy property - it's not your turn."));
                } else {
                    logger.log(Level.WARNING, "Property purchase failed for player {0}, property {1} after canBuy check.", new Object[]{userId, propertyId});//bewusst geloggt aktuell
                    sendMessageToSession(session, createJsonError("Cannot buy property (insufficient funds or already owned)."));
                }
            }

        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid property ID in payload from player {0}: {1}", new Object[]{userId, sanitizeForLog(payload)});//bewusst geloggt aktuell
            sendMessageToSession(session, createJsonError("Invalid property ID format."));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling BUY_PROPERTY for player {0}: {1}", new Object[]{userId, e.getMessage()});//bewusst geloggt aktuell
            sendMessageToSession(session, createJsonError("Server error handling buy property request."));
        }
    }

    void handleCheatMessage(String payload, String userId) throws JsonProcessingException {
        CheatCodeMessage cheatCodeMessage = objectMapper.readValue(payload, CheatCodeMessage.class);
        String cheatCode = cheatCodeMessage.getMessage();
        Optional<Player> optionalPlayer = game.getPlayerById(userId);
        if (optionalPlayer.isPresent()) {
            Player player = optionalPlayer.get();
            try {
                int amount = cheatService.getAmount(cheatCode, player.getMoney());
                game.updatePlayerMoney(userId, amount);
                broadcastGameState();
            } catch (NumberFormatException e) {
                logger.log(Level.SEVERE, "Invalid money update format: {0}", sanitizeForLog(payload));
            }
        } else {
            logger.log(Level.WARNING, "Player not found for cheat code handling (userId={0})", userId);
        }
    }

    /**
     * Behandelt die Beendigung eines Spiels und speichert die Spielhistorie
     */
    private void handleEndGame() {
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

            logger.info("Spiel beendet und Spielhistorie gespeichert");//bewusst geloggt aktuell
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Beenden des Spiels", e);//bewusst geloggt aktuell
        }
        try {
            // Erstellen einer ClearChatMessage
            Map<String, String> clearChatMessage = new HashMap<>();
            clearChatMessage.put("type", "CLEAR_CHAT");
            clearChatMessage.put("reason", "Game has ended");

            String clearChatJson = objectMapper.writeValueAsString(clearChatMessage);

            // Senden der Nachricht an alle Clients
            broadcastMessage(clearChatJson);

            logger.info("Sent chat clear signal to all clients");

        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, "Error creating clear chat message: " + e.getMessage());
        }

    }


    private void sendMessageToSession(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending message to session {0}: {1}", new Object[]{session.getId(), e.getMessage()});//bewusst geloggt aktuell
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

    void handleGiveUp(WebSocketSession session, JsonNode jsonNode) {
        String quittingUserId = jsonNode.get("userId").asText();

        if (!game.isPlayerTurn(quittingUserId)) {
            sendMessageToSession(session,
                    createJsonError("You can only give up on your turn."));
            return;
        }

        try {
            logger.log(Level.INFO,
                    "Player {0} has given up",
                    quittingUserId);

            game.giveUp(quittingUserId);

            // Do we have a winner already?
            if (game.getPlayers().size() == 1) {
                String winnerId = game.getPlayers().get(0).getId();

                HasWonMessage win = new HasWonMessage(winnerId);
                String winJson = objectMapper.writeValueAsString(win);
                broadcastMessage(winJson);

                // Wrap up the game
                handleEndGame();
                return;
            }

            GiveUpMessage msg = new GiveUpMessage(quittingUserId);
            String json = objectMapper.writeValueAsString(msg);
            broadcastMessage(json);
            broadcastGameState();

        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE,
                    "Error serializing GIVE_UP for {0}: {1}",
                    new Object[]{ quittingUserId, e.getMessage() });
            sendMessageToSession(session,
                    createJsonError("Server error processing give up."));
        }
    }

    private void handlePlayerLanding(Player player) {
        try {

            int position = player.getPosition();

            // Check for tax squares
            if (position == 30) {
                game.sendToJail(player.getId());
                broadcastMessage("Player " + player.getId() + " goes to jail!");
            }
            else if (position == 4) {  // Einkommensteuer
                game.updatePlayerMoney(player.getId(), -200);  // Deduct money first
                TaxPaymentMessage taxMsg = new TaxPaymentMessage(player.getId(), 200, "EINKOMMENSTEUER");
                String jsonTax = objectMapper.writeValueAsString(taxMsg);
                broadcastMessage(jsonTax);
            } else if (position == 38) {  // Zusatzsteuer
                game.updatePlayerMoney(player.getId(), -100);  // Deduct money first
                TaxPaymentMessage taxMsg = new TaxPaymentMessage(player.getId(), 100, "ZUSATZSTEUER");
                String jsonTax = objectMapper.writeValueAsString(taxMsg);
                broadcastMessage(jsonTax);
            }
            // Check for property and collect rent if applicable
            BaseProperty property = propertyService.getPropertyByPosition(position);
            if (property != null) {
                // Get the property owner first
                Player owner = game.getPlayerById(property.getOwnerId()).orElse(null);
                if (owner != null) {
                    // Calculate rent amount
                    int rentAmount = rentCalculationService.calculateRent(property, owner, player);
                    
                    // Create and broadcast rent payment message
                    RentPaymentMessage rentMsg = new RentPaymentMessage(
                            player.getId(),
                            owner.getId(),
                            property.getId(),
                            property.getName(),
                            rentAmount
                    );
                    String jsonRent = objectMapper.writeValueAsString(rentMsg);
                    broadcastMessage(jsonRent);
                    
                    // Now try to collect the rent
                    boolean rentCollected = rentCollectionService.collectRent(player, property, owner);
                    if (rentCollected) {
                        logger.log(Level.INFO, "Rent of {0} collected from player {1} for property {2}", 
                            new Object[]{rentAmount, player.getId(), property.getName()});
                    } else {
                        logger.warning("Failed to collect rent for property " + property.getName());
                    }
                }
            }

            broadcastGameState();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling player landing: {0}", e.getMessage());
        }
    }
    private WebSocketSession findSessionByPlayerId(String playerId) {
        for (Map.Entry<String, String> entry : sessionToUserId.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                String sessionId = entry.getKey();
                for (WebSocketSession session : sessions) {
                    if (session.getId().equals(sessionId)) {
                        return session;
                    }
                }
            }
        }
        return null;
    }
}