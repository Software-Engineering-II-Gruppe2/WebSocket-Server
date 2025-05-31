package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.ObjectMapper;
import data.Deals.DealProposalMessage;
import data.Deals.DealResponseMessage;
import data.Deals.DealResponseType;
import model.Game;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

public class GameWebSocketHandlerDealTest {

    private GameWebSocketHandler handler;
    private DealService dealService;
    private WebSocketSession fromSession;
    private WebSocketSession toSession;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        handler = new GameWebSocketHandler();
        dealService = mock(DealService.class);
        Game game = new Game();
        objectMapper = new ObjectMapper();

        fromSession = mock(WebSocketSession.class);
        toSession = mock(WebSocketSession.class);

        when(fromSession.getId()).thenReturn("session-from");
        when(toSession.getId()).thenReturn("session-to");
        when(fromSession.isOpen()).thenReturn(true);
        when(toSession.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(fromSession);
        handler.afterConnectionEstablished(toSession);


        game.addPlayer("fromPlayer", "Alice");
        game.addPlayer("toPlayer", "Bob");


        ConcurrentHashMap<String, String> sessionMap = new ConcurrentHashMap<>();
        sessionMap.put("session-from", "fromPlayer");
        sessionMap.put("session-to", "toPlayer");

        ReflectionTestUtils.setField(handler, "game", game);
        ReflectionTestUtils.setField(handler, "sessionToUserId", sessionMap);


        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(fromSession);
        sessions.add(toSession);
        ReflectionTestUtils.setField(handler, "sessions", sessions);

        ReflectionTestUtils.setField(handler, "dealService", dealService);
    }

    @Test
    void testDealProposalIsRoutedToTargetPlayer() throws Exception {
        DealProposalMessage proposal = new DealProposalMessage();
        proposal.setType("DEAL_PROPOSAL");
        proposal.setFromPlayerId("fromPlayer");
        proposal.setToPlayerId("toPlayer");
        proposal.setRequestedPropertyIds(List.of(1));
        proposal.setOfferedPropertyIds(List.of());
        proposal.setOfferedMoney(100);

        String json = objectMapper.writeValueAsString(proposal);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        verify(toSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void testDealProposalWithOfferedProperties() throws Exception {
        DealProposalMessage proposal = new DealProposalMessage();
        proposal.setType("DEAL_PROPOSAL");
        proposal.setFromPlayerId("fromPlayer");
        proposal.setToPlayerId("toPlayer");
        proposal.setRequestedPropertyIds(List.of(1));
        proposal.setOfferedPropertyIds(List.of(3, 5));
        proposal.setOfferedMoney(150);

        String json = objectMapper.writeValueAsString(proposal);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        verify(toSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void testDealResponseAcceptCallsDealServiceAndBroadcasts() throws Exception {
        DealResponseMessage response = new DealResponseMessage();
        response.setType("DEAL_RESPONSE");
        response.setFromPlayerId("fromPlayer");
        response.setToPlayerId("toPlayer");
        response.setResponseType(DealResponseType.ACCEPT);
        response.setCounterPropertyIds(List.of(2));
        response.setCounterMoney(200);

        String json = objectMapper.writeValueAsString(response);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        verify(dealService).executeTrade(eq(response));
        verify(toSession, atLeastOnce()).sendMessage(any(TextMessage.class));
    }


    @Test
    void testDealResponseDeclineSkipsDealService() throws Exception {
        DealResponseMessage response = new DealResponseMessage();
        response.setType("DEAL_RESPONSE");
        response.setFromPlayerId("fromPlayer");
        response.setToPlayerId("toPlayer");
        response.setResponseType(DealResponseType.DECLINE);
        response.setCounterPropertyIds(List.of());
        response.setCounterMoney(0);

        String json = objectMapper.writeValueAsString(response);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        verify(dealService, never()).executeTrade(any());
        verify(toSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void testDealResponseFailsIfSessionNotFound() throws Exception {
        DealResponseMessage response = new DealResponseMessage();
        response.setType("DEAL_RESPONSE");
        response.setFromPlayerId("fromPlayer");
        response.setToPlayerId("toPlayer");
        response.setResponseType(DealResponseType.ACCEPT);
        response.setCounterPropertyIds(List.of());
        response.setCounterMoney(0);

        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(fromSession); // Only one session now
        ReflectionTestUtils.setField(handler, "sessions", sessions);

        String json = objectMapper.writeValueAsString(response);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        verify(dealService).executeTrade(any()); // Trade is still processed
        // No exception expected, just a missing session warning in logs
    }
}