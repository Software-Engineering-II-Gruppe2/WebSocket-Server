package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.BotManager;
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

        // Setze alle benötigten Felder per Reflection
        ReflectionTestUtils.setField(handler, "game", game);
        ReflectionTestUtils.setField(handler, "propertyTransactionService", propertyTransactionService);
        ReflectionTestUtils.setField(handler, "objectMapper", mapper);

        // BotManager als Mock setzen, damit kein echter BotManager verwendet wird
        BotManager botManager = mock(BotManager.class);
        ReflectionTestUtils.setField(handler, "botManager", botManager);

        // Sitzungs-Zuordnung: Session-ID → User-ID
        ConcurrentHashMap<String, String> sessionMap = new ConcurrentHashMap<>();
        sessionMap.put("testSessionId", TEST_USER_ID);
        ReflectionTestUtils.setField(handler, "sessionToUserId", sessionMap);

        // Session-Liste (nur 1 Mock-Session drin)
        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(session);
        ReflectionTestUtils.setField(handler, "sessions", sessions);

        // Session-Mocks konfigurieren
        when(session.getId()).thenReturn("testSessionId");
        when(session.isOpen()).thenReturn(true);

        // Player-Mapping vorbereiten
        when(game.getPlayerById(TEST_USER_ID)).thenReturn(Optional.of(player));
        when(player.getId()).thenReturn(TEST_USER_ID);

        // Sende manuell ein INIT-JSON zur Spieler-Registrierung
        String initJson = mapper.createObjectNode()
                .put("type", "INIT")
                .put("userId", TEST_USER_ID)
                .put("name", TEST_PLAYER_NAME)
                .toString();

        handler.handleTextMessage(session, new TextMessage(initJson));

        // Danach alle vorherigen Sendungen zurücksetzen
        clearInvocations(session);
    }


    @Test
    void testSellProperty_Success() throws Exception {
        // Arrange
        int propertyId = 1;
        when(game.getCurrentPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(TEST_USER_ID);
        when(propertyTransactionService.sellProperty(player, propertyId)).thenReturn(true);

        // JSON-Nachricht wie vom Client
        String jsonMessage = mapper.createObjectNode()
                .put("type", "SELL_PROPERTY")
                .put("propertyId", propertyId)
                .toString();

        // Act
        handler.handleTextMessage(session, new TextMessage(jsonMessage));

        // Assert
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();
        messages.forEach(m -> System.out.println("DEBUG: " + m.getPayload()));

        boolean foundSuccessMessage = messages.stream()
                .anyMatch(msg -> msg.getPayload().contains("\"type\":\"PROPERTY_BOUGHT\"") &&
                        msg.getPayload().contains("sold property"));
        assertTrue(foundSuccessMessage, "Expected a PROPERTY_BOUGHT message indicating property was sold");

        verify(propertyTransactionService).sellProperty(player, propertyId);
    }




    @Test
    void testSellProperty_NotOwned() throws Exception {
        // Arrange
        int propertyId = 1;
        when(game.getCurrentPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(TEST_USER_ID);
        when(propertyTransactionService.sellProperty(player, propertyId)).thenReturn(false);

        // JSON-Nachricht wie sie vom Client erwartet wird
        String jsonMessage = mapper.createObjectNode()
                .put("type", "SELL_PROPERTY")
                .put("propertyId", propertyId)
                .toString();

        // Act
        handler.handleTextMessage(session, new TextMessage(jsonMessage));

        // Assert
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();

        messages.forEach(msg -> System.out.println("DEBUG: " + msg.getPayload()));

        boolean foundErrorMessage = messages.stream()
                .anyMatch(msg -> msg.getPayload().contains("Cannot sell property"));
        assertTrue(foundErrorMessage, "Expected to find a message containing 'Cannot sell property'");

        verify(propertyTransactionService).sellProperty(player, propertyId);
    }


    @Test
    void testSellProperty_InvalidFormat() throws Exception {
        // JSON mit ungültigem String als Property-ID
        String invalidJson = mapper.createObjectNode()
                .put("type", "SELL_PROPERTY")
                .put("propertyId", "invalid")  // Ungültiger Wert
                .toString();

        // Act
        handler.handleTextMessage(session, new TextMessage(invalidJson));

        // Assert
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();

        messages.forEach(msg -> System.out.println("DEBUG: " + msg.getPayload()));

        boolean foundErrorMessage = messages.stream()
                .anyMatch(msg -> msg.getPayload().contains("Cannot sell property"));
        assertTrue(foundErrorMessage, "Expected to find a message containing 'Cannot sell property'");


    }


    @Test
    void testSellProperty_PlayerNotFound() throws Exception {
        // Arrange
        when(game.getPlayerById(TEST_USER_ID)).thenReturn(Optional.empty());
        String jsonMessage = mapper.createObjectNode()
                .put("type", "SELL_PROPERTY")
                .put("propertyId", 1)
                .toString();

        handler.handleTextMessage(session, new TextMessage(jsonMessage));


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