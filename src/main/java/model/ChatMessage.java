package model;

import lombok.Data;

@Data
public class ChatMessage {
    private String type= "Chat_Message";
    private String playerId;
    private String message;
}
