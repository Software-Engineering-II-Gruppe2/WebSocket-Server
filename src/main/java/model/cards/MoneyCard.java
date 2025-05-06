package model.cards;

import lombok.Data;
import model.Game;
import model.Player;

import java.util.List;

@Data
public class MoneyCard extends Card {
    private int amount;
    private boolean othersPay;   // you get money from all other players if true
    private boolean othersGet;   // you gift money to all other players if true

    @Override
    public void apply(Game game, String playerId) {
        List<Player> all = game.getPlayers();

        // Each player pays some amount to me
        if (othersPay) {
            for (Player p : all) {
                if (!p.getId().equals(playerId)) {
                    game.updatePlayerMoney(p.getId(), -amount);
                    game.updatePlayerMoney(playerId, amount);
                }
            }

        } else if (othersGet) { // I have to pay some amount to all other players
            for (Player p : all) {
                if (!p.getId().equals(playerId)) {
                    game.updatePlayerMoney(p.getId(), amount);
                    game.updatePlayerMoney(playerId, -amount);
                }
            }

        } else if (getAction() == ActionType.GET_MONEY) { // Simple bank payout
            game.updatePlayerMoney(playerId, amount);

        } else { // Simple bank payment
            game.updatePlayerMoney(playerId, -amount);
        }
    }
}
