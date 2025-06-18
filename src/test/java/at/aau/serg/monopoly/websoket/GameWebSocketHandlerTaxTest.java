package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.ObjectMapper;
import data.TaxPaymentMessage;
import model.Game;
import model.Player;
import model.DiceManagerInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameWebSocketHandlerTaxTest {
    private GameWebSocketHandler handler;
    private Game game;
    private WebSocketSession session;
    private ObjectMapper objectMapper;
    private static final String TEST_USER_ID = "testUserId";
    private static final String TEST_PLAYER_NAME = "Test Player";

    @BeforeEach
    void setUp() throws Exception {
        handler = new GameWebSocketHandler();
        game = new Game();
        session = mock(WebSocketSession.class);
        objectMapper = new ObjectMapper();

        // Set up test game state
        game.addPlayer(TEST_USER_ID, TEST_PLAYER_NAME);

        // Use reflection to set the private fields
        Field sessionToUserIdField = GameWebSocketHandler.class.getDeclaredField("sessionToUserId");
        sessionToUserIdField.setAccessible(true);
        ConcurrentHashMap<String, String> sessionMap = new ConcurrentHashMap<>();
        sessionToUserIdField.set(handler, sessionMap);

        Field gameField = GameWebSocketHandler.class.getDeclaredField("game");
        gameField.setAccessible(true);
        gameField.set(handler, game);

        // Set up session ID
        when(session.getId()).thenReturn("testSessionId");
        sessionMap.put("testSessionId", TEST_USER_ID);

        // Mock dice manager
        DiceManagerInterface diceManager = mock(DiceManagerInterface.class);
        Field diceManagerField = GameWebSocketHandler.class.getDeclaredField("diceManager");
        diceManagerField.setAccessible(true);
        diceManagerField.set(handler, diceManager);

        // Send INIT message to register the player
        String initJson = objectMapper.createObjectNode()
                .put("type", "INIT")
                .put("userId", TEST_USER_ID)
                .put("name", TEST_PLAYER_NAME)
                .toString();
        handler.handleTextMessage(session, new TextMessage(initJson));
    }

    @Test
    void testHandleValidTaxPaymentMessage() throws Exception {
        // Arrange
        String taxMessage = objectMapper.writeValueAsString(
                new TaxPaymentMessage(TEST_USER_ID, 200, "EINKOMMENSTEUER")
        );
        TextMessage message = new TextMessage(taxMessage);

        // Act
        handler.handleTextMessage(session, message);

        // Assert
        Player player = game.getPlayerById(TEST_USER_ID).orElseThrow();
        assertEquals(1300, player.getMoney()); // 1500 (starting) - 200 (tax)
    }

    @Test
    void testHandleTaxPaymentMessageFromDifferentPlayer() throws Exception {
        // Arrange
        String differentUserId = "differentUserId";
        String taxMessage = objectMapper.writeValueAsString(
                new TaxPaymentMessage(differentUserId, 200, "EINKOMMENSTEUER")
        );
        TextMessage message = new TextMessage(taxMessage);

        // Act
        handler.handleTextMessage(session, message);

        // Assert
        Player player = game.getPlayerById(TEST_USER_ID).orElseThrow();
        assertEquals(1500, player.getMoney()); // Money should not change
    }

    @Test
    void testHandleInvalidTaxPaymentMessage() {
        // Arrange
        String invalidMessage = "{\"type\":\"TAX_PAYMENT\",\"invalid\":\"json\"}";
        TextMessage message = new TextMessage(invalidMessage);

        // Act
        handler.handleTextMessage(session, message);

        // Assert
        Player player = game.getPlayerById(TEST_USER_ID).orElseThrow();
        assertEquals(1500, player.getMoney()); // Money should not change
    }

    @Test
    void testHandleZusatzsteuerTaxPayment() throws Exception {
        // Arrange
        String taxMessage = objectMapper.writeValueAsString(
                new TaxPaymentMessage(TEST_USER_ID, 100, "ZUSATZSTEUER")
        );
        TextMessage message = new TextMessage(taxMessage);

        // Act
        handler.handleTextMessage(session, message);

        // Assert
        Player player = game.getPlayerById(TEST_USER_ID).orElseThrow();
        assertEquals(1400, player.getMoney()); // 1500 (starting) - 100 (tax)
    }

    @Test
    void testHandleMultipleTaxPayments() throws Exception {
        // Arrange
        String einkommensteuerMessage = objectMapper.writeValueAsString(
                new TaxPaymentMessage(TEST_USER_ID, 200, "EINKOMMENSTEUER")
        );
        String zusatzsteuerMessage = objectMapper.writeValueAsString(
                new TaxPaymentMessage(TEST_USER_ID, 100, "ZUSATZSTEUER")
        );

        // Act
        handler.handleTextMessage(session, new TextMessage(einkommensteuerMessage));
        handler.handleTextMessage(session, new TextMessage(zusatzsteuerMessage));

        // Assert
        Player player = game.getPlayerById(TEST_USER_ID).orElseThrow();
        assertEquals(1200, player.getMoney()); // 1500 - 200 - 100
    }

    @Test
    void testTaxPaymentOnEinkommensteuerPosition() throws Exception {
        // Arrange
        Game spyGame = spy(new Game());
        spyGame.addPlayer(TEST_USER_ID, "TestPlayer");


        Field gameField = GameWebSocketHandler.class.getDeclaredField("game");
        gameField.setAccessible(true);
        gameField.set(handler, spyGame);

        Player player = spyGame.getPlayerById(TEST_USER_ID).orElseThrow();
        player.setPosition(0);
        int initialMoney = player.getMoney();

        // DiceManager mocken
        DiceManagerInterface mockDiceManager = mock(DiceManagerInterface.class);
        when(mockDiceManager.rollDices()).thenReturn(4);
        when(mockDiceManager.isPasch()).thenReturn(false);
        when(mockDiceManager.getLastRollValues()).thenReturn(List.of(4));

        Field diceManagerField = GameWebSocketHandler.class.getDeclaredField("diceManager");
        diceManagerField.setAccessible(true);
        diceManagerField.set(handler, mockDiceManager);

        doReturn(true).when(spyGame).updatePlayerPosition(4, TEST_USER_ID);
        player.setPosition(4);

        // Act: Roll
        handler.handleTextMessage(session, new TextMessage("Roll"));

        // Assert
        assertEquals(initialMoney - 200, player.getMoney());
    }

    @Test
    void testTaxPaymentOnZusatzsteuerPosition() throws Exception {
        // Arrange
        Game spyGame = spy(new Game());
        spyGame.addPlayer(TEST_USER_ID, "TestPlayer");

        Field gameField = GameWebSocketHandler.class.getDeclaredField("game");
        gameField.setAccessible(true);
        gameField.set(handler, spyGame);

        Player player = spyGame.getPlayerById(TEST_USER_ID).orElseThrow();
        player.setPosition(0);
        int initialMoney = player.getMoney();

        // DiceManager mocken
        DiceManagerInterface mockDiceManager = mock(DiceManagerInterface.class);
        when(mockDiceManager.rollDices()).thenReturn(38);
        when(mockDiceManager.isPasch()).thenReturn(false);
        when(mockDiceManager.getLastRollValues()).thenReturn(List.of(38));

        Field diceManagerField = GameWebSocketHandler.class.getDeclaredField("diceManager");
        diceManagerField.setAccessible(true);
        diceManagerField.set(handler, mockDiceManager);

        doReturn(true).when(spyGame).updatePlayerPosition(38, TEST_USER_ID);
        player.setPosition(38);

        // Act
        handler.handleTextMessage(session, new TextMessage("Roll"));

        // Assert
        assertEquals(initialMoney - 100, player.getMoney());
    }

    @Test
    void testNoTaxPaymentOnOtherPositions() throws Exception {
        // Arrange
        Player player = game.getPlayerById(TEST_USER_ID).orElseThrow();
        player.setPosition(0); // Start at position 0
        int initialMoney = player.getMoney();

        // Mock dice roll to land on a non-tax position
        Field diceManagerField = GameWebSocketHandler.class.getDeclaredField("diceManager");
        diceManagerField.setAccessible(true);
        DiceManagerInterface diceManager = (DiceManagerInterface) diceManagerField.get(handler);
        when(diceManager.rollDices()).thenReturn(5);

        // Act - Simulate dice roll
        handler.handleTextMessage(session, new TextMessage("Roll"));

        // Assert
        assertEquals(initialMoney, player.getMoney()); // Money should not change
    }

    @Test
    void testTaxPaymentAfterPassingGo() throws Exception {
        // Arrange
        Player player = game.getPlayerById(TEST_USER_ID).orElseThrow();
        player.setPosition(39); // Set player one position before GO
        int initialMoney = player.getMoney();

        // Mock dice roll to pass GO and land on Einkommensteuer
        Field diceManagerField = GameWebSocketHandler.class.getDeclaredField("diceManager");
        diceManagerField.setAccessible(true);
        DiceManagerInterface diceManager = (DiceManagerInterface) diceManagerField.get(handler);
        when(diceManager.rollDices()).thenReturn(5); // 39 + 5 = 44, wraps around to 4 (Einkommensteuer)

        // Act - Simulate dice roll that passes GO and lands on Einkommensteuer
        handler.handleTextMessage(session, new TextMessage("Roll"));

        // Assert
        assertEquals(initialMoney + 200 - 200, player.getMoney()); // Should get GO bonus and pay tax
    }

    @Test
    void testTaxPaymentWithMultiplePlayers() throws Exception {
        // Arrange
        Game spyGame = spy(new Game());
        spyGame.addPlayer(TEST_USER_ID, "TestPlayer");
        String otherPlayerId = "otherPlayerId";
        spyGame.addPlayer(otherPlayerId, "Other Player");

        Field gameField = GameWebSocketHandler.class.getDeclaredField("game");
        gameField.setAccessible(true);
        gameField.set(handler, spyGame);

        Player currentPlayer = spyGame.getPlayerById(TEST_USER_ID).orElseThrow();
        Player otherPlayer = spyGame.getPlayerById(otherPlayerId).orElseThrow();

        currentPlayer.setPosition(0); // Start at 0
        int currentPlayerInitialMoney = currentPlayer.getMoney();
        int otherPlayerInitialMoney = otherPlayer.getMoney();

        // DiceManager mocken
        DiceManagerInterface mockDiceManager = mock(DiceManagerInterface.class);
        when(mockDiceManager.rollDices()).thenReturn(4);
        when(mockDiceManager.isPasch()).thenReturn(false);
        when(mockDiceManager.getLastRollValues()).thenReturn(List.of(4));

        Field diceManagerField = GameWebSocketHandler.class.getDeclaredField("diceManager");
        diceManagerField.setAccessible(true);
        diceManagerField.set(handler, mockDiceManager);


        doReturn(true).when(spyGame).updatePlayerPosition(4, TEST_USER_ID);
        currentPlayer.setPosition(4);

        // Act
        handler.handleTextMessage(session, new TextMessage("Roll"));

        // Assert
        assertEquals(currentPlayerInitialMoney - 200, currentPlayer.getMoney()); // Steuern gezahlt
        assertEquals(otherPlayerInitialMoney, otherPlayer.getMoney());           // Andere bleibt gleich
    }
} 