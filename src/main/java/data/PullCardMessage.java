package data;

public class PullCardMessage {
    private String type;      // will be "PULL_CARD"
    private String playerId;
    private String cardType;  // "COMMUNITY_CHEST" or "CHANCE"

    public PullCardMessage() { }
    public String getType()       { return type; }
    public void   setType(String t)       { this.type = t; }
    public String getPlayerId()   { return playerId; }
    public void   setPlayerId(String id)  { this.playerId = id; }
    public String getCardType()   { return cardType; }
    public void   setCardType(String c)   { this.cardType = c; }
}

