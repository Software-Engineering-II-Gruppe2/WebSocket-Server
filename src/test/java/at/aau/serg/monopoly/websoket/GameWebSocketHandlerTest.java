package at.aau.serg.monopoly.websoket;

import model.Game;
import model.Player;
import model.DiceManagerInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class GameWebSocketHandlerTest {

    private GameWebSocketHandler handler;

    @Mock
    private WebSocketSession session;


    @Mock
    private Game game;

    @Mock
    private PropertyTransactionService propertyTransactionService;

    @Mock
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        handler = new GameWebSocketHandler();
        handler.propertyService = mock(PropertyService.class);
        handler.rentCalculationService = mock(RentCalculationService.class);
        handler.rentCollectionService = mock(RentCollectionService.class);

        ReflectionTestUtils.setField(handler, "game", game);
        ReflectionTestUtils.setField(handler, "propertyTransactionService", propertyTransactionService);

        game.addPlayer("player1", "Player 1");
        when(game.getPlayerById("player1")).thenReturn(Optional.of(player));
        when(game.getCurrentPlayer()).thenReturn(player);

        when(session.getId()).thenReturn("player1");
        when(session.isOpen()).thenReturn(true);

        String initJson = "{\"type\":\"INIT\",\"userId\":\"player1\",\"name\":\"Player1\"}";
        handler.handleTextMessage(session, new TextMessage(initJson));

        when(handler.propertyService.getPropertyByPosition(anyInt())).thenReturn(null);
        clearInvocations(session);
    }

    @Test
    void givenNotPlayerTurn_whenAttemptingToBuyProperty_thenShouldSendNotYourTurnError() throws Exception {
        // Arrange
        int propertyId = 1;
        when(game.getPlayerById("player1")).thenReturn(Optional.of(player));
        when(game.isPlayerTurn("player1")).thenReturn(false);
        when(propertyTransactionService.canBuyProperty(any(), anyInt())).thenReturn(false);

        handler.handleTextMessage(session, new TextMessage("BUY_PROPERTY:1"));

        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("Cannot buy property - it's not your turn")
        ));
    }

    @Test
    void givenPlayerTurnButCannotBuy_whenAttemptingToBuyProperty_thenShouldSendCannotBuyError() throws Exception {
        when(game.isPlayerTurn("player1")).thenReturn(true);
        when(propertyTransactionService.canBuyProperty(any(), anyInt())).thenReturn(false);

        handler.handleTextMessage(session, new TextMessage("BUY_PROPERTY:1"));

        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("Cannot buy property (insufficient funds or already owned)")
        ));
    }

    @Test
    void givenInvalidPropertyIdFormat_whenBuyingProperty_thenShouldSendFormatError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("BUY_PROPERTY:invalid"));

        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("Invalid property ID format")
        ));
    }

    @Test
    void givenExceptionDuringBuyPropertyHandling_thenShouldSendServerError() throws Exception {
        when(game.getPlayerById("player1")).thenThrow(new RuntimeException("unexpected"));

        handler.handleTextMessage(session, new TextMessage("BUY_PROPERTY:1"));

        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("Server error handling buy property request")
        ));
    }

    @Test
    void givenCanBuyTrueButBuyFails_whenBuyingProperty_thenShouldSendPurchaseFailedError() throws Exception {
        when(game.isPlayerTurn("player1")).thenReturn(true);
        when(propertyTransactionService.canBuyProperty(any(), anyInt())).thenReturn(true);
        when(propertyTransactionService.buyProperty(any(), anyInt())).thenReturn(false);

        handler.handleTextMessage(session, new TextMessage("BUY_PROPERTY:1"));

        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("Failed to buy property due to server error")
        ));
    }

    @Test
    void givenValidPurchase_whenBuyingProperty_thenShouldBroadcastSuccess() throws Exception {
        when(game.isPlayerTurn("player1")).thenReturn(true);
        when(propertyTransactionService.canBuyProperty(any(), anyInt())).thenReturn(true);
        when(propertyTransactionService.buyProperty(any(), anyInt())).thenReturn(true);

        Field sessionsField = GameWebSocketHandler.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        CopyOnWriteArrayList<WebSocketSession> sessions =
                (CopyOnWriteArrayList<WebSocketSession>) sessionsField.get(handler);
        sessions.add(session);

        handler.handleTextMessage(session, new TextMessage("BUY_PROPERTY:1"));

        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("bought property")
        ));
    }

    @Test
    void testPlayerInJailCannotRoll() throws Exception {
        when(player.isInJail()).thenReturn(true);
        when(game.isPlayerTurn("player1")).thenReturn(true);

        handler.handleTextMessage(session, new TextMessage("Roll"));

        verify(session).sendMessage(argThat((TextMessage msg) ->
                msg.getPayload().contains("You are in jail and cannot roll")
        ));
    }

    @Test
    @Disabled("Temporarily Disabled due to a bug")
    void testPlayerLandingOnGoToJail() throws Exception {
        when(game.isPlayerTurn("player1")).thenReturn(true);
        doAnswer(inv -> {
            when(player.getPosition()).thenReturn(30);
            return false;
        }).when(game).updatePlayerPosition(anyInt(), any());

        when(player.isInJail()).thenReturn(false);

        handler.handleTextMessage(session, new TextMessage("Roll"));

        verify(game).sendToJail("player1");
        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("goes to jail")
        ));
    }

    @Test
    @Disabled("Temporarily Disabled due to a bug")
    void testJailReleaseAfterThreeTurns() throws Exception {
        when(game.isPlayerTurn("player1")).thenReturn(true);
        when(player.isInJail()).thenReturn(true);
        when(player.getJailTurns()).thenReturn(1);

        doAnswer(inv -> {
            when(player.getJailTurns()).thenReturn(0);
            when(player.isInJail()).thenReturn(false);
            return null;
        }).when(player).reduceJailTurns();

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);

        handler.handleTextMessage(session, new TextMessage("NEXT_TURN"));

        verify(player).reduceJailTurns();
        verify(session, atLeastOnce()).sendMessage(captor.capture());

        boolean released = captor.getAllValues().stream()
                .anyMatch(msg -> msg.getPayload().contains("is released from jail"));

        assertTrue(released);
    }
@Disabled
    @Test
    void testDiceRollTriggersUpdate() throws Exception {
        when(game.isPlayerTurn("player1")).thenReturn(true);
        when(player.isInJail()).thenReturn(false);
        when(player.hasRolledThisTurn()).thenReturn(false);
        when(game.updatePlayerPosition(anyInt(), any())).thenReturn(true);

        handler.handleTextMessage(session, new TextMessage("Roll"));

        verify(game).updatePlayerPosition(anyInt(), eq("player1"));
    }

    @Test
    void testRollWithoutTurnSendsError() throws Exception {
        when(game.isPlayerTurn("player1")).thenReturn(false);

        handler.handleTextMessage(session, new TextMessage("Roll"));

        verify(session).sendMessage(argThat(msg ->
                ((TextMessage) msg).getPayload().contains("Not your turn!")
        ));
    }
}
