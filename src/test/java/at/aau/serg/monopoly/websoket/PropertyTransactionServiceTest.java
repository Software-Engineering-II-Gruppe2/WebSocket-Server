package at.aau.serg.monopoly.websoket;

import model.Player;
import model.properties.BaseProperty;
import model.properties.HouseableProperty; // Using a concrete subclass for testing
import model.properties.TrainStation;
import model.properties.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Initialize Mockito
class PropertyTransactionServiceTest {

    @Mock // Mock the dependency
    private PropertyService propertyService;

    @InjectMocks // Inject the mock into the service under test
    private PropertyTransactionService propertyTransactionService;

    private Player testPlayer;
    private HouseableProperty testProperty; // Use a concrete type for setup

    // Test constants
    private static final int PROPERTY_ID = 1;
    private static final int PURCHASE_PRICE = 100;
    private static final int MORTGAGE_VALUE = 50;
    private static final String PLAYER_ID = "player123";
    private static final String PROPERTY_NAME = "Test Street";

    @BeforeEach
    void setUp() {
        // Reset mocks and setup test objects before each test
        testPlayer = new Player(PLAYER_ID, "Test Player");
        testProperty = new HouseableProperty(
                PROPERTY_ID, null, PROPERTY_NAME, PURCHASE_PRICE, // Initially unowned (null ownerId)
                10, 20, 30, 40, 50, 60, 50, 50, // Rent/house prices (example values)
                MORTGAGE_VALUE, false, "image", 1 // Added position parameter
        );

        // Make all property service lookups lenient
        lenient().when(propertyService.getHouseablePropertyById(anyInt())).thenReturn(null);
        lenient().when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        lenient().when(propertyService.getUtilities()).thenReturn(Collections.emptyList());
    }

    // --- Tests for canBuyProperty ---

    @Test
    void canBuyProperty_SufficientFunds_Unowned_ReturnsTrue() {
        testPlayer.setMoney(PURCHASE_PRICE + 50); // Player has more than enough
        testPlayer.setPosition(1); // Set player position to match property position
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        assertTrue(propertyTransactionService.canBuyProperty(testPlayer, PROPERTY_ID));
    }

    @Test
    void canBuyProperty_ExactFunds_Unowned_ReturnsTrue() {
        testPlayer.setMoney(PURCHASE_PRICE); // Player has exact amount
        testPlayer.setPosition(1); // Set player position to match property position
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        assertTrue(propertyTransactionService.canBuyProperty(testPlayer, PROPERTY_ID));
    }

    @Test
    void canBuyProperty_InsufficientFunds_Unowned_ReturnsFalse() {
        testPlayer.setMoney(PURCHASE_PRICE - 1); // Player has less than needed
        testPlayer.setPosition(1); // Set player position to match property position
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        assertFalse(propertyTransactionService.canBuyProperty(testPlayer, PROPERTY_ID));
    }

    @Test
    void canBuyProperty_SufficientFunds_Owned_ReturnsFalse() {
        testPlayer.setMoney(PURCHASE_PRICE + 50);
        testPlayer.setPosition(1); // Set player position to match property position
        testProperty.setOwnerId("anotherPlayer"); // Property is already owned
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        assertFalse(propertyTransactionService.canBuyProperty(testPlayer, PROPERTY_ID));
    }

    @Test
    void canBuyProperty_PropertyNotFound_ReturnsFalse() {
        testPlayer.setMoney(PURCHASE_PRICE + 50);
        testPlayer.setPosition(1); // Set player position to match property position
        // Mock to return null when property ID is requested
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null);
        // Ensure other lookups also return null/empty
        when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        when(propertyService.getUtilities()).thenReturn(Collections.emptyList());

        assertFalse(propertyTransactionService.canBuyProperty(testPlayer, PROPERTY_ID));
        // Verify that all property lookup methods were potentially called by findPropertyById
        verify(propertyService).getHouseablePropertyById(PROPERTY_ID);
        verify(propertyService).getTrainStations();
        verify(propertyService).getUtilities();
    }

    @Test
    void canBuyProperty_WrongPosition_ReturnsFalse() {
        testPlayer.setMoney(PURCHASE_PRICE + 50);
        testPlayer.setPosition(2); // Set player position to different from property position
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        assertFalse(propertyTransactionService.canBuyProperty(testPlayer, PROPERTY_ID));
    }

    // --- Tests for buyProperty ---

    @Test
    void buyProperty_SuccessfulPurchase() {
        testPlayer.setMoney(PURCHASE_PRICE + 50);
        testPlayer.setPosition(1); // Set player position to match property position
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        boolean result = propertyTransactionService.buyProperty(testPlayer, PROPERTY_ID);

        assertTrue(result);
        assertEquals(50, testPlayer.getMoney()); // Money should be deducted
        assertEquals(PLAYER_ID, testProperty.getOwnerId()); // Owner ID should be set
    }

    @Test
    void buyProperty_InsufficientFunds_FailsPreCheck() {
        testPlayer.setMoney(PURCHASE_PRICE - 1);
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        boolean result = propertyTransactionService.buyProperty(testPlayer, PROPERTY_ID);

        assertFalse(result);
        assertEquals(PURCHASE_PRICE - 1, testPlayer.getMoney()); // Money should not change
        assertNull(testProperty.getOwnerId()); // Owner ID should remain null
    }

    @Test
    void buyProperty_AlreadyOwned_FailsPreCheck() {
        testPlayer.setMoney(PURCHASE_PRICE + 50);
        testProperty.setOwnerId("anotherPlayer");
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        boolean result = propertyTransactionService.buyProperty(testPlayer, PROPERTY_ID);

        assertFalse(result);
        assertEquals(PURCHASE_PRICE + 50, testPlayer.getMoney()); // Money should not change
        assertEquals("anotherPlayer", testProperty.getOwnerId()); // Owner ID should not change
    }

    @Test
    void buyProperty_PropertyNotFound_FailsPreCheck() {
        testPlayer.setMoney(PURCHASE_PRICE + 50);
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null);
        // Ensure other lookups also return null/empty
        when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        when(propertyService.getUtilities()).thenReturn(Collections.emptyList());


        boolean result = propertyTransactionService.buyProperty(testPlayer, PROPERTY_ID);

        assertFalse(result);
        assertEquals(PURCHASE_PRICE + 50, testPlayer.getMoney()); // Money should not change
    }

    @Test
    void buyProperty_WrongPosition_FailsPreCheck() {
        testPlayer.setMoney(PURCHASE_PRICE + 50);
        testPlayer.setPosition(2); // Set player position to different from property position
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        boolean result = propertyTransactionService.buyProperty(testPlayer, PROPERTY_ID);

        assertFalse(result);
        assertEquals(PURCHASE_PRICE + 50, testPlayer.getMoney()); // Money should not change
        assertNull(testProperty.getOwnerId()); // Owner ID should remain null
    }

    @Test
    void findPropertyById_FindsHouseableProperty() {
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);

        BaseProperty found = propertyTransactionService.findPropertyById(PROPERTY_ID);

        assertNotNull(found);
        assertEquals(PROPERTY_ID, found.getId());
        verify(propertyService).getHouseablePropertyById(PROPERTY_ID);
        verify(propertyService, never()).getTrainStations(); // Should not be called if found earlier
        verify(propertyService, never()).getUtilities();     // Should not be called if found earlier
    }

    @Test
    void findPropertyById_FindsTrainStation() {
        // Setup train station property
        TrainStation trainStation = new TrainStation(
                PROPERTY_ID,
                null,  // unowned
                "Test Station",
                200,   // purchase price
                25,    // baseRent
                50,    // rent2Stations
                75,    // rent3Stations
                100,   // rent4Stations
                MORTGAGE_VALUE,
                false, // not mortgaged
                "train_image",
                5      // Added position parameter
        );

        // Mock houseable property to return null (not found)
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null);
        // Mock train stations to include our test station
        when(propertyService.getTrainStations()).thenReturn(Collections.singletonList(trainStation));

        BaseProperty found = propertyTransactionService.findPropertyById(PROPERTY_ID);

        assertNotNull(found);
        assertEquals(PROPERTY_ID, found.getId());
        assertEquals("Test Station", found.getName());
        verify(propertyService).getHouseablePropertyById(PROPERTY_ID);
        verify(propertyService).getTrainStations();
        verify(propertyService, never()).getUtilities(); // Should not be called if found in train stations
    }

    @Test
    void findPropertyById_PropertyNotFound_ReturnsNull() {
        // Mock all property lookups to return null or empty
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null);
        when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        when(propertyService.getUtilities()).thenReturn(Collections.emptyList());

        BaseProperty found = propertyTransactionService.findPropertyById(PROPERTY_ID);

        assertNull(found);
        verify(propertyService).getHouseablePropertyById(PROPERTY_ID);
        verify(propertyService).getTrainStations();
        verify(propertyService).getUtilities();
    }

    @Test
    void findPropertyById_FindsUtility() {
        // Setup utility property
        Utility utility = new Utility(
                PROPERTY_ID,
                null,  // unowned
                "Test Utility",
                150,   // purchase price
                4,     // rentOneUtilityMultiplier
                10,    // rentTwoUtilitiesMultiplier
                MORTGAGE_VALUE,
                false, // not mortgaged
                "utility_image",
                12     // Added position parameter
        );

        // Mock houseable property and train stations to return null/empty (not found)
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null);
        when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        when(propertyService.getUtilities()).thenReturn(Collections.singletonList(utility));

        BaseProperty found = propertyTransactionService.findPropertyById(PROPERTY_ID);

        assertNotNull(found);
        assertEquals(PROPERTY_ID, found.getId());
        assertEquals("Test Utility", found.getName());
        verify(propertyService).getHouseablePropertyById(PROPERTY_ID);
        verify(propertyService).getTrainStations();
        verify(propertyService).getUtilities();
    }

    // --- Tests for sellProperty ---

    @Test
    void sellProperty_SuccessfulSale() {
        // Arrange
        testProperty.setOwnerId(PLAYER_ID);
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);
        int initialMoney = 100;
        testPlayer.setMoney(initialMoney);

        // Act
        boolean result = propertyTransactionService.sellProperty(testPlayer, PROPERTY_ID);

        // Assert
        assertTrue(result);
        assertEquals(initialMoney + (PURCHASE_PRICE / 2), testPlayer.getMoney()); // Money should be increased by half the purchase price
        assertNull(testProperty.getOwnerId()); // Owner ID should be cleared
    }

    @Test
    void sellProperty_PropertyNotFound_ReturnsFalse() {
        // Arrange
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null);
        when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        when(propertyService.getUtilities()).thenReturn(Collections.emptyList());
        int initialMoney = 100;
        testPlayer.setMoney(initialMoney);

        // Act
        boolean result = propertyTransactionService.sellProperty(testPlayer, PROPERTY_ID);

        // Assert
        assertFalse(result);
        assertEquals(initialMoney, testPlayer.getMoney()); // Money should not change
    }

    @Test
    void sellProperty_NotOwnedByPlayer_ReturnsFalse() {
        // Arrange
        testProperty.setOwnerId("differentPlayer");
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(testProperty);
        int initialMoney = 100;
        testPlayer.setMoney(initialMoney);

        // Act
        boolean result = propertyTransactionService.sellProperty(testPlayer, PROPERTY_ID);

        // Assert
        assertFalse(result);
        assertEquals(initialMoney, testPlayer.getMoney()); // Money should not change
        assertEquals("differentPlayer", testProperty.getOwnerId()); // Owner ID should not change
    }

    @Test
    void sellProperty_WithTrainStation() {
        // Arrange
        TrainStation trainStation = new TrainStation(
                PROPERTY_ID,
                PLAYER_ID,  // owned by test player
                "Test Station",
                200,   // purchase price
                25,    // baseRent
                50,    // rent2Stations
                75,    // rent3Stations
                100,   // rent4Stations
                MORTGAGE_VALUE,
                false, // not mortgaged
                "train_image",
                5      // position
        );
        lenient().when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null);
        lenient().when(propertyService.getTrainStations()).thenReturn(Collections.singletonList(trainStation));
        lenient().when(propertyService.getUtilities()).thenReturn(Collections.emptyList());
        int initialMoney = 100;
        testPlayer.setMoney(initialMoney);

        // Act
        boolean result = propertyTransactionService.sellProperty(testPlayer, PROPERTY_ID);

        // Assert
        assertTrue(result);
        assertEquals(initialMoney + (200 / 2), testPlayer.getMoney()); // Money should be increased by half the purchase price
        assertNull(trainStation.getOwnerId()); // Owner ID should be cleared
    }

    @Test
    void sellProperty_WithUtility() {
        // Arrange
        Utility utility = new Utility(
                PROPERTY_ID,
                PLAYER_ID,  // owned by test player
                "Test Utility",
                150,   // purchase price
                4,     // rentOneUtilityMultiplier
                10,    // rentTwoUtilitiesMultiplier
                MORTGAGE_VALUE,
                false, // not mortgaged
                "utility_image",
                12     // position
        );
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null);
        when(propertyService.getTrainStations()).thenReturn(Collections.emptyList());
        when(propertyService.getUtilities()).thenReturn(Collections.singletonList(utility));
        int initialMoney = 100;
        testPlayer.setMoney(initialMoney);

        // Act
        boolean result = propertyTransactionService.sellProperty(testPlayer, PROPERTY_ID);

        // Assert
        assertTrue(result);
        assertEquals(initialMoney + (150 / 2), testPlayer.getMoney()); // Money should be increased by half the purchase price
        assertNull(utility.getOwnerId()); // Owner ID should be cleared
    }

    @Test
    void canBuyProperty_PropertyIsNull_ReturnsFalse() {
        // Arrange
        testPlayer.setPosition(1);
        testPlayer.setMoney(PURCHASE_PRICE);
        when(propertyService.getHouseablePropertyById(PROPERTY_ID)).thenReturn(null); // Property = null

        // Act
        boolean result = propertyTransactionService.canBuyProperty(testPlayer, PROPERTY_ID);

        // Assert
        assertFalse(result);
    }

}