package data;

import lombok.Data;

// fixme extract superclass for game operations with playerId / userId(?)
// fixme remove type from class hierarchy or use enum if required for message parsing
@Data
public class CheatCodeMessage {
    private String message;
    private String playerId;
    private String type = "CHEAT_MESSAGE";
}
