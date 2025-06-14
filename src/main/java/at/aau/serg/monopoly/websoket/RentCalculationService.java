package at.aau.serg.monopoly.websoket;

import model.Player;
import model.properties.BaseProperty;
import model.properties.HouseableProperty;
import model.properties.TrainStation;
import model.properties.Utility;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class RentCalculationService {
    private static final Logger logger = Logger.getLogger(RentCalculationService.class.getName());

    private final PropertyService propertyService;

    public RentCalculationService(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /**
     * Calculates the rent for a property based on its type and owner
     * @param property The property to calculate rent for
     * @param owner The owner of the property
     * @param renter The player who landed on the property
     * @return The calculated rent amount
     */
    public int calculateRent(BaseProperty property, Player owner, Player renter) {
        if (property == null || owner == null || renter == null) {
            logger.log(Level.WARNING, "Invalid parameters for rent calculation");
            return 0;
        }

        // Calculate rent based on property type
        if (property instanceof HouseableProperty houseableProperty) {
            return calculateHouseablePropertyRent(houseableProperty);
        } else if (property instanceof TrainStation) {
            return calculateTrainStationRent((TrainStation) property);
        } else if (property instanceof Utility) {
            return calculateUtilityRent((Utility) property, owner);
        }

        logger.log(Level.WARNING, "Unknown property type for rent calculation: {0}", property.getClass().getName());
        return 0;
    }

    /**
     * Calculates rent for a houseable property based on number of houses/hotels
     * @param property The houseable property
     * @return The calculated rent amount
     */
    private int calculateHouseablePropertyRent(HouseableProperty property) {
        // For now, just return base rent
        return property.getBaseRent();
    }

    /**
     * Calculates rent for a train station based on number of stations owned
     * @param station The train station
     * @return The calculated rent amount
     */
    private int calculateTrainStationRent(TrainStation station) {
        // For now, just return base rent
        return station.getBaseRent();
    }

    /**
     * Calculates rent for a utility based on number of utilities owned
     * @param utility The utility property
     * @param owner The owner of the property
     * @return The calculated rent amount
     */
    private int calculateUtilityRent(Utility utility, Player owner) {
        // Get the number of utilities owned by the owner
        int ownedUtilities = propertyService.getUtilities().stream()
                .filter(u -> owner.getId().equals(u.getOwnerId()))
                .toList()
                .size();

        // Calculate rent based on number of utilities owned
        return ownedUtilities == 1 ? 
            utility.getRentOneUtilityMultiplier() : 
            utility.getRentTwoUtilitiesMultiplier();
    }
}