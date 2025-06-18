package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.ObjectMapper;
import data.deals.CounterProposalMessage;
import data.deals.DealProposalMessage;
import data.deals.DealResponseMessage;
import data.deals.DealResponseType;
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

class GameWebSocketHandlerDealTest {

    private GameWebSocketHandler handler;
    private DealService dealService;
    private WebSocketSession fromSession;
    private WebSocketSession toSession;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
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

        verify(dealService).executeTrade(response);
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
    @Test
    void testDealProposalIsSavedAndLoggedIfTargetNotFound() throws Exception {
        DealProposalMessage proposal = new DealProposalMessage();
        proposal.setType("DEAL_PROPOSAL");
        proposal.setFromPlayerId("fromPlayer");
        proposal.setToPlayerId("missingPlayer");
        proposal.setRequestedPropertyIds(List.of(1));
        proposal.setOfferedPropertyIds(List.of());
        proposal.setOfferedMoney(100);

        String json = objectMapper.writeValueAsString(proposal);

        // Ensure that the session for "missingPlayer" is not present
        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(fromSession); // Only fromSession is present
        ReflectionTestUtils.setField(handler, "sessions", sessions);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        // Proposal should still be saved even if recipient session is not found
        verify(dealService).saveProposal(any(DealProposalMessage.class));

        // No message should be sent since session is missing
        verify(toSession, never()).sendMessage(any());
    }

    @Test
    void testDealResponseAcceptWithPropertiesBroadcastsMessages() throws Exception {
        DealResponseMessage response = new DealResponseMessage();
        response.setType("DEAL_RESPONSE");
        response.setFromPlayerId("fromPlayer");
        response.setToPlayerId("toPlayer");
        response.setResponseType(DealResponseType.ACCEPT);
        response.setCounterPropertyIds(List.of());
        response.setCounterMoney(200);

        DealProposalMessage proposal = new DealProposalMessage();
        proposal.setFromPlayerId("fromPlayer");
        proposal.setToPlayerId("toPlayer");
        proposal.setOfferedPropertyIds(List.of(3, 5)); // von fromPlayer → toPlayer
        proposal.setRequestedPropertyIds(List.of(2));  // von toPlayer → fromPlayer

        when(dealService.executeTrade(response)).thenReturn(proposal);

        String json = objectMapper.writeValueAsString(response);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        verify(fromSession, atLeastOnce()).sendMessage(argThat(msg -> {
            String payload = ((TextMessage) msg).getPayload();
            return payload.contains("toPlayer bought property 3")
                    || payload.contains("toPlayer bought property 5")
                    || payload.contains("fromPlayer bought property 2");
        }));

        verify(toSession, atLeastOnce()).sendMessage(argThat(msg -> {
            String payload = ((TextMessage) msg).getPayload();
            return payload.contains("toPlayer bought property 3")
                    || payload.contains("toPlayer bought property 5")
                    || payload.contains("fromPlayer bought property 2");
        }));
    }

    @Test
    void testCounterOfferIsSavedAndForwarded() throws Exception {
        CounterProposalMessage counter = new CounterProposalMessage(
                "fromPlayer",
                "toPlayer",
                List.of(1),   // requested by counter-er
                List.of(2),   // offered
                150           // money
        );

        String json = objectMapper.writeValueAsString(counter);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        verify(dealService).saveCounterProposal(any(CounterProposalMessage.class));

        verify(toSession).sendMessage(argThat(message ->
                ((TextMessage) message).getPayload().contains("\"type\":\"COUNTER_OFFER\"")
        ));
    }

    @Test
    void testCounterOfferSavedButNotForwardedIfTargetMissing() throws Exception {
        CounterProposalMessage counter = new CounterProposalMessage(
                "fromPlayer",
                "missingPlayer",
                List.of(),
                List.of(2),
                200
        );

        String json = objectMapper.writeValueAsString(counter);

        // Only fromSession is connected
        CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
        sessions.add(fromSession);
        ReflectionTestUtils.setField(handler, "sessions", sessions);

        handler.handleTextMessage(fromSession, new TextMessage(json));

        verify(dealService).saveCounterProposal(any(CounterProposalMessage.class));
        verify(toSession, never()).sendMessage(any());
    }


}