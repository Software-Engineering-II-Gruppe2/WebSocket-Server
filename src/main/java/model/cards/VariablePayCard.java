package model.cards;

// TODO: Add a card that needs this class - currently not in the stack

import lombok.Data;
import model.Game;
import model.Player;

import java.util.List;

@Data
public class VariablePayCard extends Card {

    @Override
    public void apply(Game game, String playerId) {

    }
}