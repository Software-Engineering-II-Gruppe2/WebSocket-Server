package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.GiveUpMessage;
import data.HasWonMessage;
import model.Game;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameWebSocketHandlerGiveUpTest {

    @InjectMocks
    private GameWebSocketHandler handler;

    @Mock private Game game;
    @Mock private WebSocketSession session;
    @Mock private Player quittingPlayer;
    @Mock private Player remainingPlayer;
    @Mock private PropertyService propertyService;
    @Mock private GameHistoryService gameHistoryService;
    @Mock private CardDeckService cardDeckService;
    @Mock private PropertyTransactionService propertyTransactionService;
    @Mock private RentCollectionService rentCollectionService;
    @Mock private RentCalculationService rentCalculationService;
    @Mock private CheatService cheatService;
    @Mock private DealService dealService;

    @Captor
    private ArgumentCaptor<TextMessage> messageCaptor;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // inject our mocks
        ReflectionTestUtils.setField(handler, "game", game);
        handler.sessions.clear();
        handler.sessions.add(session);

        when(session.getId()).thenReturn("session1");
        when(session.isOpen()).thenReturn(true);

        handler.sessionToUserId.clear();
        handler.sessionToUserId.put("session1", "session1");

        when(propertyService.getHouseableProperties()).thenReturn(Collections.emptyList());
        when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        when(propertyService.getUtilities()).thenReturn(Collections.emptyList());
        when(quittingPlayer.getMoney()).thenReturn(1000);
        when(remainingPlayer.getMoney()).thenReturn(1000);
        when(quittingPlayer.getId()).thenReturn("session1");
        when(remainingPlayer.getId()).thenReturn("remainingId");
    }

    @Test
    void testGivingUpWhenNotYourTurnSendsAnError() throws Exception {
        // Arrange
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(false);

        // Act
        handler.handleGiveUpFromClient(session, json);

        // Assert: error sent back to same session
        verify(session, times(1)).sendMessage(messageCaptor.capture());
        String payload = messageCaptor.getValue().getPayload();
        assertTrue(payload.contains("\"type\":\"ERROR\""));
        assertTrue(payload.contains("You can only give up on your turn"));
        // no game.giveUp
        verify(game, never()).giveUp(any());
    }

    @Test
    void testGivingUpWithMultiplePlayersBroadcastsGiveUpAndGameState() throws Exception {
        // Arrange: it's your turn
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(true);

        List<Player> twoPlayers = Arrays.asList(quittingPlayer, remainingPlayer);
        when(game.getPlayers()).thenReturn(twoPlayers);
        when(game.getPlayerInfo()).thenReturn(Collections.emptyList());
        when(game.getCurrentPlayer()).thenReturn(remainingPlayer);
        when(remainingPlayer.getId()).thenReturn("remainingId");

        // Act
        handler.handleGiveUpFromClient(session, json);

        // Assert: game.giveUp called
        verify(game).giveUp("session1");

        // We expect exactly 3 sendMessage calls:
        // GIVE_UP JSON
        // "GAME_STATE:..."
        // "PLAYER_TURN:remainingId"
        verify(session, times(3)).sendMessage(messageCaptor.capture());
        List<TextMessage> sent = messageCaptor.getAllValues();
        assertEquals(3, sent.size());

        // 1st message = GIVE_UP
        GiveUpMessage gu = mapper.readValue(sent.get(0).getPayload(), GiveUpMessage.class);
        assertEquals("session1", gu.getUserId());

        // 2nd message = GAME_STATE:
        assertTrue(sent.get(1).getPayload().startsWith("GAME_STATE:"));

        // 3rd message = PLAYER_TURN:
        assertTrue(sent.get(2).getPayload().startsWith("PLAYER_TURN:remainingId"));
    }

    @Disabled("Currently disabled due to a bug")
    @Test
    void testWhenLastPlayerGivesUpBroadcastFullEndGameFlow() throws Exception {
        // Arrange: it's your turn
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(true);
        // after removal only one player left
        List<Player> winnerList = new ArrayList<>();
        winnerList.add(remainingPlayer);
        when(game.getPlayers()).thenReturn(winnerList);
        when(remainingPlayer.getId()).thenReturn("winner1");

        // Act
        handler.handleGiveUpFromClient(session, json);

        // Assert: game.giveUp called
        verify(game).giveUp("session1");

        // Jetzt erwarten wir 5 Nachrichten:
        // 1: GIVE_UP
        // 2: HAS_WON
        // 3: PROPERTY_BOUGHT (Spielende)
        // 4: CLEAR_CHAT
        // 5: RESET
        verify(session, times(5)).sendMessage(messageCaptor.capture());
        List<String> payloads = new ArrayList<>();
        for (TextMessage tm : messageCaptor.getAllValues()) {
            payloads.add(tm.getPayload());
        }

        // GIVE_UP
        GiveUpMessage gu = mapper.readValue(payloads.get(0), GiveUpMessage.class);
        assertEquals("session1", gu.getUserId());

        // HAS_WON
        HasWonMessage hw = mapper.readValue(payloads.get(1), HasWonMessage.class);
        assertEquals("winner1", hw.getUserId());

        // PROPERTY_BOUGHT (Spielende)
        assertTrue(payloads.get(2).contains("\"type\":\"PROPERTY_BOUGHT\""));
        assertTrue(payloads.get(2).contains("Der Gewinner ist"));

        // CLEAR_CHAT
        assertTrue(payloads.get(3).contains("\"type\":\"CLEAR_CHAT\""));

        // RESET
        assertTrue(payloads.get(4).contains("\"type\":\"RESET\""));
    }

    @Disabled("Currently disabled due to a bug")
    @Test
    void testSerializationErrorWhenGivingUpOnlySendsEndGame() throws Exception {
        // Arrange
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(true);
        when(game.getPlayers()).thenReturn(Collections.singletonList(remainingPlayer));
        when(remainingPlayer.getId()).thenReturn("winner1");

        ObjectMapper badMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(handler, "objectMapper", badMapper);
        when(badMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("fail") {});

        // Act
        handler.handleGiveUpFromClient(session, json);

        // Assert: game.giveUp(...) was called
        verify(game).giveUp("session1");

        // Wir erwarten 3 Nachrichten:
        // 1: PROPERTY_BOUGHT (Spielende)
        // 2: CLEAR_CHAT
        // 3: RESET
        verify(session, times(3)).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();

        // PROPERTY_BOUGHT
        assertTrue(messages.get(0).getPayload().contains("\"type\":\"PROPERTY_BOUGHT\""));

        // CLEAR_CHAT
        assertTrue(messages.get(1).getPayload().contains("\"type\":\"CLEAR_CHAT\""));

        // RESET
        assertTrue(messages.get(2).getPayload().contains("\"type\":\"RESET\""));
    }
}
