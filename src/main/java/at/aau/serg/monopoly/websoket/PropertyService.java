package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import model.Game;
import model.Player;
import model.properties.BaseProperty;
import model.properties.HouseableProperty;
import model.properties.TrainStation;
import model.properties.Utility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PropertyService {

    public PropertyService() {
        // only for tests / fallback
    }

    private static final Logger logger = Logger.getLogger(PropertyService.class.getName());
    private Game game;
    @Getter
    private List<HouseableProperty> houseableProperties;
    @Getter
    private List<TrainStation> trainStations;
    @Getter
    private List<Utility> utilities;


    @Autowired(required = false)
    public PropertyService(Game game) {
        this.game = game;
    }

    @PostConstruct
    public void init() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("propertyData.json")) {
            if (is == null) {
                throw new IllegalStateException("propertyData.json not found in resources folder");
            }
            PropertyDataWrapper wrapper = mapper.readValue(is, PropertyDataWrapper.class);
            this.houseableProperties = wrapper.getProperties();
            this.trainStations = wrapper.getTrainStations();
            this.utilities = wrapper.getUtilities();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize property data", e);
        }
    }

    public HouseableProperty getHouseablePropertyById(int id) {
        return houseableProperties.stream()
                .filter(property -> property.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets a player by their ID
     * @param playerId The ID of the player to find
     * @return The player if found, null otherwise
     */
    public Player getPlayerById(String playerId) {
        if (playerId == null) {
            logger.log(Level.WARNING, "Attempted to get player with null ID");
            return null;
        }
        if (game == null) {
            logger.log(Level.WARNING, "Game instance is null, cannot get player");
            return null;
        }
        return game.getPlayers().stream()
                .filter(player -> player.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets a property by its position on the board
     * @param position The position to find the property at
     * @return The property if found, null otherwise
     */
    public BaseProperty getPropertyByPosition(int position) {
        // Check houseable properties
        BaseProperty property = houseableProperties.stream()
                .filter(p -> p.getPosition() == position)
                .findFirst()
                .orElse(null);

        if (property != null) {
            return property;
        }

        // Check train stations
        property = trainStations.stream()
                .filter(p -> p.getPosition() == position)
                .findFirst()
                .orElse(null);

        if (property != null) {
            return property;
        }

        // Check utilities
        return utilities.stream()
                .filter(p -> p.getPosition() == position)
                .findFirst()
                .orElse(null);
    }

    public void resetBoardOwnership() {
        houseableProperties.forEach(p -> {
            p.setOwnerId(null);
            p.setMortgaged(false);  // falls vorhanden
        });
        trainStations.forEach(ts -> ts.setOwnerId(null));
        utilities.forEach(u -> u.setOwnerId(null));
    }
}

