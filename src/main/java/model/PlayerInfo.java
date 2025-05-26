package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// fixme is there a reason for having player and playerinfo
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerInfo {
    private String id;
    private String name;
    private int money;
    private int position;
}