package model.cards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import at.aau.serg.monopoly.websoket.CardDeckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;

class CardDeckServiceTest {
    ObjectMapper realMapper = new ObjectMapper();
    ObjectMapper badMapper = mock(ObjectMapper.class);

    CardDeckService realService;
    CardDeckService failService;

    @BeforeEach
    void setUp() {
        realService = new CardDeckService(realMapper);
        failService = new CardDeckService(badMapper);
    }

    @Test
    void init_successLoadsAllDecksAndEmptyDiscards() throws Exception {
        realService.init();

        Field decksF = CardDeckService.class.getDeclaredField("decks");
        Field discF = CardDeckService.class.getDeclaredField("discards");
        decksF.setAccessible(true);
        discF.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<CardType, Deque<Card>> decks =
                (Map<CardType, Deque<Card>>) decksF.get(realService);
        @SuppressWarnings("unchecked")
        Map<CardType, List<Card>> discards =
                (Map<CardType, List<Card>>) discF.get(realService);

        // One Entry per card type:
        for (CardType type : CardType.values()) {
            assertTrue(decks.containsKey(type), "deck missing " + type);
            assertTrue(decks.get(type).size() > 0, "no cards for " + type);
            assertTrue(discards.containsKey(type), "discards missing " + type);
            assertTrue(discards.get(type).isEmpty(), "discards not empty for " + type);
        }
    }

    @Test
    void drawCard_popsAndDiscartsAndReshufflesWhenEmpty() throws Exception {
        realService.init();

        CardType someType = CardType.values()[0];

        Field decksF = CardDeckService.class.getDeclaredField("decks");
        Field discF = CardDeckService.class.getDeclaredField("discards");
        decksF.setAccessible(true);
        discF.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<CardType, Deque<Card>> decks =
                (Map<CardType, Deque<Card>>) decksF.get(realService);
        @SuppressWarnings("unchecked")
        Map<CardType, List<Card>> discards =
                (Map<CardType, List<Card>>) discF.get(realService);

        // test normal draw
        int before = decks.get(someType).size();
        Card first = realService.drawCard(someType);
        assertNotNull(first);
        assertEquals(before - 1, decks.get(someType).size());
        assertEquals(1, discards.get(someType).size());
        assertSame(first, discards.get(someType).get(0));

        // move all remaining cards to discards to force reshuffle
        while (!decks.get(someType).isEmpty()) {
            discards.get(someType).add(decks.get(someType).pop());
        }
        assertTrue(decks.get(someType).isEmpty());
        assertTrue(discards.get(someType).size() > 1);

        // now draw again: should reshuffle discards back into deck
        Card reshuffled = realService.drawCard(someType);
        assertNotNull(reshuffled);
        // after pop from reshuffled deck, exactly (originalDiscardCount - 1) remain in deck
        int remaining = decks.get(someType).size();
        assertEquals(discards.get(someType).size(), 1,
                "after reshuffle+draw, discards should have only the drawn card");
        // make sure the drawn card is now in discards
        assertSame(reshuffled, discards.get(someType).get(0));
    }
}
