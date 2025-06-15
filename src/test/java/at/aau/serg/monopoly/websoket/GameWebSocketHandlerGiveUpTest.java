package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.GiveUpMessage;
import data.HasWonMessage;
import model.Game;
import model.Player;
import org.mockito.ArgumentCaptor;
import model.properties.HouseableProperty;
import model.properties.TrainStation;
import model.properties.Utility;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameWebSocketHandlerGiveUpTest {

    @InjectMocks
    private GameWebSocketHandler handler;

    @Mock
    private Game game;
    @Mock
    private WebSocketSession session;
    @Mock
    private Player quittingPlayer;
    @Mock
    private Player remainingPlayer;
    @Mock
    private PropertyService propertyService;
    @Mock
    private GameHistoryService gameHistoryService;
    @Mock
    private CardDeckService cardDeckService;
    @Mock
    private PropertyTransactionService propertyTransactionService;
    @Mock
    private RentCollectionService rentCollectionService;
    @Mock
    private RentCalculationService rentCalculationService;
    @Mock
    private CheatService cheatService;
    @Mock
    private DealService dealService;
    @Captor
    private ArgumentCaptor<TextMessage> messageCaptor;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(handler, "game", game);
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper()); // <- wichtig!

        handler.sessions.clear();
        handler.sessions.add(session);

        when(session.getId()).thenReturn("session1");
        when(session.isOpen()).thenReturn(true);

        handler.sessionToUserId.clear();
        handler.sessionToUserId.put("session1", "session1");

        // NEU: BotManager initialisieren
        handler.initializeBotManager();

        when(propertyService.getHouseableProperties()).thenReturn(Collections.emptyList());
        when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        when(propertyService.getUtilities()).thenReturn(Collections.emptyList());
        when(quittingPlayer.getMoney()).thenReturn(1000);
        when(remainingPlayer.getMoney()).thenReturn(1000);
        when(quittingPlayer.getId()).thenReturn("session1");
        when(remainingPlayer.getId()).thenReturn("remainingId");
    }

    @Test
    void giveUp_notYourTurn_sendsError() throws Exception {
        // Arrange
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(false);

        // Act
        handler.handleGiveUpFromClient(session, json);

        // Assert: error sent back to same session
        verify(session).sendMessage(messageCaptor.capture());
        String payload = messageCaptor.getValue().getPayload();
        assertTrue(payload.contains("\"type\":\"ERROR\""));
        assertTrue(payload.contains("You can only give up on your turn"));
        // no game.giveUp
        verify(game, never()).giveUp(any());
    }

    @Test
    void giveUp_multiplePlayers_broadcastsGiveUpAndGameState() throws Exception {
        // Arrange: it's your turn
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(true);

        List<Player> playersAfterGiveUp = Arrays.asList(quittingPlayer, remainingPlayer);
        when(game.getPlayers()).thenReturn(playersAfterGiveUp);
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
        // Capture alle Nachrichten an irgendeine Session
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());

        List<String> payloads = captor.getAllValues().stream().map(TextMessage::getPayload).toList();
        // 1st message = GIVE_UP
        GiveUpMessage gu = mapper.readValue(sent.get(0).getPayload(), GiveUpMessage.class);
        assertEquals("session1", gu.getUserId());

        // 2nd message = GAME_STATE:
        assertTrue(sent.get(1).getPayload().startsWith("GAME_STATE:"));
        // Prüfe, ob die gewünschten Typen enthalten sind
        assertTrue(payloads.stream().anyMatch(p -> p.contains("\"type\":\"GIVE_UP\"")));
        assertTrue(payloads.stream().anyMatch(p -> p.startsWith("GAME_STATE:")));
        assertTrue(payloads.stream().anyMatch(p -> p.startsWith("PLAYER_TURN:")));



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
        when(game.getPlayers()).thenReturn(Collections.singletonList(remainingPlayer));
        when(remainingPlayer.getId()).thenReturn("winner1");

        // Act
        handler.handleGiveUpFromClient(session, json);

        // Assert: game.giveUp called
        verify(game).giveUp("session1");

        // We now expect exactly 4 sendMessage calls:
        // GIVE_UP
        // HAS_WON
        // PROPERTY_BOUGHT JSON
        // CLEAR_CHAT JSON
        verify(session, times(4)).sendMessage(messageCaptor.capture());
        List<TextMessage> sent = messageCaptor.getAllValues();
        assertEquals(4, sent.size());

        // GIVE_UP
        String giveUpPayload = sent.get(0).getPayload();
        GiveUpMessage parsedGiveUp = mapper.readValue(giveUpPayload, GiveUpMessage.class);
        assertEquals("session1", parsedGiveUp.getUserId());

        // HAS_WON
        String hasWonPayload = sent.get(1).getPayload();
        HasWonMessage parsedHasWon = mapper.readValue(hasWonPayload, HasWonMessage.class);
        assertEquals("winner1", parsedHasWon.getUserId());

        // PROPERTY_BOUGHT
        String endGamePayload = sent.get(2).getPayload();
        assertTrue(endGamePayload.contains("\"type\":\"PROPERTY_BOUGHT\""));
        assertTrue(endGamePayload.contains("Das Spiel wurde beendet. Der Gewinner ist"));

        // CLEAR_CHAT
        String clearChatPayload = sent.get(3).getPayload();
        assertTrue(clearChatPayload.contains("\"type\":\"CLEAR_CHAT\""));
    }
@Disabled
    @Test
    void giveUp_serializationError_sendsOnlyEndGameAnnouncement() throws Exception {
        // Arrange
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(true);
        when(game.getPlayers()).thenReturn(Collections.singletonList(remainingPlayer));
        when(remainingPlayer.getId()).thenReturn("winner1");

        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(handler, "objectMapper", mockMapper);
        when(mockMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialize-fail") {});

        // Act
        handler.handleGiveUpFromClient(session, json);

        // Assert: game.giveUp(...) was called
        verify(game).giveUp("session1");

        // Only one sendMessage should occur (PROPERTY_BOUGHT):
        verify(session, times(1)).sendMessage(messageCaptor.capture());
        String payload = messageCaptor.getValue().getPayload();

        assertTrue(payload.contains("\"type\":\"PROPERTY_BOUGHT\""),
                "Expected a PROPERTY_BOUGHT message");
        assertTrue(payload.contains("Das Spiel wurde beendet. Der Gewinner ist"),
                "Expected the winner announcement in the message");
    }

}





