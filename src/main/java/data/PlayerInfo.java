package data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerInfo {
    private String id;
    private String name;
    private int money;
    private int position;
    private boolean inJail;
    private int jailTurns;
    private boolean bot;
}