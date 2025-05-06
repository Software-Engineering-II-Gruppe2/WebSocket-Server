package model.cards;

import static org.mockito.Mockito.*;
import java.util.Optional;
import model.Game;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class MoveCardTest {

    Game game;
    Player player;

    @BeforeEach
    void setUp() {
        game = mock(Game.class);
        player = mock(Player.class);

        when(game.getPlayerById("p1")).thenReturn(Optional.of(player));
    }

    @Test
    void moveBySpaces_callsUpdatePlayerPosition() {
        MoveCard card = new MoveCard();
        card.setSpaces(3);
        card.setField(null);

        card.apply(game, "p1");

        verify(game).updatePlayerPosition(3, "p1");
        verifyNoMoreInteractions(game);
    }

    @Test
    void moveToField_lessThanOldPos_movesAndGives200() {
        MoveCard card = new MoveCard();
        card.setSpaces(null);
        card.setField(2);

        when(player.getPosition()).thenReturn(5);

        card.apply(game, "p1");

        verify(game).updatePlayerMoney("p1", 200);
        verify(player).setPosition(2);
        verifyNoMoreInteractions(game);
    }

    @Test
    void moveToField_greaterThanOldPos_movesWithoutMoney() {
        MoveCard card = new MoveCard();
        card.setSpaces(null);
        card.setField(8);

        when(player.getPosition()).thenReturn(3);

        card.apply(game, "p1");

        verify(player).setPosition(8);
        verify(game, never()).updatePlayerMoney(anyString(), anyInt());
    }

    @Test
    void moveToField_jailField_setsPositionOnly() {
        MoveCard card = new MoveCard();
        card.setSpaces(null);
        card.setField(30);

        when(player.getPosition()).thenReturn(12);

        card.apply(game, "p1");

        verify(player).setPosition(30);
        verify(game, never()).updatePlayerMoney(anyString(), anyInt());
    }

    @Test
    void noSpacesNoField_doesNothing() {
        MoveCard card = new MoveCard();
        card.setSpaces(null);
        card.setField(null);

        when(player.getPosition()).thenReturn(10);

        card.apply(game, "p1");

        // Only getPosition() was called on the player
        verify(player).getPosition();
        verifyNoMoreInteractions(game, player);
    }
}
