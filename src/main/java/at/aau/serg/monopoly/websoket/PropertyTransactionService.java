package at.aau.serg.monopoly.websoket;

import model.Player;
import model.properties.BaseProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PropertyTransactionService {

    private static final Logger logger = Logger.getLogger(PropertyTransactionService.class.getName());

    @Autowired
    private PropertyService propertyService;

    /**
     * Validates if a player is currently on the property they're trying to buy
     * @param player The player attempting to buy
     * @param property The property being bought
     * @return true if the player is on the property, false otherwise
     */
    private boolean isPlayerOnProperty(Player player, BaseProperty property) {
        if (player == null || property == null) {
            logger.log(Level.WARNING, "Player or property is null in position validation");
            return false;
        }

        boolean isOnProperty = player.getPosition() == property.getPosition();
        
        if (!isOnProperty) {
            logger.log(Level.INFO, "Player {0} is not on property {1}. Player position: {2}, Property position: {3}",
                    new Object[]{player.getId(), property.getName(), player.getPosition(), property.getPosition()});
        }
        
        return isOnProperty;
    }

    /**
     * Checks if a player can buy a specific property
     * @param player The player attempting to buy
     * @param propertyId The ID of the property to buy
     * @return true if the player can buy the property, false otherwise
     */
    public boolean canBuyProperty(Player player, int propertyId) {
        BaseProperty property = findPropertyById(propertyId);

        if (property == null) {
            return false;
        }

        // Check if property is unowned, player has enough money, and is on the property
        return property.getOwnerId() == null &&
                player.getMoney() >= property.getPurchasePrice() &&
                isPlayerOnProperty(player, property);
    }

    /**
     * Executes the purchase of a property for a player.
     * @param player The player buying the property.
     * @param propertyId The ID of the property being bought.
     * @return true if the purchase was successful, false otherwise.
     */
    public boolean buyProperty(Player player, int propertyId) {
        BaseProperty property = findPropertyById(propertyId);

        // Double-check conditions in case state changed
        if (property == null || property.getOwnerId() != null || 
            player.getMoney() < property.getPurchasePrice() || 
            !isPlayerOnProperty(player, property)) {
            logger.log(Level.WARNING, "Attempted to buy property {0} by player {1} failed pre-check.", 
                    new Object[]{propertyId, player.getId()});
            return false;
        }

        try {
            // Perform transaction - directly use player ID as owner ID
            player.subtractMoney(property.getPurchasePrice());
            property.setOwnerId(player.getId());

            logger.log(Level.INFO, "Player {0} successfully bought property {1}. New balance: {2}",
                    new Object[]{player.getId(), propertyId, player.getMoney()});
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "An unexpected error occurred during property purchase: {0}", e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to find a property by its ID across all property types
     */
    BaseProperty findPropertyById(int propertyId) {
        // Check houseable properties
        BaseProperty property = propertyService.getHouseablePropertyById(propertyId);
        if (property != null) {
            return property;
        }

        // Check train stations
        property = propertyService.getTrainStations().stream()
                .filter(p -> p.getId() == propertyId)
                .findFirst()
                .orElse(null);
        if (property != null) {
            return property;
        }

        // Check utilities
        return propertyService.getUtilities().stream()
                .filter(p -> p.getId() == propertyId)
                .findFirst()
                .orElse(null);
    }
}