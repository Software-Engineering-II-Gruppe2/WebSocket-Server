package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import model.cards.Card;
import model.cards.CardType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class CardDeckService {
    private final ObjectMapper mapper;
    private final Map<CardType, Deque<Card>> decks = new EnumMap<>(CardType.class);
    private final Map<CardType, List<Card>> discards = new EnumMap<>(CardType.class);

    public CardDeckService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // Construct the card decks, load & shuffle them
    @PostConstruct
    public void init() {
        try {
            // ReadJSON file in a Map
            var resource = new ClassPathResource("ChanceAndChestCards.json");
            TypeReference<Map<String, List<Card>>> typeRef = new TypeReference<>() {};
            Map<String, List<Card>> raw =
                    mapper.readValue(resource.getInputStream(), typeRef);

            // For each entry: convert, shuffle and wrap in Deque
            for (var entry : raw.entrySet()) {
                CardType type = CardType.valueOf(entry.getKey());
                List<Card> list = new ArrayList<>(entry.getValue());
                Collections.shuffle(list);
                decks.put(type, new ArrayDeque<>(list));
                discards.put(type, new ArrayList<>());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load ChanceAndChestCards.json", e);
        }
    }

    // Method to draw the next card:
    public synchronized Card drawCard(CardType type) {
        Deque<Card> deck = decks.get(type);
        if (deck.isEmpty()) {
            // move discards back into deck
            List<Card> pile = discards.get(type);
            Collections.shuffle(pile);
            deck.addAll(pile);
            pile.clear();
        }
        Card drawn = deck.pop();
        discards.get(type).add(drawn);
        return drawn;
    }
}

