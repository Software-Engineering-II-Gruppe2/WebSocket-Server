package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import data.GiveUpMessage;
import data.HasWonMessage;
import model.BotManager;
import model.Game;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
        import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

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
    @Captor private ArgumentCaptor<TextMessage> messageCaptor;

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
    }

    @Test
    void giveUp_notYourTurn_sendsError() throws Exception {
        // Arrange
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(false);

        // Act
        handler.handleGiveUp(session, json);

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
        // Arrange
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(true);
        when(game.getPlayers()).thenReturn(Arrays.asList(quittingPlayer, remainingPlayer));
        when(game.getPlayerInfo()).thenReturn(Collections.emptyList());
        when(game.getCurrentPlayer()).thenReturn(remainingPlayer);
        when(remainingPlayer.getId()).thenReturn("remainingId");

        // BotManager mocken, damit kein echter Bot-Zug passiert
        BotManager mockBotManager = mock(BotManager.class);
        ReflectionTestUtils.setField(handler, "botManager", mockBotManager);

        // Act
        handler.handleGiveUp(session, json);

        // Assert
        verify(game).giveUp("session1");

        // Capture alle Nachrichten an irgendeine Session
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());

        List<String> payloads = captor.getAllValues().stream().map(TextMessage::getPayload).toList();

        // Prüfe, ob die gewünschten Typen enthalten sind
        assertTrue(payloads.stream().anyMatch(p -> p.contains("\"type\":\"GIVE_UP\"")));
        assertTrue(payloads.stream().anyMatch(p -> p.startsWith("GAME_STATE:")));
        assertTrue(payloads.stream().anyMatch(p -> p.startsWith("PLAYER_TURN:")));
    }



    @Test
    void giveUp_lastPlayer_sendsHasWonAndEndGame() throws Exception {
        // Arrange: it's your turn
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(true);
        // after removal only one player left
        when(game.getPlayers()).thenReturn(Collections.singletonList(remainingPlayer));
        when(remainingPlayer.getId()).thenReturn("winner1");

        // Act
        handler.handleGiveUp(session, json);

        // Assert: game.giveUp called
        verify(game).giveUp("session1");

        // We expect exactly 2 sendMessage calls: HAS_WON and CLEAR_CHAT
        verify(session, times(2)).sendMessage(messageCaptor.capture());

        List<TextMessage> sent = messageCaptor.getAllValues();
        assertEquals(2, sent.size());

        // 1) The first must be the HasWonMessage
        String hasWonPayload = sent.get(0).getPayload();
        HasWonMessage parsed = mapper.readValue(hasWonPayload, HasWonMessage.class);
        assertEquals("winner1", parsed.getUserId());

        // 2) The second can be the clear-chat JSON (we just assert it has type CLEAR_CHAT)
        String clearChat = sent.get(1).getPayload();
        assertTrue(clearChat.contains("\"type\":\"CLEAR_CHAT\""));
    }

    @Test
    void giveUp_serializationError_sendsError() throws Exception {
        // Arrange
        JsonNode json = mapper.readTree("{\"userId\":\"session1\"}");
        when(game.isPlayerTurn("session1")).thenReturn(true);
        when(game.getPlayers())
                .thenReturn(Arrays.asList(quittingPlayer, remainingPlayer));
        when(game.getPlayerInfo()).thenReturn(Collections.emptyList());
        when(game.getCurrentPlayer()).thenReturn(remainingPlayer);
        when(remainingPlayer.getId()).thenReturn("remainingId");
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(handler, "objectMapper", mockMapper);
        when(mockMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialize-fail") {});

        // Act
        handler.handleGiveUp(session, json);

        // Assert
        // 1) game.giveUp must still have been called
        verify(game).giveUp("session1");

        // 2) Since serialization failed, we should send exactly one ERROR to the session
        verify(session, times(1)).sendMessage(messageCaptor.capture());
        String errorPayload = messageCaptor.getValue().getPayload();
        assertTrue(errorPayload.contains("\"type\":\"ERROR\""),
                "Expected an ERROR message");
        assertTrue(errorPayload.contains("Server error processing give up"),
                "Expected our give-up error text");
    }


}
