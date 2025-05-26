package model;

import model.properties.HouseableProperty;
import model.properties.TrainStation;
import model.properties.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// fixme dont need to test Lombok generation functionality
class PropertyTest {

    private HouseableProperty houseableProperty;
    private TrainStation trainStation;
    private Utility utility;

    @BeforeEach
    void setUp() {
        //Beispielinstanzen mit Testwerten
        houseableProperty = new HouseableProperty(
                1,                // id
                null,             // ownerId
                "Badstrasse",     // name
                60,               // purchasePrice
                2,                // baseRent
                10,               // rent1House
                30,               // rent2Houses
                90,               // rent3Houses
                160,              // rent4Houses
                250,              // rentHotel
                50,               // housePrice
                50,               // hotelPrice
                30,               // mortgageValue
                false,            // isMortgaged
                "Badstrasse_Bild",
                1                 // position
        );

        trainStation = new TrainStation(
                101,              // id
                null,             // ownerId
                "Südbahnhof",     // name
                200,              // purchasePrice
                50,               // baseRent
                100,              // rent2Stations
                150,              // rent3Stations
                200,              // rent4Stations
                100,              // mortgageValue
                false,            // isMortgaged
                "Südbahnhof_Bild",
                5                 // position
        );

        utility = new Utility(
                201,              // id
                null,             // ownerId
                "Elektrizitätswerk",  // name
                150,              // purchasePrice
                4,                // rentOneUtilityMultiplier
                10,               // rentTwoUtilitiesMultiplier
                75,               // mortgageValue
                false,            // isMortgaged
                "Elektrizität_Bild",
                12                // position
        );
    }

    @Test
    void testHouseablePropertyGetters() {
        assertEquals(1, houseableProperty.getId());
        assertEquals("Badstrasse", houseableProperty.getName());
        assertEquals(60, houseableProperty.getPurchasePrice());
        assertEquals(2, houseableProperty.getBaseRent());
        assertFalse(houseableProperty.isMortgaged());
    }

    @Test
    void testTrainStationGetters() {
        assertEquals(101, trainStation.getId());
        assertEquals("Südbahnhof", trainStation.getName());
        assertEquals(200, trainStation.getPurchasePrice());
        assertEquals(50, trainStation.getBaseRent());
        assertFalse(trainStation.isMortgaged());
    }

    @Test
    void testUtilityGetters() {
        assertEquals(201, utility.getId());
        assertEquals("Elektrizitätswerk", utility.getName());
        assertEquals(150, utility.getPurchasePrice());
        assertEquals(4, utility.getRentOneUtilityMultiplier());
        assertFalse(utility.isMortgaged());
    }
}