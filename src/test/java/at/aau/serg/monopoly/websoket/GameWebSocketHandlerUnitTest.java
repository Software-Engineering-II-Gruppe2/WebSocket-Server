package at.aau.serg.monopoly.websoket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import static org.mockito.Mockito.*;


class GameWebSocketHandlerUnitTest {

    private WebSocketSession session;
    private GameWebSocketHandler gameWebSocketHandler;

    @BeforeEach
    void setUp() {
        gameWebSocketHandler= new GameWebSocketHandler();
        session= mock(WebSocketSession.class);
        when(session.getId()).thenReturn("1");
        when(session.isOpen()).thenReturn(true);
    }



    @Test
    void testAfterConnectionEstablished() throws IOException {
        gameWebSocketHandler.afterConnectionEstablished(session);
        verify(session,  times(1)).sendMessage(new TextMessage("Player joined: 1 (Total: 1)"));
    }

    @Test
    void testGameStartTriggeredOnFourConnections() throws Exception {
        // Erstelle vier Mock-Sitzungen
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);
        WebSocketSession session3 = mock(WebSocketSession.class);
        WebSocketSession session4 = mock(WebSocketSession.class);

        // Setze IDs und isOpen()-Status
        when(session1.getId()).thenReturn("1");
        when(session2.getId()).thenReturn("2");
        when(session3.getId()).thenReturn("3");
        when(session4.getId()).thenReturn("4");

        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
        when(session3.isOpen()).thenReturn(true);
        when(session4.isOpen()).thenReturn(true);


        gameWebSocketHandler.afterConnectionEstablished(session1);
        gameWebSocketHandler.afterConnectionEstablished(session2);
        gameWebSocketHandler.afterConnectionEstablished(session3);
        gameWebSocketHandler.afterConnectionEstablished(session4);

    String expectedPayload = "Game started! All 4 players are connected.";


        verify(session1, atLeastOnce()).sendMessage(argThat(msg -> msg.getPayload().equals(expectedPayload)));
        verify(session2, atLeastOnce()).sendMessage(argThat(msg -> msg.getPayload().equals(expectedPayload)));
        verify(session3, atLeastOnce()).sendMessage(argThat(msg -> msg.getPayload().equals(expectedPayload)));
        verify(session4, atLeastOnce()).sendMessage(argThat(msg -> msg.getPayload().equals(expectedPayload)));
    }


    @Test
    void testHandleTextMessage() throws Exception {
        TextMessage textMessage = new TextMessage("Test");
        gameWebSocketHandler.afterConnectionEstablished(session);
        gameWebSocketHandler.handleTextMessage(session,textMessage);
        verify(session, times(1)).sendMessage(new TextMessage("Player 1: Test"));

    }

    @Test
    void testAfterConnectionClosed() throws Exception{
        gameWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verify(session, never()).sendMessage(any());

    }

    @Test
    void testAfterConnectionClosedWithRemainingSessions() throws IOException {

        WebSocketSession session2 = mock(WebSocketSession.class);


        when(session.getId()).thenReturn("1");
        when(session2.getId()).thenReturn("2");


        when(session2.isOpen()).thenReturn(true);


        gameWebSocketHandler.afterConnectionEstablished(session);
        gameWebSocketHandler.afterConnectionEstablished(session2);


        gameWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);


        verify(session, times(1)).sendMessage(new TextMessage("Player joined: 1 (Total: 1)"));
        verify(session2, times(1)).sendMessage(new TextMessage("Player joined: 2 (Total: 2)"));
        verify(session, times(1)).sendMessage(new TextMessage("Player joined: 2 (Total: 2)"));
        verify(session2, times(1)).sendMessage(new TextMessage("Player left: 1 (Total: 1)"));
    }





    @AfterEach
    void tearDown() {
        gameWebSocketHandler=null;
        session= null;
    }
}