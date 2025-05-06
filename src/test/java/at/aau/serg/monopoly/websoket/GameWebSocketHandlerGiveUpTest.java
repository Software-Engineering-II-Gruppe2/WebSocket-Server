package at.aau.serg.monopoly.websoket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameWebSocketHandlerGiveUpTest {

    @Mock
    WebSocketSession session;

    @Captor
    ArgumentCaptor<TextMessage> msgCaptor;

    GameWebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new GameWebSocketHandler();

        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        // send INIT
        String initJson = """
            {
              "type": "INIT",
              "userId": "u1",
              "name": "Alice"
            }
            """;
        handler.handleTextMessage(session, new TextMessage(initJson));
        clearInvocations(session); // Log reset
    }

    @Test
    void testGiveUpRemovesPlayerAndSendsMessage() throws Exception {
        handler.handleTextMessage(session, new TextMessage("GIVE_UP"));

        verify(session, atLeastOnce()).sendMessage(msgCaptor.capture());
        List<TextMessage> messages = msgCaptor.getAllValues();

        boolean systemMessageFound = messages.stream()
                .anyMatch(m -> m.getPayload().contains("SYSTEM: Player u1 gave up"));

        assertTrue(systemMessageFound, "Expected system give-up message was not found");
    }

    @Test
    void testGiveUpWithoutInitReturnsError() throws Exception {
        WebSocketSession newSession = mock(WebSocketSession.class);
        when(newSession.getId()).thenReturn("unregistered");
        when(newSession.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(newSession);
        clearInvocations(newSession);

        handler.handleTextMessage(newSession, new TextMessage("GIVE_UP"));

        ArgumentCaptor<TextMessage> errorCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(newSession).sendMessage(errorCaptor.capture());
        String payload = errorCaptor.getValue().getPayload();

        assertTrue(payload.contains("ERROR"), "Expected error when GIVE_UP sent before INIT");
    }
}
