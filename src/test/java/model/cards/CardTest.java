package model.cards;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class CardTest {

    @Test
    void lombokGettersAndSetters_work() {
        MoneyCard mc = new MoneyCard();
        mc.setId(42);
        mc.setDescription("You found a treasure");
        mc.setAction(ActionType.GET_MONEY);

        assertEquals(42, mc.getId());
        assertEquals("You found a treasure", mc.getDescription());
        assertSame(ActionType.GET_MONEY, mc.getAction());

        MoveCard mv = new MoveCard();
        mv.setId(7);
        mv.setDescription("Go forward");
        mv.setAction(ActionType.MOVE);
        mv.setSpaces(5);

        assertEquals(7, mv.getId());
        assertTrue(mv.getSpaces() == 5);
        assertEquals("Go forward", mv.getDescription());
        assertSame(ActionType.MOVE, mv.getAction());
    }
}

