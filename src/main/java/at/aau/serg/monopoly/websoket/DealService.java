package at.aau.serg.monopoly.websoket;

import data.Deals.DealResponseMessage;
import data.Deals.DealResponseType;
import lombok.Setter;
import model.Game;
import model.Player;
import model.properties.BaseProperty;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
public class DealService {

    private static final Logger logger = Logger.getLogger(DealService.class.getName());

    private final PropertyTransactionService propertyTransactionService;
    @Setter
    private Game game; // wird per Setter gesetzt

    public DealService(PropertyTransactionService propertyTransactionService) {
        this.propertyTransactionService = propertyTransactionService;
    }

    public void executeTrade(DealResponseMessage response) {
        if (game == null) {
            logger.warning("Game instance is not set in DealService.");
            return;
        }

        Player from = game.getPlayerById(response.getFromPlayerId()).orElse(null);
        Player to = game.getPlayerById(response.getToPlayerId()).orElse(null);
        if (from == null || to == null) return;

        for (int propertyId : response.getCounterPropertyIds()) {
            BaseProperty prop = propertyTransactionService.findPropertyById(propertyId);
            if (prop != null && response.getResponseType() == DealResponseType.ACCEPT) {
                prop.setOwnerId(from.getId());
            }
        }

        if (response.getCounterMoney() > 0) {
            from.subtractMoney(response.getCounterMoney());
            to.addMoney(response.getCounterMoney());
        }

        logger.info("Trade executed between " + from.getId() + " and " + to.getId());
    }
}
