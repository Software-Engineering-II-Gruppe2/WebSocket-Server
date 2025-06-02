package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.Game;
import model.Player;
import model.BotManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GameWebSocketHandlerUnitTest {

    private WebSocketSession session;
    private GameWebSocketHandler gameWebSocketHandler;
    @Mock
    private BotManager botManager;
    @Mock
    private Game game;
    @Captor
    private ArgumentCaptor<TextMessage> messageCaptor;

    @BeforeEach
    void setUp() {
        gameWebSocketHandler = new GameWebSocketHandler();
        session = mock(WebSocketSession.class);
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(gameWebSocketHandler, "botManager", botManager);
        when(session.getId()).thenReturn("1");
        when(session.isOpen()).thenReturn(true);

        // Establish connection and send INIT
        gameWebSocketHandler.afterConnectionEstablished(session);
        sendInit(session, "1", "Player1");
        clearInvocations(session);
    }

    @AfterEach
    void tearDown() {
        gameWebSocketHandler = null;
        session = null;
    }

    private void sendInit(WebSocketSession session, String userId, String name) {
        String initJson = String.format(
                "{\"type\":\"INIT\",\"userId\":\"%s\",\"name\":\"%s\"}",
                userId, name
        );
        gameWebSocketHandler.handleTextMessage(session, new TextMessage(initJson));
    }

    @Test
    void testAfterConnectionEstablished() throws Exception {
        // No messages after setup
        verify(session, never()).sendMessage(any());
    }

    @Test
    void testGameStartTriggeredOnFourConnections() throws Exception {
        gameWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        WebSocketSession[] testSessions = new WebSocketSession[4];
        for (int i = 1; i <= 4; i++) {
            testSessions[i - 1] = mock(WebSocketSession.class);
            when(testSessions[i - 1].getId()).thenReturn(String.valueOf(i));
            when(testSessions[i - 1].isOpen()).thenReturn(true);
            gameWebSocketHandler.afterConnectionEstablished(testSessions[i - 1]);
            sendInit(testSessions[i - 1], String.valueOf(i), "Player" + i);
            // Clear invocations only for the first three sessions
            if (i < 4) {
                clearInvocations(testSessions[i - 1]);
            }
        }

        // Verify all four sessions received the game start message
        for (WebSocketSession s : testSessions) {
            verify(s, atLeastOnce()).sendMessage(argThat(msg -> {
                String payload = ((TextMessage) msg).getPayload();
                return payload.contains("Game started");
            }));
        }
    }

    @Test
    void testHandleTextMessage() throws IOException {
        clearInvocations(session);
        gameWebSocketHandler.handleTextMessage(session, new TextMessage("Test"));
        verify(session).sendMessage(new TextMessage("Player 1: Test"));
    }

    @Test
    void testAfterConnectionClosed_broadcastsBotReplacementMessageToOthers() throws Exception {
        // Arrange
        String disconnectedSessionId = "session1";
        String otherSessionId = "session2";
        String userId = "user123";

        WebSocketSession otherSession = mock(WebSocketSession.class);
        when(otherSession.getId()).thenReturn(otherSessionId);
        when(otherSession.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn(disconnectedSessionId);
        when(session.isOpen()).thenReturn(true);

        // Spieler-Setup
        Player player = new Player(userId, "TestUser");
        player.setConnected(true);

        Game mockGame = mock(Game.class);
        when(mockGame.getPlayerById(userId)).thenReturn(Optional.of(player));
        when(mockGame.getCurrentPlayer()).thenReturn(player);
        when(mockGame.getPlayers()).thenReturn(List.of(player));

        // Setze Felder
        ReflectionTestUtils.setField(gameWebSocketHandler, "game", mockGame);
        ReflectionTestUtils.setField(gameWebSocketHandler, "sessionToUserId", new ConcurrentHashMap<>(Map.of(disconnectedSessionId, userId)));

        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(session); // wird geschlossen
        sessions.add(otherSession); // bleibt offen und bekommt die Nachricht
        ReflectionTestUtils.setField(gameWebSocketHandler, "sessions", sessions);

        BotManager botManagerMock = mock(BotManager.class);
        ReflectionTestUtils.setField(gameWebSocketHandler, "botManager", botManagerMock);

        // Act
        gameWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Assert: verify that the other session received the broadcast
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(otherSession, atLeastOnce()).sendMessage(captor.capture());

        boolean foundSystemMessage = captor.getAllValues().stream()
                .map(TextMessage::getPayload)
                .anyMatch(msg -> msg.contains("Player left: user123 and was replaced by a bot."));

        assertTrue(foundSystemMessage, "Expected SYSTEM message about bot replacement to be sent.");
    }



    @Test
    void testHandleUpdateMoneyMessage() throws IOException {
        clearInvocations(session);
        gameWebSocketHandler.handleTextMessage(session, new TextMessage("UPDATE_MONEY:500"));
        verify(session, atLeastOnce()).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().startsWith("GAME_STATE:")));
    }

    @Test
    void testHandleInvalidUpdateMoneyMessage() throws IOException {
        clearInvocations(session);
        gameWebSocketHandler.handleTextMessage(session, new TextMessage("UPDATE_MONEY:notanumber"));
        verify(session, never()).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().startsWith("GAME_STATE:")));
    }

    @Test
    void testHandleBuyProperty_PlayerNotFound() throws IOException {
        clearInvocations(session);
        WebSocketSession unknown = mock(WebSocketSession.class);
        when(unknown.getId()).thenReturn("unknown");
        when(unknown.isOpen()).thenReturn(true);
        gameWebSocketHandler.handleTextMessage(unknown, new TextMessage("BUY_PROPERTY:1"));
        verify(unknown).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("Send INIT message first")));
    }

    @Test
    void testHandleInitMessageValidUser() throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn("123");
        when(s.isOpen()).thenReturn(true);

        JsonNode mockNode = mock(JsonNode.class);
        JsonNode userIdNode = mock(JsonNode.class);
        JsonNode nameNode = mock(JsonNode.class);

        when(mockNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn("user123");
        when(mockNode.get("name")).thenReturn(nameNode);
        when(nameNode.asText()).thenReturn("Player123");

        gameWebSocketHandler.afterConnectionEstablished(s);
        clearInvocations(s);

        Field f = GameWebSocketHandler.class.getDeclaredField("sessionToUserId");
        f.setAccessible(true);
        Map<String, String> sessionToUserId = (Map<String, String>) f.get(gameWebSocketHandler);

        gameWebSocketHandler.handleInitMessage(s, mockNode);

        assertTrue(sessionToUserId.containsKey("123"));
        assertEquals("user123", sessionToUserId.get("123"));
        verify(s, never()).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("ERROR")));
    }

    @Test
    void testHandleInitMessageDuplicateUserId() throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn("1");
        when(s.isOpen()).thenReturn(true);

        Field f = GameWebSocketHandler.class.getDeclaredField("sessionToUserId");
        f.setAccessible(true);
        Map<String, String> sessionToUserId = (Map<String, String>) f.get(gameWebSocketHandler);
        sessionToUserId.put("anotherSession", "user123");

        JsonNode mockNode = mock(JsonNode.class);
        JsonNode userIdNode = mock(JsonNode.class);
        JsonNode nameNode = mock(JsonNode.class);

        when(mockNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn("user123");
        when(mockNode.get("name")).thenReturn(nameNode);
        when(nameNode.asText()).thenReturn("PlayerDuplicate");

        gameWebSocketHandler.handleInitMessage(s, mockNode);

        verify(s).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("Invalid user")));
    }

    @Test
    void testInvalidMessageHandling() throws Exception {
        GameWebSocketHandler handler = new GameWebSocketHandler();
        when(session.getId()).thenReturn("test-session-id");
        when(session.isOpen()).thenReturn(true);

        Field sessionsField = GameWebSocketHandler.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        ((CopyOnWriteArrayList<WebSocketSession>) sessionsField.get(handler)).add(session);

        // Init
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"INIT\",\"userId\":\"user123\",\"name\":\"TestUser\"}"));

        // Now send invalid message
        handler.handleTextMessage(session, new TextMessage("INVALID_MESSAGE"));

        // Verify broadcast fallback (not an error!)
        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("Player user123: INVALID_MESSAGE")
        ));
    }

    @Test
    void testPlayerDisconnect_sendsSystemMessageToOthers() throws Exception {
        // Arrange
        String sessionId = "testSessionId";
        String userId = "user123";
        Player player = new Player(userId, "TestUser");
        player.setConnected(true);

        // Erstelle eine zweite Session, die im Spiel bleibt
        WebSocketSession otherSession = mock(WebSocketSession.class);
        when(otherSession.isOpen()).thenReturn(true);
        when(otherSession.getId()).thenReturn("otherSession");

        // Session-Zuordnung: disconnected + ein anderer Spieler
        Map<String, String> sessionMap = new ConcurrentHashMap<>();
        sessionMap.put(sessionId, userId);
        sessionMap.put("otherSession", "player2");

        ReflectionTestUtils.setField(gameWebSocketHandler, "sessionToUserId", sessionMap);

        // Sessionliste (beide)
        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(session);        // wird getrennt
        sessions.add(otherSession);   // bleibt im Spiel
        ReflectionTestUtils.setField(gameWebSocketHandler, "sessions", sessions);

        // Game vorbereiten
        Game mockGame = mock(Game.class);
        when(session.getId()).thenReturn(sessionId);
        when(mockGame.getPlayerById(userId)).thenReturn(Optional.of(player));
        when(mockGame.getPlayers()).thenReturn(List.of(player));
        when(mockGame.getCurrentPlayer()).thenReturn(player);
        ReflectionTestUtils.setField(gameWebSocketHandler, "game", mockGame);

        // BotManager mocken, damit kein echter Bot aktiviert wird
        BotManager botManagerMock = mock(BotManager.class);
        ReflectionTestUtils.setField(gameWebSocketHandler, "botManager", botManagerMock);

        // Act – Verbindung trennen
        gameWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Assert – Nachricht an andere Session prüfen
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(otherSession, atLeastOnce()).sendMessage(messageCaptor.capture());

        boolean foundBotMessage = messageCaptor.getAllValues().stream()
                .map(TextMessage::getPayload)
                .anyMatch(msg -> msg.contains("was replaced by a bot"));

        assertTrue(foundBotMessage, "Expected SYSTEM message about bot replacement to be sent to other players.");
    }


}

