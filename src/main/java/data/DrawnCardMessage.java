package data;

import model.cards.Card;
import lombok.Data;

@Data
public class DrawnCardMessage {
    private String type;       // = "CARD_DRAWN"
    private String playerId;
    private String cardType;   // "CHANCE" or "COMMUNITY_CHEST"
    private Card card;

    public DrawnCardMessage(String playerId, String cardType, Card card) {
        this.type = "CARD_DRAWN";
        this.playerId = playerId;
        this.cardType = cardType;
        this.card = card;
    }
}
