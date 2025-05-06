package model.cards;

import lombok.Data;
import model.Game;
import model.Player;

import java.util.Optional;

@Data
public class MoveCard extends Card {
    private Integer field;   // move to a specific field
    private Integer spaces;  // move by some amount

    @Override
    public void apply(Game game, String playerId) {
        Optional<Player> optPlayer = game.getPlayerById(playerId);
        if (!optPlayer.isPresent()) {
            throw new IllegalArgumentException("No player with id: " + playerId);
        }
        Player player = optPlayer.get();
        int oldPos = player.getPosition();

        if (spaces != null) {
            game.updatePlayerPosition(spaces, playerId);
        } else if (field != null) {
            if (field == 30) {
                player.setPosition(30);
            } else {
                if (field < oldPos) {
                    game.updatePlayerMoney(playerId, 200);
                }
                player.setPosition(field);
            }
        }
    }

}
