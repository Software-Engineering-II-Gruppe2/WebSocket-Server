package at.aau.serg.monopoly.websoket;

import data.Deals.DealResponseMessage;
import data.Deals.DealResponseType;
import model.Game;
import model.Player;
import model.properties.BaseProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class DealServiceTest {

    private DealService dealService;
    private PropertyTransactionService propertyTransactionService;
    private Game game;
    private Player fromPlayer;
    private Player toPlayer;
    private BaseProperty property;

    @BeforeEach
    void setUp() {
        propertyTransactionService = mock(PropertyTransactionService.class);
        dealService = new DealService(propertyTransactionService);

        game = mock(Game.class);
        fromPlayer = mock(Player.class);
        toPlayer = mock(Player.class);
        property = mock(BaseProperty.class);

        dealService.setGame(game);
    }

    @Test
    void testExecuteTrade_gameNotSet_doesNothing() {
        DealService localService = new DealService(propertyTransactionService);
        DealResponseMessage msg = new DealResponseMessage("DEAL_RESPONSE", "from", "to", DealResponseType.ACCEPT, List.of(1), 100);
        localService.executeTrade(msg);
        // No exception = pass
    }

    @Test
    void testExecuteTrade_playersNotFound_doesNothing() {
        when(game.getPlayerById("from")).thenReturn(Optional.empty());
        when(game.getPlayerById("to")).thenReturn(Optional.of(toPlayer));

        DealResponseMessage msg = new DealResponseMessage("DEAL_RESPONSE", "from", "to", DealResponseType.ACCEPT, List.of(1), 100);
        dealService.executeTrade(msg);
        // No exception = pass
    }

    @Test
    void testExecuteTrade_propertyTransferred_andMoneyExchanged() {
        when(game.getPlayerById("from")).thenReturn(Optional.of(fromPlayer));
        when(game.getPlayerById("to")).thenReturn(Optional.of(toPlayer));
        when(propertyTransactionService.findPropertyById(1)).thenReturn(property);
        when(fromPlayer.getId()).thenReturn("from");

        DealResponseMessage msg = new DealResponseMessage();
        msg.setType("DEAL_RESPONSE");
        msg.setFromPlayerId("from");
        msg.setToPlayerId("to");
        msg.setResponseType(DealResponseType.ACCEPT);
        msg.setCounterPropertyIds(List.of(1));
        msg.setCounterMoney(100);

        dealService.executeTrade(msg);

        verify(property).setOwnerId("from");
        verify(fromPlayer).subtractMoney(100);
        verify(toPlayer).addMoney(100);
    }

    @Test
    void testExecuteTrade_noMoneyTransferred_ifZero() {
        when(game.getPlayerById("from")).thenReturn(Optional.of(fromPlayer));
        when(game.getPlayerById("to")).thenReturn(Optional.of(toPlayer));
        when(propertyTransactionService.findPropertyById(1)).thenReturn(property);
        when(fromPlayer.getId()).thenReturn("from");

        DealResponseMessage msg = new DealResponseMessage();
        msg.setType("DEAL_RESPONSE");
        msg.setFromPlayerId("from");
        msg.setToPlayerId("to");
        msg.setResponseType(DealResponseType.ACCEPT);
        msg.setCounterPropertyIds(List.of(1));
        msg.setCounterMoney(0);

        dealService.executeTrade(msg);

        verify(property).setOwnerId("from");
        verify(fromPlayer, never()).subtractMoney(anyInt());
        verify(toPlayer, never()).addMoney(anyInt());
    }

    @Test
    void testExecuteTrade_ignoresPropertyIfNotFound() {
        when(game.getPlayerById("from")).thenReturn(Optional.of(fromPlayer));
        when(game.getPlayerById("to")).thenReturn(Optional.of(toPlayer));
        when(propertyTransactionService.findPropertyById(1)).thenReturn(null);

        DealResponseMessage msg = new DealResponseMessage("DEAL_RESPONSE", "from", "to", DealResponseType.ACCEPT, List.of(1), 0);

        dealService.executeTrade(msg);
        // Should skip property, but still work
    }
}
