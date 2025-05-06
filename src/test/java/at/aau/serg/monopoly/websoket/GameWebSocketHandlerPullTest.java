package at.aau.serg.monopoly.websoket;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.PullCardMessage;
import model.cards.CardType;
import model.cards.MoneyCard;
import model.cards.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class GameWebSocketHandlerPullTest {

    @Mock WebSocketSession session;
    @Mock CardDeckService cardDeckService;
    @Mock PropertyTransactionService propertyTransactionService;
    @Mock GameHistoryService gameHistoryService;
    @Captor ArgumentCaptor<TextMessage> msgCaptor;

    ObjectMapper mapper = new ObjectMapper();
    GameWebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = spy(new GameWebSocketHandler());

        // Inject:
        ReflectionTestUtils.setField(handler, "cardDeckService", cardDeckService);
        ReflectionTestUtils.setField(handler, "propertyTransactionService", propertyTransactionService);
        ReflectionTestUtils.setField(handler, "gameHistoryService", gameHistoryService);

        when(session.getId()).thenReturn("sess-1");
        when(session.isOpen()).thenReturn(true);

        // INIT handshake
        handler.afterConnectionEstablished(session);
        String initJson = mapper.createObjectNode()
                .put("type", "INIT")
                .put("userId", "u1")
                .put("name", "Alice")
                .toString();
        handler.handleTextMessage(session, new TextMessage(initJson));

        // Clear INIT messages
        clearInvocations(session);

        doNothing().when(handler).broadcastGameState();
    }

    @Test
    void testPullCard_appliesAndRepliesForChanceDeck() throws Exception {
        MoneyCard realCard = new MoneyCard();
        realCard.setId(99);
        realCard.setDescription("Advance to Go");
        realCard.setAction(ActionType.MOVE);
        realCard.setAmount(0);
        MoneyCard spyCard = spy(realCard);

        when(cardDeckService.drawCard(CardType.CHANCE)).thenReturn(spyCard);

        PullCardMessage pull = new PullCardMessage();
        pull.setType("PULL_CARD");
        pull.setPlayerId("u1");
        pull.setCardType("CHANCE");

        String payload = mapper.writeValueAsString(pull);
        handler.handleTextMessage(session, new TextMessage(payload));

        verify(cardDeckService).drawCard(CardType.CHANCE);

        verify(spyCard).apply(any(), eq("u1"));

        verify(session).sendMessage(msgCaptor.capture());
        String jsonReply = msgCaptor.getValue().getPayload();

        assertTrue(jsonReply.contains("\"playerId\":\"u1\""),       "must include playerId");
        assertTrue(jsonReply.contains("\"cardType\":\"CHANCE\""),    "must include cardType");
        assertTrue(jsonReply.contains("\"id\":99"),                  "must include card.id");
        assertTrue(jsonReply.contains("\"description\":\"Advance to Go\""),
                "must include card.description");
    }
}
