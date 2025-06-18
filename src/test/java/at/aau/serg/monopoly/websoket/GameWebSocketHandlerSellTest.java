package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.Game;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameWebSocketHandlerSellTest {

    @Mock private WebSocketSession session;
    @Mock private Game game;
    @Mock private Player player;
    @Mock private PropertyTransactionService propertyTransactionService;
    @Captor private ArgumentCaptor<TextMessage> messageCaptor;

    private GameWebSocketHandler handler;
    private ObjectMapper mapper;
    private static final String TEST_USER_ID = "testUserId";
    private static final String TEST_PLAYER_NAME = "Test Player";

    @BeforeEach
    void setUp() {
        handler = new GameWebSocketHandler();
        mapper = new ObjectMapper();

        // Set up test game state
        when(game.getPlayerById(TEST_USER_ID)).thenReturn(Optional.of(player));

        // Use reflection to set the private fields
        ReflectionTestUtils.setField(handler, "game", game);
        ReflectionTestUtils.setField(handler, "propertyTransactionService", propertyTransactionService);

        // Set up session mapping
        ConcurrentHashMap<String, String> sessionMap = new ConcurrentHashMap<>();
        sessionMap.put("testSessionId", TEST_USER_ID);
        ReflectionTestUtils.setField(handler, "sessionToUserId", sessionMap);

        // Set up sessions list
        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(session);
        ReflectionTestUtils.setField(handler, "sessions", sessions);

        // Set up session ID
        when(session.getId()).thenReturn("testSessionId");
        when(session.isOpen()).thenReturn(true);

        // Send INIT message to register the player
        String initJson = mapper.createObjectNode()
                .put("type", "INIT")
                .put("userId", TEST_USER_ID)
                .put("name", TEST_PLAYER_NAME)
                .toString();
        handler.handleTextMessage(session, new TextMessage(initJson));

        // Clear all invocations after INIT setup
        clearInvocations(session);
    }

    @Test
    void testSellProperty_Success() throws Exception {
        // Arrange
        int propertyId = 1;
        when(game.getCurrentPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(TEST_USER_ID);
        when(propertyTransactionService.sellProperty(player, propertyId)).thenReturn(true);

        // Act
        handler.handleTextMessage(session, new TextMessage("SELL_PROPERTY:" + propertyId));

        // Assert
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();
        boolean foundSuccessMessage = messages.stream()
                .anyMatch(msg -> msg.getPayload().contains("sold property"));
        assertTrue(foundSuccessMessage, "Expected to find a message containing 'sold property'");
        verify(propertyTransactionService).sellProperty(player, propertyId);
    }

    @Test
    void testSellProperty_NotOwned() throws Exception {
        // Arrange
        int propertyId = 1;
        when(game.getCurrentPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(TEST_USER_ID);
        when(propertyTransactionService.sellProperty(player, propertyId)).thenReturn(false);

        // Act
        handler.handleTextMessage(session, new TextMessage("SELL_PROPERTY:" + propertyId));

        // Assert
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();
        boolean foundErrorMessage = messages.stream()
                .anyMatch(msg -> msg.getPayload().contains("Cannot sell property"));
        assertTrue(foundErrorMessage, "Expected to find a message containing 'Cannot sell property'");
        verify(propertyTransactionService).sellProperty(player, propertyId);
    }

    @Test
    void testSellProperty_InvalidFormat() throws Exception {
        // Act
        handler.handleTextMessage(session, new TextMessage("SELL_PROPERTY:invalid"));

        // Assert
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();
        boolean foundErrorMessage = messages.stream()
                .anyMatch(msg -> msg.getPayload().contains("Invalid property ID format"));
        assertTrue(foundErrorMessage, "Expected to find a message containing 'Invalid property ID format'");
    }

    @Test
    void testSellProperty_PlayerNotFound() throws Exception {
        // Arrange
        when(game.getPlayerById(TEST_USER_ID)).thenReturn(Optional.empty());

        // Act
        handler.handleTextMessage(session, new TextMessage("SELL_PROPERTY:1"));

        // Assert
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();
        boolean foundErrorMessage = messages.stream()
                .anyMatch(msg -> msg.getPayload().contains("Player not found"));
        assertTrue(foundErrorMessage, "Expected to find a message containing 'Player not found'");
    }

    @Test
    void testSellProperty_JsonFormat() throws Exception {
        // Arrange
        int propertyId = 1;
        when(game.getCurrentPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(TEST_USER_ID);
        when(propertyTransactionService.sellProperty(player, propertyId)).thenReturn(true);
        String jsonMessage = mapper.createObjectNode()
                .put("type", "SELL_PROPERTY")
                .put("propertyId", propertyId)
                .toString();

        // Act
        handler.handleTextMessage(session, new TextMessage(jsonMessage));

        // Assert
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();

        // Check if any of the messages contains the expected content
        boolean foundSuccessMessage = messages.stream()
                .anyMatch(msg -> msg.getPayload().contains("sold property"));
        assertTrue(foundSuccessMessage, "Expected to find a message containing 'sold property'");

        verify(propertyTransactionService).sellProperty(player, propertyId);
    }
} 