package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.JsonNode;
import model.Game;
import model.Player;
import model.BotManager;
import org.junit.jupiter.api.AfterEach;
import static org.awaitility.Awaitility.await;
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
import java.util.concurrent.TimeUnit;

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

    private Game prepareMockGameWithPlayer(String userId) {
        Player player = new Player(userId, "TestUser");
        player.setConnected(true);

        Game mockGame = mock(Game.class);
        when(mockGame.getPlayerById(userId)).thenReturn(Optional.of(player));
        when(mockGame.getCurrentPlayer()).thenReturn(player);
        when(mockGame.getPlayers()).thenReturn(List.of(player));
        when(mockGame.getPlayerInfo()).thenReturn(List.of());

        ReflectionTestUtils.setField(gameWebSocketHandler, "game", mockGame);
        ReflectionTestUtils.setField(gameWebSocketHandler, "botManager", mock(BotManager.class));
        return mockGame;
    }

    @Test
    void testAfterConnectionEstablished() throws Exception {
        // No messages after setup
        verify(session, never()).sendMessage(any());
    }

    @Test
    void testGameStartTriggeredOnFourConnections() throws Exception {
        // Vorab: gameHistoryService mocken und setzen
        GameHistoryService mockHistoryService = mock(GameHistoryService.class);
        ReflectionTestUtils.setField(gameWebSocketHandler, "gameHistoryService", mockHistoryService);

        // Simuliere 4 WebSocketSessions
        WebSocketSession[] testSessions = new WebSocketSession[4];
        for (int i = 0; i < 4; i++) {
            testSessions[i] = mock(WebSocketSession.class);
            when(testSessions[i].getId()).thenReturn(String.valueOf(i + 1));
            when(testSessions[i].isOpen()).thenReturn(true);
            gameWebSocketHandler.afterConnectionEstablished(testSessions[i]);
            sendInit(testSessions[i], String.valueOf(i + 1), "Player" + (i + 1));
            if (i < 3) clearInvocations(testSessions[i]);
        }

        // Verifiziere, dass eine Start-Meldung gesendet wurde
        for (WebSocketSession session : testSessions) {
            verify(session, atLeastOnce()).sendMessage(argThat(msg -> {
                String payload = ((TextMessage) msg).getPayload();
                return payload.contains("Game started")
                        || payload.contains("PLAYER_TURN")
                        || payload.contains("SYSTEM")
                        || payload.contains("GAME_STATE");
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
        String userId = "user123";
        String disconnectedSessionId = "session1";
        String otherSessionId = "session2";

        WebSocketSession otherSession = mock(WebSocketSession.class);
        when(otherSession.getId()).thenReturn(otherSessionId);
        when(otherSession.isOpen()).thenReturn(true);

        when(session.getId()).thenReturn(disconnectedSessionId);
        when(session.isOpen()).thenReturn(true);

        Game mockGame = prepareMockGameWithPlayer(userId);
        when(mockGame.isPlayerTurn(userId)).thenReturn(false);
        doNothing().when(mockGame).replaceDisconnectedWithBot(userId);
        ReflectionTestUtils.setField(gameWebSocketHandler, "game", mockGame);

        ReflectionTestUtils.setField(gameWebSocketHandler, "sessionToUserId",
                new ConcurrentHashMap<>(Map.of(disconnectedSessionId, userId)));

        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(session);
        sessions.add(otherSession);
        ReflectionTestUtils.setField(gameWebSocketHandler, "sessions", sessions);

        gameWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // ✅ Warten bis Bot-Ersetzung erfolgt
        await().atMost(6, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(otherSession, atLeastOnce()).sendMessage(captor.capture());

            boolean foundMessage = captor.getAllValues().stream()
                    .anyMatch(msg -> msg.getPayload().contains("durch einen Bot ersetzt"));
            assertTrue(foundMessage);
        });
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
        String sessionId = "testSessionId";
        String userId = "user123";

        // 1. Session vorbereiten
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);

        WebSocketSession otherSession = mock(WebSocketSession.class);
        when(otherSession.isOpen()).thenReturn(true);
        when(otherSession.getId()).thenReturn("otherSession");

        // 2. sessionToUserId Map vorbereiten
        Map<String, String> sessionMap = new ConcurrentHashMap<>();
        sessionMap.put(sessionId, userId);
        sessionMap.put("otherSession", "player2");
        ReflectionTestUtils.setField(gameWebSocketHandler, "sessionToUserId", sessionMap);

        // 3. Sitzungen vorbereiten
        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(session);
        sessions.add(otherSession);
        ReflectionTestUtils.setField(gameWebSocketHandler, "sessions", sessions);

        // 4. Spieler vorbereiten
        Player player = new Player(userId, "TestUser");
        player.setConnected(true);

        Game mockGame = mock(Game.class);
        when(mockGame.getPlayerById(userId)).thenReturn(Optional.of(player));
        when(mockGame.getCurrentPlayer()).thenReturn(player);
        when(mockGame.getPlayers()).thenReturn(List.of(player));
        when(mockGame.getPlayerInfo()).thenReturn(List.of());

        // 5. Stelle sicher, dass replaceDisconnectedWithBot aufgerufen wird
        doNothing().when(mockGame).replaceDisconnectedWithBot(userId);

        // 6. Spiel setzen
        ReflectionTestUtils.setField(gameWebSocketHandler, "game", mockGame);

        // 7. Test ausführen
        gameWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        await().atMost(6, TimeUnit.SECONDS).untilAsserted(() -> {
            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(otherSession, atLeastOnce()).sendMessage(messageCaptor.capture());

            boolean found = messageCaptor.getAllValues().stream()
                    .anyMatch(msg -> msg.getPayload().contains("wurde durch einen Bot ersetzt"));

            assertTrue(found);
        });
    }

    @Test
    void testHandleKickVoteIfPlayerNotFound() throws Exception {
        clearInvocations(session);
        String kickJson = "{\"type\":\"CHAT_MESSAGE\",\"playerId\":\"1\",\"message\":\"KICK Unknown\"}";
        gameWebSocketHandler.handleTextMessage(session, new TextMessage(kickJson));

        verify(session).sendMessage(argThat(msg -> {
            String payload = ((TextMessage) msg).getPayload();
            return payload.contains("\"type\":\"ERROR\"")
                    && payload.contains("Player not found: Unknown");
        }));
    }

    @Test
    void testHandleKickVoteSingleVoteBroadcastOnly() throws Exception {
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("2");
        when(session2.isOpen()).thenReturn(true);
        gameWebSocketHandler.afterConnectionEstablished(session2);
        sendInit(session2, "2", "Player2");
        clearInvocations(session);
        clearInvocations(session2);

        String vote1 = "{\"type\":\"CHAT_MESSAGE\",\"playerId\":\"1\",\"message\":\"KICK Player2\"}";
        gameWebSocketHandler.handleTextMessage(session, new TextMessage(vote1));

        String expected = "SYSTEM: Player1 voted to kick Player2 (1/2)";
        verify(session).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains(expected)));
        verify(session2).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains(expected)));
    }




}
