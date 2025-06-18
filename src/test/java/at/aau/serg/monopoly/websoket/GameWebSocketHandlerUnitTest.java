package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GameWebSocketHandlerUnitTest {

    private WebSocketSession session;
    private GameWebSocketHandler gameWebSocketHandler;

    @BeforeEach
    void setUp() {
        gameWebSocketHandler = new GameWebSocketHandler();
        session = mock(WebSocketSession.class);
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
            testSessions[i-1] = mock(WebSocketSession.class);
            when(testSessions[i-1].getId()).thenReturn(String.valueOf(i));
            when(testSessions[i-1].isOpen()).thenReturn(true);
            gameWebSocketHandler.afterConnectionEstablished(testSessions[i-1]);
            sendInit(testSessions[i-1], String.valueOf(i), "Player" + i);
            // Clear invocations only for the first three sessions
            if (i < 4) {
                clearInvocations(testSessions[i-1]);
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
    void testHandleTextMessage() throws IOException{
        clearInvocations(session);
        gameWebSocketHandler.handleTextMessage(session, new TextMessage("Test"));
        verify(session).sendMessage(new TextMessage("Player 1: Test"));
    }

    @Test
    void testAfterConnectionClosed() throws IOException{
        clearInvocations(session);
        gameWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verify(session).sendMessage(argThat(msg -> ((TextMessage)msg).getPayload().startsWith("Player left:")));
    }

    @Test
    void testHandleUpdateMoneyMessage() throws IOException{
        clearInvocations(session);
        gameWebSocketHandler.handleTextMessage(session, new TextMessage("UPDATE_MONEY:500"));
        verify(session, atLeastOnce()).sendMessage(argThat(msg -> ((TextMessage)msg).getPayload().startsWith("GAME_STATE:")));
    }

    @Test
    void testHandleInvalidUpdateMoneyMessage() throws IOException{
        clearInvocations(session);
        gameWebSocketHandler.handleTextMessage(session, new TextMessage("UPDATE_MONEY:notanumber"));
        verify(session, never()).sendMessage(argThat(msg -> ((TextMessage)msg).getPayload().startsWith("GAME_STATE:")));
    }

    @Test
    void testHandleBuyProperty_PlayerNotFound() throws IOException {
        clearInvocations(session);
        WebSocketSession unknown = mock(WebSocketSession.class);
        when(unknown.getId()).thenReturn("unknown");
        when(unknown.isOpen()).thenReturn(true);
        gameWebSocketHandler.handleTextMessage(unknown, new TextMessage("BUY_PROPERTY:1"));
        verify(unknown).sendMessage(argThat(msg -> ((TextMessage)msg).getPayload().contains("Send INIT message first")));
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
    void testPlayerDisconnect() throws Exception {
        GameWebSocketHandler handler = new GameWebSocketHandler();
        when(session.getId()).thenReturn("123");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        // Send INIT message to register the user
        String initJson = "{\"type\":\"INIT\",\"userId\":\"user123\",\"name\":\"TestUser\"}";
        handler.handleTextMessage(session, new TextMessage(initJson));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("Player left")
        ));
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
