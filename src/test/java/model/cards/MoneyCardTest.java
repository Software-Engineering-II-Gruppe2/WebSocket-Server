package model.cards;

import static org.mockito.Mockito.*;
import java.util.Arrays;
import model.Game;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class MoneyCardTest {

    Game game;
    Player p1, p2, p3;

    @BeforeEach
    void setUp() {
        game = mock(Game.class);
        p1 = mock(Player.class);
        p2 = mock(Player.class);
        p3 = mock(Player.class);

        // lenient stubbing to prevent UnnecessaryStubbingException
        lenient().when(p1.getId()).thenReturn("p1");
        lenient().when(p2.getId()).thenReturn("p2");
        lenient().when(p3.getId()).thenReturn("p3");
        lenient().when(game.getPlayers()).thenReturn(Arrays.asList(p1, p2, p3));
    }

    @Test
    void othersPay_transfersFromAllOthersToMe() {
        MoneyCard card = new MoneyCard();
        card.setAmount(10);
        card.setOthersPay(true);
        card.setOthersGet(false);

        card.apply(game, "p1");

        // first, the service must fetch all players
        verify(game).getPlayers();

        // then each other pays
        verify(game).updatePlayerMoney("p2", -10);
        verify(game).updatePlayerMoney("p3", -10);

        // and p1 receives twice
        verify(game, times(2)).updatePlayerMoney("p1", 10);

        verifyNoMoreInteractions(game);
    }

    @Test
    void othersGet_transfersFromMeToAllOthers() {
        MoneyCard card = new MoneyCard();
        card.setAmount(5);
        card.setOthersPay(false);
        card.setOthersGet(true);

        card.apply(game, "p1");

        verify(game).getPlayers();
        verify(game).updatePlayerMoney("p2", 5);
        verify(game).updatePlayerMoney("p3", 5);
        verify(game, times(2)).updatePlayerMoney("p1", -5);

        verifyNoMoreInteractions(game);
    }

    @Test
    void getMoney_action_GET_MONEY_givesMeMoneyOnce() {
        MoneyCard card = new MoneyCard();
        card.setAmount(100);
        card.setOthersPay(false);
        card.setOthersGet(false);
        card.setAction(ActionType.GET_MONEY);

        card.apply(game, "p1");

        verify(game).getPlayers();
        verify(game).updatePlayerMoney("p1", 100);

        verifyNoMoreInteractions(game);
    }

    @Test
    void pay_action_PAY_chargesMeOnce() {
        MoneyCard card = new MoneyCard();
        card.setAmount(7);
        card.setOthersPay(false);
        card.setOthersGet(false);
        card.setAction(ActionType.PAY);

        card.apply(game, "p1");

        verify(game).getPlayers();
        verify(game).updatePlayerMoney("p1", -7);

        verifyNoMoreInteractions(game);
    }
}
