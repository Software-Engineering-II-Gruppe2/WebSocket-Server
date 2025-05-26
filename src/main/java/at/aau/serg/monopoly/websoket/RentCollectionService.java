package at.aau.serg.monopoly.websoket;

import model.Player;
import model.properties.BaseProperty;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class RentCollectionService {
    private static final Logger logger = Logger.getLogger(RentCollectionService.class.getName());

    private final PropertyService propertyService;
    private final RentCalculationService rentCalculationService;

    public RentCollectionService(PropertyService propertyService, RentCalculationService rentCalculationService) {
        this.propertyService = propertyService;
        this.rentCalculationService = rentCalculationService;
    }

    /**
     * Validates if rent can be collected from a player
     * @param renter The player who landed on the property
     * @param property The property to collect rent for
     * @return true if rent can be collected, false otherwise
     */
    boolean canCollectRent(Player renter, BaseProperty property) {
        if (renter == null || property == null) {
            logger.log(Level.WARNING, "Invalid parameters for rent collection validation");
            return false;
        }

        // Check if property is owned by another player
        if (property.getOwnerId() == null) {
            logger.log(Level.INFO, "Property {0} is not owned by any player", property.getName());
            return false;
        }
        
        if (property.getOwnerId().equals(renter.getId())) {
            logger.log(Level.INFO, "Property {0} is owned by the renter {1}", new Object[]{property.getName(), renter.getId()});
            return false;
        }

        // Check if property is mortgaged
        if (property.isMortgaged()) {
            logger.log(Level.INFO, "Property {0} is mortgaged, no rent can be collected", property.getName());
            return false;
        }

        logger.log(Level.INFO, "Rent can be collected for property {0} from player {1}", new Object[]{property.getName(), renter.getId()});
        return true;
    }

    // fixme extract some sub methods
    /**
     * Collects rent from a player who landed on a property
     * @param renter The player who landed on the property
     * @param property The property to collect rent for
     * @param owner The owner of the property
     * @return true if rent was collected successfully, false otherwise
     */
    public boolean collectRent(Player renter, BaseProperty property, Player owner) {
        logger.log(Level.INFO, "Attempting to collect rent for property {0} from player {1}", 
            new Object[]{property.getName(), renter.getId()});
            
        if (!canCollectRent(renter, property)) {
            logger.log(Level.WARNING, "Cannot collect rent for property {0}", property.getName());
            return false;
        }

        if (owner == null) {
            logger.log(Level.WARNING, "Property owner not found for property {0}", property.getName());
            return false;
        }

        // Calculate rent amount
        int rentAmount = rentCalculationService.calculateRent(property, owner, renter);
        logger.log(Level.INFO, "Calculated rent amount: {0} for property {1}", new Object[]{rentAmount, property.getName()});
        
        // Check if renter has enough money
        if (renter.getMoney() < rentAmount) {
            logger.log(Level.INFO, "Player {0} has insufficient funds to pay rent. Has: {1}, Needs: {2}", 
                new Object[]{renter.getId(), renter.getMoney(), rentAmount});
            return false;
        }

        // Process rent payment
        try {
            renter.subtractMoney(rentAmount);
            owner.addMoney(rentAmount);
            
            logger.log(Level.INFO, "Rent of {0} collected from player {1} for property {2}. New balances - Renter: {3}, Owner: {4}", 
                new Object[]{rentAmount, renter.getId(), property.getName(), renter.getMoney(), owner.getMoney()});
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing rent payment: {0}", e.getMessage());
            return false;
        }
    }
} 