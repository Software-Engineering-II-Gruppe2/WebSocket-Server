package at.aau.serg.monopoly.websoket;

import at.aau.serg.monopoly.firebase.UserStatisticsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import data.*;
import data.deals.CounterProposalMessage;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import model.*;
import model.properties.BaseProperty;
import data.deals.DealProposalMessage;
import data.deals.DealResponseMessage;
import data.deals.DealResponseType;
import model.properties.HouseableProperty;
import model.properties.TrainStation;
import model.properties.Utility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import model.ChatMessage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
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
    // oben im Handler
    private final ScheduledExecutorService disconnectExec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "DisconnectTimer");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();
    private static final int DISCONNECT_GRACE_SEC = 5;

    // Felder
    private final ScheduledExecutorService turnTimerExec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TurnTimer");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, ScheduledFuture<?>> turnTimers = new ConcurrentHashMap<>();
    private static final int TURN_TIMEOUT_SEC = 30;


    private DiceManagerInterface diceManager;
    private final Map<String, Set<String>> kickVotes = new ConcurrentHashMap<>();
    private static final String BOUGHT_PROPERTY_MSG = " bought property ";
    private final String USER_ID = "userId";

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
    @Autowired
    private UserStatisticsService userStatisticsService;


    BotManager botManager;

    @PostConstruct
    public void init() {
        dealService.setGame(game);
        game.getDiceManager().initializeStandardDices();
        game.setPropertyService(propertyService);
        game.setPropertyTransactionService(propertyTransactionService);
        initializeBotManager();
    }


    public void initializeBotManager() {
        botManager = new BotManager(
                game,
                propertyTransactionService,
                new BotManager.BotCallback() {
                    @Override
                    public void broadcast(String m) {
                        broadcastMessage(m);
                    }

                    @Override
                    public void updateGameState() {
                        broadcastGameState();
                    }

                    @Override
                    public void advanceToNextPlayer() {
                        switchToNextPlayer();
                    }

                    @Override
                    public void checkBankruptcy() {        // ➋ einfach durchreichen
                        checkAllPlayersForBankruptcy();
                    }
                });
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        // Falls noch ein Kick-Timer läuft, abbrechen
        String userId = sessionToUserId.get(session.getId());
        if (userId == null) {
            sendMessageToSession(session, createJsonError("Send INIT message first"));
            return;
        }
        Optional.ofNullable(disconnectTasks.remove(userId))
                .ifPresent(f -> f.cancel(false));
    }


    protected void handleInitMessage(WebSocketSession session, JsonNode jsonNode) {
        try {
            String userId = jsonNode.get(USER_ID).asText();
            String name = jsonNode.get("name").asText();

            if (userId == null || sessionToUserId.containsValue(userId)) {
                sendMessageToSession(session, createJsonError("Invalid user"));
                return;
            }

            if (game.isStarted() && game.getPlayerById(userId).map(p -> !p.isConnected()).orElse(false)) {
                sendMessageToSession(session, createJsonError("Rejoin not allowed. You have been disconnected."));
                return;
            }


            // Spieler mit Firebase-ID hinzufügen
            game.addPlayer(userId, name);
            sessionToUserId.put(session.getId(), userId);

            logger.log(Level.INFO, "Player connected: {0} | Name: {1}", new Object[]{userId, name}); //bewusst geloggt aktuell
            broadcastMessage("SYSTEM: " + name + " (" + userId + ") joined the game");

            // Spielstart-Logik anpassen
            if (!game.isStarted() && sessionToUserId.size() >= 2 && sessionToUserId.size() <= 4) {
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
            logger.info(PLAYER_PREFIX + taxMsg.getPlayerId()
                    + " has to pay taxes"); //bewusst geloggt aktuell

            if (taxMsg.getPlayerId().equals(userId)) {
                game.updatePlayerMoney(userId, -taxMsg.getAmount());
                broadcastMessage(payload);
                broadcastGameState();
                checkAllPlayersForBankruptcy();
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

            if (game.isPlayerTurn(userId)) {
                refreshTurnTimer(userId);
            }
            broadcastGameState();
            checkAllPlayersForBankruptcy();
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
            checkAllPlayersForBankruptcy();
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "Invalid money update format: {0}", sanitizeForLog(payload));//bewusst geloggt aktuell
        }
    }


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload   = message.getPayload();
        String sessionId = session.getId();

        /* ───────── 1) INIT/END_GAME/GIVE_UP/SELL_PROPERTY als JSON ───────── */
        try {
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
                    handleGiveUpFromClient(session, jsonNode);
                    return;
                } else if ("SELL_PROPERTY".equals(type)) {
                    String uid = sessionToUserId.get(sessionId);
                    if (uid != null) {
                        handleSellProperty(session, payload, uid);
                    }
                    return;
                }
            }
        } catch (IOException ignored) {
            // Kein JSON; Verarbeitung läuft unten weiter
        }

        /* ───────── 2) Zugehörige Spieler-Session prüfen ───────── */
        String userId = sessionToUserId.get(sessionId);
        if (userId == null) {
            sendMessageToSession(session, createJsonError("Send INIT message first"));
            return;
        }

        /* ───────── 3) Sonderbehandlung SHAKE_REQUEST ───────── */
        if (payload.contains("\"type\":\"SHAKE_REQUEST\"")) {
            try {
                ShakeMessage shake = objectMapper.readValue(payload, ShakeMessage.class);
                logger.log(Level.INFO, "Player {0} has shaken his device", shake.getPlayerId());
                handleDiceRoll(session, userId);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error parsing SHAKE_MESSAGE: {0}", ex.getMessage());
            }
            return;
        }

        /* ───────── 4) Alle übrigen Nachrichten ───────── */
        try {
            /* 4a · JSON-Nachrichten ------------------------------------ */
            if (payload.contains("\"type\":\"CHEAT_MESSAGE\"")) {
                logger.log(Level.INFO, "Received cheat message from player {0}", userId);
                broadcastMessage(payload);
                handleCheatMessage(payload, userId);
                return;
            }

            if (payload.contains("\"type\":\"CHAT_MESSAGE\"")) {
                ChatMessage chat = objectMapper.readValue(payload, ChatMessage.class);
                if (chat.getMessage().startsWith("KICK ")) {
                    logger.log(Level.INFO, "Received kick request from {0}: {1}",
                            new Object[]{userId, chat.getMessage()});
                    handleKickVote(session, chat.getMessage(), userId);
                } else {
                    broadcastMessage(payload);
                }
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

                    BaseProperty property = propertyTransactionService.findPropertyById(rentMsg.getPropertyId());
                    if (property == null) {
                        logger.warning("Property not found for ID: " + rentMsg.getPropertyId());
                        return;
                    }

                    Player renter = game.getPlayerById(rentMsg.getPlayerId()).orElse(null);
                    if (renter == null) {
                        logger.warning("Renter not found: " + rentMsg.getPlayerId());
                        return;
                    }

                    Player owner = game.getPlayerById(property.getOwnerId()).orElse(null);
                    if (owner == null) {
                        logger.warning("Property owner not found for property: " + property.getName());
                        return;
                    }

                    int rentAmount = rentCalculationService.calculateRent(property, owner, renter);
                    logger.info("Calculated rent amount: " + rentAmount + " for property " + property.getName());

                    RentPaymentMessage completeRentMsg = new RentPaymentMessage(
                            renter.getId(), owner.getId(), property.getId(),
                            property.getName(), rentAmount);
                    String jsonRent = objectMapper.writeValueAsString(completeRentMsg);
                    broadcastMessage(jsonRent);
                    checkAllPlayersForBankruptcy();

                    boolean rentCollected = rentCollectionService.collectRent(renter, property, owner);
                    if (rentCollected) {
                        logger.info("Rent of " + rentAmount + " collected from player " + renter.getId()
                                + " for property " + property.getName());
                        broadcastGameState();
                        checkAllPlayersForBankruptcy();
                    } else {
                        logger.warning("Failed to collect rent for property " + property.getName());
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error processing rent payment: {0}", e.getMessage());
                }
                return;
            }

            if (payload.contains("\"type\":\"PULL_CARD\"")) {
                PullCardMessage pull = objectMapper.readValue(payload, PullCardMessage.class);
                logger.info(PLAYER_PREFIX + pull.getPlayerId() + " requested a " + pull.getCardType() + " card");

                model.cards.CardType deckType = model.cards.CardType.valueOf(pull.getCardType());
                model.cards.Card card = cardDeckService.drawCard(deckType);

                if (pull.getPlayerId().equals(userId)) {
                    card.apply(game, pull.getPlayerId());
                    DrawnCardMessage reply = new DrawnCardMessage(
                            pull.getPlayerId(), pull.getCardType(), card);
                    sendMessageToSession(session, objectMapper.writeValueAsString(reply));
                    logger.info(PLAYER_PREFIX + pull.getPlayerId() + " received a drawn card");
                    broadcastGameState();
                    checkAllPlayersForBankruptcy();
                }
                return;
            }

            if (payload.contains("\"type\":\"DEAL_PROPOSAL\"")) {
                DealProposalMessage deal = objectMapper.readValue(payload, DealProposalMessage.class);
                logger.info("Received deal proposal from " + deal.getFromPlayerId());
                dealService.saveProposal(deal);

                WebSocketSession target = findSessionByPlayerId(deal.getToPlayerId());
                if (target != null) {
                    sendMessageToSession(target, payload);
                } else {
                    logger.warning("Target player session not found for deal proposal");
                }
                return;
            }

            if (payload.contains("\"type\":\"DEAL_RESPONSE\"")) {
                DealResponseMessage resp = objectMapper.readValue(payload, DealResponseMessage.class);
                logger.info("Received deal response: " + resp.getResponseType()
                        + " from " + resp.getFromPlayerId() + " to " + resp.getToPlayerId());

                if (resp.getResponseType() == DealResponseType.ACCEPT) {
                    DealProposalMessage proposal = dealService.executeTrade(resp);
                    if (proposal != null) {
                        for (int id : proposal.getOfferedPropertyIds()) {
                            broadcastMessage(createJsonMessage(
                                    PLAYER_PREFIX + proposal.getToPlayerId() + BOUGHT_PROPERTY_MSG + id));
                        }
                        for (int id : proposal.getRequestedPropertyIds()) {
                            broadcastMessage(createJsonMessage(
                                    PLAYER_PREFIX + proposal.getFromPlayerId() + BOUGHT_PROPERTY_MSG + id));
                        }
                    }
                    broadcastGameState();
                    checkAllPlayersForBankruptcy();
                }

                WebSocketSession target = findSessionByPlayerId(resp.getToPlayerId());
                if (target != null) {
                    sendMessageToSession(target, payload);
                } else {
                    logger.warning("Target player session not found for deal response");
                }
                return;
            }

            if (payload.contains("\"type\":\"COUNTER_OFFER\"")) {
                CounterProposalMessage counter = objectMapper.readValue(payload, CounterProposalMessage.class);
                logger.info("Received counter offer from " + counter.getFromPlayerId());
                dealService.saveCounterProposal(counter);

                WebSocketSession target = findSessionByPlayerId(counter.getToPlayerId());
                if (target != null) {
                    sendMessageToSession(target, payload);
                } else {
                    logger.warning("Target player session not found for counter offer");
                }
                return;
            }

            /* 4b · Plain-Text-Kommandos --------------------------------- */
            if (payload.trim().equalsIgnoreCase("Roll")) {
                handleDiceRoll(session, userId);
                return;
            }

            if ("NEXT_TURN".equals(payload)) {
                logger.log(Level.INFO, "Received NEXT_TURN from {0}", userId);

                if (!game.isPlayerTurn(userId)) {
                    sendMessageToSession(session, createJsonError("Not your turn!"));
                    return;
                }

                Player current = game.getCurrentPlayer();
                if (current == null) return;

                /* Gefängnis zuerst */
                if (current.isInJail()) {
                    current.reduceJailTurns();
                    if (!current.isInJail()) {
                        broadcastMessage("Player " + current.getName() + " is released from jail!");
                    }
                    cancelTurnTimer(userId);
                    switchToNextPlayer();
                    checkAllPlayersForBankruptcy();
                    return;
                }

                /* Muss vorher gewürfelt haben */
                if (!current.isHasRolledThisTurn()) {
                    sendMessageToSession(session, createJsonError("Roll the dice first!"));
                    return;
                }

                /* Zug übergeben */
                cancelTurnTimer(userId);
                switchToNextPlayer();
                checkAllPlayersForBankruptcy();
                return;
            }

            if (payload.startsWith("MANUAL_ROLL:")) {
                handleManualRoll(payload, userId, session);
                return;
            }

            if (payload.startsWith("UPDATE_MONEY:")) {
                handleUpdateMoney(payload, userId);
                return;
            }

            if (payload.startsWith("BUY_PROPERTY:")) {
                handleBuyProperty(session, userId, payload);
                return;
            }

            if (payload.startsWith("SELL_PROPERTY:")) {
                handleSellProperty(session, payload, userId);
                return;
            }

            /* 4c · Unbekanntes Format ----------------------------------- */
            String safe = sanitizeForLog(payload);
            logger.log(Level.INFO, "Unknown message format: {0} from {1}", new Object[]{safe, userId});
            broadcastMessage(PLAYER_PREFIX + userId + ": " + safe);
            checkAllPlayersForBankruptcy();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling message from {0}: {1}",
                    new Object[]{userId, e.getMessage()});
            sendMessageToSession(session, createJsonError("Server error processing your request."));
        }
    }



    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        String userId = sessionToUserId.remove(session.getId());
        sessions.remove(session);
        if (userId == null) return;
        game.markPlayerDisconnected(userId);
        // ➊  Prüfen, ob noch ein echter Spieler online ist
        if (!anyHumanConnected()) {
            logger.info("No human players connected – ending game.");
            handleEndGame();          // beendet Spiel + stoppt BotManager
            return;
        }


        // ➜ 5-s-Timer statt sofort Bot
        ScheduledFuture<?> task = disconnectExec.schedule(() -> {

            /* 1 ─ Mensch endgültig ersetzen */
            cancelTurnTimer(userId);                //  ← laufenden 30-s-Timer stoppen
            game.replaceDisconnectedWithBot(userId);

            broadcastMessage("SYSTEM: " + userId +
                    " hat die Verbindung verloren und wurde durch einen Bot ersetzt.");
            broadcastGameState();
            checkAllPlayersForBankruptcy();

            /* 2 ─ Bot sofort loslegen lassen, falls er an der Reihe ist */
            if (game.isPlayerTurn(userId)) {
                botManager.queueBotTurn(userId);    //  ← jetzt BOT_DELAY_SEC später würfeln
            }

        }, DISCONNECT_GRACE_SEC, TimeUnit.SECONDS);

        disconnectTasks.put(userId, task);
    }


    private void handleDiceRoll(WebSocketSession session, String userId) throws JsonProcessingException {

        /*  1. Gültigkeits-Checks  */
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

        /* ── 2. Würfeln über Game  */
        int roll = game.handleDiceRoll(userId);               // zentrales Handling
        boolean pasch = game.getDiceManager().isPasch();
        if (game.isPlayerTurn(userId)) {
            refreshTurnTimer(userId);
        }

        // Bei Pasch darf noch einmal gewürfelt werden
        if (pasch) {
            player.setHasRolledThisTurn(false);
        }

        /* ── 3. Nachricht an alle Clients  */
        DiceRollMessage drm = new DiceRollMessage(
                userId,
                roll,
                /* isManual  = */ false,
                /* pasch     = */ pasch
        );
        String json = objectMapper.writeValueAsString(drm);
        broadcastMessage(json);

        // Update Position and broadcast Game-State:
        if (game.updatePlayerPosition(roll, userId)) {
            broadcastMessage(PLAYER_PREFIX + userId + " passed GO and collected €200");
        }
        handlePlayerLanding(player);
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
            Player current = game.getCurrentPlayer();
            if (current != null) {
                broadcastMessage("PLAYER_TURN:" + current.getId());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error broadcasting game state: {0}", e.getMessage());//bewusst geloggt aktuell
        }
    }

    private void startGame() {
        try {
            /* 1 ─ Spielfeld zurücksetzen + Spielstand senden */
            propertyService.resetBoardOwnership();
            game.resetForNewMatch();

            String gameState = objectMapper.writeValueAsString(game.getPlayerInfo());
            broadcastMessage("GAME_STATE:" + gameState);
            broadcastMessage("Game started! " + sessions.size() + " players are connected.");
            logger.log(Level.INFO, "Game started with {0} players!", sessions.size());//bewusst geloggt aktuell
            game.start();

            /* 3 ─ Bot-Manager neu aufsetzen (alter Thread sauber beenden) */
            if (botManager != null) botManager.shutdown();
            initializeBotManager();   // erzeugt eine *neue* Instanz
            botManager.start();       // kickt ggf. den ersten Bot an (Fix #1)

            /* 4 ─ Zug-Timer nur, wenn jetzt ein Mensch dran ist */
            Player current = game.getCurrentPlayer();
            if (current != null && !current.isBot()) {
                scheduleTurnTimer(current.getId());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending game state: {0}", e.getMessage());//bewusst geloggt aktuell
        }
    }

    private void handleBuyProperty(WebSocketSession session, String userId, String payload) {
        try {
            int propertyId = Integer.parseInt(payload.substring("BUY_PROPERTY:".length()));

            Optional<Player> playerOpt = game.getPlayerById(userId);
            if (playerOpt.isEmpty()) {
                sendMessageToSession(session, createJsonError("Player not found."));
                return;
            }
            Player player = playerOpt.get();

            if (propertyTransactionService.canBuyProperty(player, propertyId)) {
                boolean success = propertyTransactionService.buyProperty(player, propertyId);
                if (success) {
                    broadcastMessage(createJsonMessage(PLAYER_PREFIX + userId + " bought property " + propertyId));
                    broadcastGameState();
                    if (game.isPlayerTurn(userId)) {
                        refreshTurnTimer(userId);
                    }
                    checkAllPlayersForBankruptcy();

                } else {
                    sendMessageToSession(session, createJsonError("Failed to buy property due to server error."));
                }
            } else {
                if (!game.isPlayerTurn(userId)) {
                    sendMessageToSession(session, createJsonError("Cannot buy property - it's not your turn."));
                } else {
                    sendMessageToSession(session, createJsonError("Cannot buy property (insufficient funds or already owned)."));
                }
            }
        } catch (NumberFormatException e) {
            sendMessageToSession(session, createJsonError("Invalid property ID format."));
        } catch (Exception e) {
            sendMessageToSession(session, createJsonError("Server error handling buy property request."));
        }
    }

    private void handleSellProperty(WebSocketSession session, String payload, String userId) {
        try {
            int propertyId;
            // Check if the payload is in JSON format
            if (payload.contains("\"type\":\"SELL_PROPERTY\"")) {
                JsonNode jsonNode = objectMapper.readTree(payload);
                propertyId = jsonNode.get("propertyId").asInt();
            } else {
                // Handle string format
                propertyId = Integer.parseInt(payload.substring("SELL_PROPERTY:".length()));
            }

            Optional<Player> playerOpt = game.getPlayerById(userId);
            if (playerOpt.isEmpty()) {
                sendMessageToSession(session, createJsonError("Player not found."));
                return;
            }
            Player player = playerOpt.get();

            if (propertyTransactionService.sellProperty(player, propertyId)) {
                broadcastMessage(createJsonMessage(PLAYER_PREFIX + userId + " sold property " + propertyId));
                broadcastGameState();
                if (game.isPlayerTurn(userId)) {
                    refreshTurnTimer(userId);
                }
                checkAllPlayersForBankruptcy();
            } else {
                sendMessageToSession(session, createJsonError("Cannot sell property (not owned by player)."));
            }
        } catch (NumberFormatException e) {
            sendMessageToSession(session, createJsonError("Invalid property ID format."));
        } catch (Exception e) {
            sendMessageToSession(session, createJsonError("Server error handling sell property request."));
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
                checkAllPlayersForBankruptcy();
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
    /**
     * Beendet die laufende Partie, informiert die Clients,
     * speichert Statistiken und räumt alle internen Strukturen auf.
     * Alle Web-Socket-Sessions werden dabei **sauber geschlossen**,
     * sodass sich die Spieler für die nächste Runde neu verbinden müssen.
     */
    private void handleEndGame() {

        /* ───────── 1. Gewinner ermitteln & Historie schreiben ───────── */
        try {
            String winnerId = game.determineWinner();                 // kann null sein
            int durationMinutes = game.endGame(winnerId);             // Spiel beenden

            gameHistoryService.saveGameHistoryForAllPlayers(
                    game.getPlayers(), durationMinutes, winnerId);

            // Stats updaten
            List<String> ids = game.getPlayers().stream()
                    .map(Player::getId).toList();
            userStatisticsService.updateStatsForUsers(ids);

            // Meldung an alle
            broadcastMessage(createJsonMessage(
                    "Das Spiel wurde beendet. Der Gewinner ist " +
                            game.getPlayerById(winnerId)
                                    .map(Player::getName)
                                    .orElse("unbekannt")));
            logger.info("Spiel beendet und Spielhistorie gespeichert");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Fehler beim Beenden des Spiels", ex);
        }

        /* ───────── 2. CLEAR_CHAT & RESET an die Clients ───────── */
        try {
            Map<String,String> clearChat = Map.of(
                    "type",   "CLEAR_CHAT",
                    "reason", "Game has ended");
            broadcastMessage(objectMapper.writeValueAsString(clearChat));

            ObjectNode reset = objectMapper.createObjectNode();
            reset.put("type", "RESET");
            broadcastMessage(objectMapper.writeValueAsString(reset));
        } catch (JsonProcessingException ex) {
            logger.log(Level.SEVERE, "Fehler beim Senden von CLEAR_CHAT/RESET", ex);
        }

        /* ───────── 3. alle offenen Sessions schließen ───────── */
        for (WebSocketSession s : new ArrayList<>(sessions)) {
            try {
                if (s.isOpen()) {
                    s.close(CloseStatus.NORMAL);
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING,
                        "Error while closing session {0}: {1}",
                        new Object[]{s.getId(), ex.getMessage()});
            }
        }
        sessions.clear();
        sessionToUserId.clear();

        /* ───────── 4. interne Threads & Timer stoppen ───────── */
        if (botManager != null) { botManager.shutdown(); botManager = null; }

        turnTimers.values().forEach(t -> t.cancel(false));
        turnTimers.clear();
        disconnectTasks.values().forEach(t -> t.cancel(false));
        disconnectTasks.clear();

        /* ───────── 5. Spiel- und Spielfeld-Daten zurücksetzen ───────── */
        propertyService.resetBoardOwnership();   // Eigentümer & Häuser löschen
        game.reset();                            // Geld, Positionen, Flags
        game.getPlayers().clear();               // Spieler entfernen
        game.setStarted(false);                  // neue INITs erlauben
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

    void handleGiveUpFromClient(WebSocketSession session, JsonNode jsonNode) {
        String quittingUserId = jsonNode.get(USER_ID).asText();

        if (!game.isPlayerTurn(quittingUserId)) {
            sendMessageToSession(session,
                    createJsonError("You can only give up on your turn."));
            return;
        }

        logger.log(Level.INFO, "Player {0} has given up", quittingUserId);

        int durationMinutes = 0;
        if (game.getStartTime() != null) {
            long diffMillis = new Date().getTime() - game.getStartTime().getTime();
            durationMinutes = (int) (diffMillis / 1000 / 60);
        }

        int endMoney = game.getPlayerById(quittingUserId)
                .map(Player::getMoney)
                .orElse(0);

        processPlayerGiveUp(quittingUserId, durationMinutes, endMoney);
    }

    // Helper method to handle giveUp
    public void processPlayerGiveUp(String quittingUserId, int durationMinutes, int endMoney) {

        //mark player as looser for firebase
        gameHistoryService.markPlayerAsLoser(quittingUserId, durationMinutes, endMoney);

        //handle give up in game logic
        game.giveUp(quittingUserId);

        // Broadcast a GIVE_UP message
        try {
            GiveUpMessage giveUpMsg = new GiveUpMessage(quittingUserId);
            String json = objectMapper.writeValueAsString(giveUpMsg);
            broadcastMessage(json);
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, "Error serializing GIVE_UP for {0}: {1}",
                    new Object[]{quittingUserId, e.getMessage()});
        }

        // Do we have a winner already?
        if (game.getPlayers().size() == 1) {
            String winnerId = game.getPlayers().get(0).getId();
            try {
                HasWonMessage win = new HasWonMessage(winnerId);
                String winJson = objectMapper.writeValueAsString(win);
                broadcastMessage(winJson);
            } catch (JsonProcessingException e) {
                logger.log(Level.SEVERE, "Error serializing HAS_WON: {0}", e.getMessage());
            }
            // Wrap up the game
            handleEndGame();
            return;
        }

        broadcastGameState();
        checkAllPlayersForBankruptcy();
    }

    // Helper method to calculate the value of all owned properties
    private int sumLiquidationValueOfOwnedProperties(String playerId) {
        int total = 0;

        // Houseable properties
        for (HouseableProperty p : propertyService.getHouseableProperties()) {
            if (playerId.equals(p.getOwnerId())) {
                total += p.getPurchasePrice() / 2;
            }
        }
        // Train stations
        for (TrainStation ts : propertyService.getTrainStations()) {
            if (playerId.equals(ts.getOwnerId())) {
                total += ts.getPurchasePrice() / 2;
            }
        }
        // Utilities
        for (Utility u : propertyService.getUtilities()) {
            if (playerId.equals(u.getOwnerId())) {
                total += u.getPurchasePrice() / 2;
            }
        }

        return total;
    }

    // Helper method to check if any player is bankrupt
    private void checkAllPlayersForBankruptcy() {
        // Copy of the players list
        List<Player> snapshot = new ArrayList<>(game.getPlayers());

        for (Player p : snapshot) {
            String pid = p.getId();

            // Net worth: cash + sum(property)
            int cash = p.getMoney();
            int assets = sumLiquidationValueOfOwnedProperties(pid);
            int netWorth = cash + assets;

            if (netWorth <= 0) {
                logger.log(Level.INFO, "Player {0} is bankrupt (net worth {1}). Forcing GIVE_UP.",
                        new Object[]{pid, netWorth});

                // Broadcast an IS_BANKRUPT
                try {
                    ObjectNode bankruptNotice = objectMapper.createObjectNode();
                    bankruptNotice.put("type", "IS_BANKRUPT");
                    bankruptNotice.put("userId", pid);
                    broadcastMessage(objectMapper.writeValueAsString(bankruptNotice));
                } catch (JsonProcessingException e) {
                    logger.log(Level.SEVERE, "Error serializing IS_BANKRUPT for {0}: {1}",
                            new Object[]{pid, e.getMessage()});
                }

                int playedDuration = game.getDurationPlayed();
                // Process GIVE_UP
                processPlayerGiveUp(pid, playedDuration, p.getMoney());
            }
        }
    }

    private void resetGame() {
        game.getPlayers().clear();

        // New INITs will now be accepted
        sessionToUserId.clear();

        diceManager = new DiceManager();
        diceManager.initializeStandardDices();
    }

    private void handleKickVote(WebSocketSession session, String payload, String voterId) {
        String targetName = payload.substring("KICK ".length()).trim();

        Optional<Player> targetOpt = game.getPlayers().stream()
                .filter(p -> p.getName().equals(targetName))
                .findFirst();

        if (targetOpt.isEmpty()) {
            sendMessageToSession(session,
                    createJsonError("Player not found: " + targetName));
            return;
        }

        String targetId = targetOpt.get().getId();

        Optional<Player> voterOpt = game.getPlayerById(voterId);
        if (voterOpt.isEmpty()) {
            sendMessageToSession(session,
                    createJsonError("Voting player not found: " + voterId));
            return;
        }
        String voterName = voterOpt.get().getName();

        kickVotes.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet())
                .add(voterId);

        int votesFor = kickVotes.get(targetId).size();
        int totalPlayers = game.getPlayers().size();

        // Broadcast:
        broadcastMessage("SYSTEM: " + voterName
                + " voted to kick " + targetName
                + " (" + votesFor + "/" + totalPlayers + ")");

        // Wenn mehr als 50% der Spieler voten -> GIVE_UP = KICK
        if (votesFor > totalPlayers / 2.0) {
            processPlayerGiveUp(targetId, 0, 0);
            kickVotes.remove(targetId);
        }
    }


        private void handlePlayerLanding (Player player){
            try {

                int position = player.getPosition();

                // Check for tax squares
                if (position == 30) {
                    game.sendToJail(player.getId());
                    broadcastMessage("Player " + player.getId() + " goes to jail!");
                }   else if (position == 4) {  // Einkommensteuer
                    game.updatePlayerMoney(player.getId(), -200);  // Deduct money first
                    TaxPaymentMessage taxMsg = new TaxPaymentMessage(player.getId(), 200, "EINKOMMENSTEUER");
                    String jsonTax = objectMapper.writeValueAsString(taxMsg);
                    broadcastMessage(jsonTax);
                } else if (position == 38) {  // Zusatzsteuer
                    if (player.getMoney() < 100) {
                        processPlayerGiveUp(player.getId(), game.getDurationPlayed(), player.getMoney());
                        return;
                    }
                    game.updatePlayerMoney(player.getId(), -100);
                    TaxPaymentMessage taxMsg = new TaxPaymentMessage(player.getId(), 100, "ZUSATZSTEUER");
                    String jsonTax = objectMapper.writeValueAsString(taxMsg);
                    broadcastMessage(jsonTax);
                }

                // 2. Miete für besetzte Felder einsammeln
                BaseProperty property = propertyService.getPropertyByPosition(position);
                if (property != null) {
                    // Get the property owner first
                    Player owner = game.getPlayerById(property.getOwnerId()).orElse(null);
                    if (owner != null && !owner.getId().equals(player.getId())) {
                        int rentAmount = rentCalculationService.calculateRent(property, owner, player);

                        if (player.getMoney() < rentAmount) {
                            processPlayerGiveUp(player.getId(), game.getDurationPlayed(), player.getMoney());
                            return;
                        }

                        RentPaymentMessage rentMsg = new RentPaymentMessage(
                                player.getId(), owner.getId(), property.getId(), property.getName(), rentAmount
                        );
                        String jsonRent = objectMapper.writeValueAsString(rentMsg);
                        broadcastMessage(jsonRent);

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
                checkAllPlayersForBankruptcy();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling player landing: {0}", e.getMessage());
            }
        }


        private boolean anyHumanConnected () {
            return game.getPlayers()
                    .stream()
                    .anyMatch(p -> p.isConnected() && !p.isBot());
        }


        private WebSocketSession findSessionByPlayerId (String playerId){
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


        private void scheduleTurnTimer (String playerId){
            cancelTurnTimer(playerId);

            if (game.getPlayerById(playerId).map(Player::isBot).orElse(true)) return;

            ScheduledFuture<?> t = turnTimerExec.schedule(() -> {
                if (!game.isPlayerTurn(playerId)) return;          // Zug schon vorbei
                if (game.getPlayerById(playerId).map(Player::isBot).orElse(true)) return;

                game.markPlayerDisconnected(playerId);
                game.replaceDisconnectedWithBot(playerId);
                broadcastMessage("SYSTEM: " + playerId
                        + " hat 30 Sekunden nicht beendet und wurde zum Bot.");
                broadcastGameState();
                if (!anyHumanConnected()) {          // ‼ keine Menschen mehr?
                    logger.info("All players are bots – ending game.");
                    handleEndGame();                 // Match stoppen
                    return;                          //  ←  NICHTS mehr anstoßen
                }

                if (game.isPlayerTurn(playerId)) botManager.queueBotTurn(playerId);
            }, TURN_TIMEOUT_SEC, TimeUnit.SECONDS);

            turnTimers.put(playerId, t);
        }

        private void cancelTurnTimer (String playerId){
            Optional.ofNullable(turnTimers.remove(playerId))
                    .ifPresent(f -> f.cancel(false));
        }

        /** Wechselt auf den nächsten gültigen Spieler und kümmert sich um
         *  - Flag‐Reset
         *  - Timer stoppen/neu starten
         *  - Broadcast
         *  - ggf. Bot-Queue
         */
        private void switchToNextPlayer () {
            Player prev = game.getCurrentPlayer();
            if (prev != null) {
                prev.setHasRolledThisTurn(false);
                cancelTurnTimer(prev.getId());      // Timer des Vor-Spielers stoppen
            }

            game.nextPlayer();
            Player current = game.getCurrentPlayer();
            if (current == null) {
                handleEndGame();
                return;
            }

            if (!anyHumanConnected()) {
                handleEndGame();
                return;
            }
            broadcastGameState();

            /* ─── HIER WIEDER EIN TIMER, ABER NUR FÜR MENSCHEN ─── */
            if (!current.isBot()) {
                scheduleTurnTimer(current.getId());
            } else {                                // Bot → sofort in die Queue
                botManager.queueBotTurn(current.getId());
            }
        }


        private void refreshTurnTimer (String playerId){
            cancelTurnTimer(playerId);          // Alte Aufgabe löschen
            scheduleTurnTimer(playerId);        // 30 s ab jetzt
        }

    /** Trennt alle noch offenen Verbindungen sauber. */
    private void closeAllClientSessions() {
        for (WebSocketSession s : new ArrayList<>(sessions)) {
            try {
                if (s.isOpen()) {
                    // 1000 = NORMAL_CLOSURE
                    s.close(CloseStatus.NORMAL);
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING,
                        "Error while closing session {0}: {1}",
                        new Object[]{s.getId(), ex.getMessage()});
            }
        }
        sessions.clear();          // Liste jetzt wirklich leer
        sessionToUserId.clear();   // Zuordnungen löschen
    }



}
