
package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.Game;
import model.Player;
import model.cards.Card;
import model.cards.CardType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameWebSocketHandlerDiceTest {

    @Mock
    WebSocketSession session;
    @Mock
    PropertyTransactionService propertyTransactionService;
    @Mock
    PropertyService propertyService;
    @Mock
    RentCollectionService rentCollectionService;
    @Mock
    RentCalculationService rentCalculationService;
    @Mock
    GameHistoryService gameHistoryService;
    @Mock
    CardDeckService cardDeckService;
    @Captor
    ArgumentCaptor<TextMessage> msgCaptor;

    GameWebSocketHandler handler;
    final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new GameWebSocketHandler();
        ReflectionTestUtils.setField(handler, "propertyTransactionService", propertyTransactionService);
        ReflectionTestUtils.setField(handler, "propertyService", propertyService);
        ReflectionTestUtils.setField(handler, "rentCollectionService", rentCollectionService);
        ReflectionTestUtils.setField(handler, "rentCalculationService", rentCalculationService);
        ReflectionTestUtils.setField(handler, "gameHistoryService", gameHistoryService);
        ReflectionTestUtils.setField(handler, "cardDeckService", cardDeckService);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
    }

    private void initPlayer(String userId, String name) throws Exception {
        handler.afterConnectionEstablished(session);
        String initJson = mapper.createObjectNode()
                .put("type", "INIT")
                .put("userId", userId)
                .put("name", name)
                .toString();
        handler.handleTextMessage(session, new TextMessage(initJson));
        clearInvocations(session);
    }

    @Test
    void testRollSendsDiceRollEvent() throws Exception {
        initPlayer("u1", "Alice");
        handler.handleTextMessage(session, new TextMessage("Roll"));

        verify(session, atLeastOnce()).sendMessage(msgCaptor.capture());
        boolean diceRollSent = msgCaptor.getAllValues().stream()
                .map(TextMessage::getPayload)
                .anyMatch(p -> p.contains("\"type\":\"DICE_ROLL\""));
        assertTrue(diceRollSent);
    }

    @Test
    void testManualRollInvalidValueReturnsError() throws Exception {
        initPlayer("u1", "Bob");
        handler.handleTextMessage(session, new TextMessage("MANUAL_ROLL:40"));

        verify(session, atLeastOnce()).sendMessage(msgCaptor.capture());
        assertTrue(msgCaptor.getAllValues().stream()
                .anyMatch(m -> m.getPayload().contains("Invalid roll value")));
    }

    @Test
    void testManualRollInvalidFormatReturnsError() throws Exception {
        initPlayer("u1", "Bob");
        handler.handleTextMessage(session, new TextMessage("MANUAL_ROLL:abc"));

        verify(session, atLeastOnce()).sendMessage(msgCaptor.capture());
        assertTrue(msgCaptor.getAllValues().stream()
                .anyMatch(m -> m.getPayload().contains("Invalid manual roll format")));
    }

    @Test
    void testTaxPaymentBroadcastsAndUpdatesMoney() throws Exception {
        initPlayer("u1", "Alice");

        String taxJson = mapper.createObjectNode()
                .put("type", "TAX_PAYMENT")
                .put("playerId", "u1")
                .put("amount", 200)
                .put("taxType", "EINKOMMENSTEUER")
                .toString();
        handler.handleTextMessage(session, new TextMessage(taxJson));

        verify(session, atLeastOnce()).sendMessage(msgCaptor.capture());
        assertTrue(msgCaptor.getAllValues().stream()
                .anyMatch(msg -> msg.getPayload().contains("TAX_PAYMENT")));
    }

    @Test
    void testPullCardAppliesCardAndSendsToClient() throws Exception {
        initPlayer("u1", "Alice");

        // Erstelle eine echte Karte, die etwas tut
        Card dummyCard = new Card("Test-Karte", CardType.CHANCE) {
            @Override
            public void apply(Game game, String playerId) {
                game.getPlayerById(playerId).ifPresent(p -> p.addMoney(123));
            }
        };
        when(cardDeckService.drawCard(CardType.CHANCE)).thenReturn(dummyCard);

        // Sende eine PULL_CARD Nachricht
        String pullCardJson = mapper.createObjectNode()
                .put("type", "PULL_CARD")
                .put("playerId", "u1")
                .put("cardType", "CHANCE")
                .toString();
        handler.handleTextMessage(session, new TextMessage(pullCardJson));

        // Nachrichten abfangen
        verify(session, atLeastOnce()).sendMessage(msgCaptor.capture());
        List<TextMessage> messages = msgCaptor.getAllValues();

        // Debug-Ausgabe (optional)
        for (TextMessage msg : messages) {
            System.out.println(">>> OUT: " + msg.getPayload());
        }

        boolean found = messages.stream().anyMatch(msg -> {
            try {
                JsonNode node = mapper.readTree(msg.getPayload());
                return node.has("type") && node.get("type").asText().equals("CARD_DRAWN");
            } catch (Exception e) {
                return false;
            }
        });

        assertTrue(found, "Expected a CARD_DRAWN message but none found.");

    }


    @Test
    void testGiveUpEndsGameWhenOnePlayerLeft() throws Exception {
        initPlayer("u1", "Alice");

        Game game = (Game) ReflectionTestUtils.getField(handler, "game");
        game.addPlayer("u1", "Alice");

        String giveUp = mapper.createObjectNode()
                .put("type", "GIVE_UP")
                .put("userId", "u1")
                .toString();
        handler.handleTextMessage(session, new TextMessage(giveUp));

        verify(session, atLeastOnce()).sendMessage(msgCaptor.capture());

        List<String> payloads = msgCaptor.getAllValues().stream()
                .map(TextMessage::getPayload)
                .toList();

        // Debug: Ausgabe aller Nachrichten
        payloads.forEach(p -> System.out.println(">>> OUT: " + p));

        // Test: Es wurde eine GIVE_UP Nachricht gesendet
        assertTrue(payloads.stream().anyMatch(p -> p.contains("\"type\":\"GIVE_UP\"")), "Expected GIVE_UP message");

        // Test: Der Game-State ist leer (Spiel beendet)
        assertTrue(payloads.stream().anyMatch(p -> p.startsWith("GAME_STATE:[]")), "Expected empty GAME_STATE (game over)");
    }

}
