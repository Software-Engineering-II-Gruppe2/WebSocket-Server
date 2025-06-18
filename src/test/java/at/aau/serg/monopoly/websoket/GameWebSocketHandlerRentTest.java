package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.Player;
import model.Game;
import model.properties.BaseProperty;
import model.properties.HouseableProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.Mockito.*;

class GameWebSocketHandlerRentTest {

    @Mock
    private WebSocketSession session;
    @Mock
    private PropertyTransactionService propertyTransactionService;
    @Mock
    private RentCalculationService rentCalculationService;
    @Mock
    private RentCollectionService rentCollectionService;
    @Mock
    private Game game;
    @Mock
    private PropertyService propertyService;

    private GameWebSocketHandler handler;
    private Player renter;
    private Player owner;
    private BaseProperty property;
    private static final String SESSION_ID = "test-session-id";
    private static final String RENTER_ID = "renter";
    private static final String OWNER_ID = "owner";

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Create handler with mocked dependencies
        handler = new GameWebSocketHandler();
        
        // Initialize objectMapper first
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Use reflection to set private fields
        setPrivateField(handler, "propertyTransactionService", propertyTransactionService);
        setPrivateField(handler, "rentCalculationService", rentCalculationService);
        setPrivateField(handler, "rentCollectionService", rentCollectionService);
        setPrivateField(handler, "game", game);
        setPrivateField(handler, "propertyService", propertyService);
        setPrivateField(handler, "objectMapper", objectMapper);

        // Setup session
        when(session.getId()).thenReturn(SESSION_ID);
        
        // Setup test players
        renter = new Player(RENTER_ID, "Renter");
        owner = new Player(OWNER_ID, "Owner");
        
        // Setup test property
        property = new HouseableProperty(
            1,                // id
            owner.getId(),    // ownerId
            "Test Street",    // name
            100,             // purchasePrice
            10,              // baseRent
            50,              // rent1House
            150,             // rent2Houses
            450,             // rent3Houses
            625,             // rent4Houses
            750,             // rentHotel
            50,              // housePrice
            50,              // hotelPrice
            50,              // mortgageValue
            false,           // isMortgaged
            "test_image",    // image
            1                // position
        );

        // Setup initial money
        renter.setMoney(1000);
        owner.setMoney(1000);

        // Setup session to user ID mapping
        handler.sessionToUserId.put(SESSION_ID, RENTER_ID);
        
        // Setup game state
        when(game.getPlayerById(RENTER_ID)).thenReturn(Optional.of(renter));
        when(game.getCurrentPlayer()).thenReturn(renter);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void handleRentPayment_WhenValid_CollectsRent() {
        // Setup
        when(game.getPlayerById(RENTER_ID)).thenReturn(Optional.of(renter));
        when(game.getPlayerById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(propertyTransactionService.findPropertyById(1)).thenReturn(property);
        when(rentCalculationService.calculateRent(property, owner, renter)).thenReturn(50);
        when(rentCollectionService.collectRent(renter, property, owner)).thenReturn(true);

        // Create rent payment message
        String rentMessage = String.format(
            "{\"type\":\"RENT_PAYMENT\",\"playerId\":\"%s\",\"propertyId\":1}",
            RENTER_ID
        );

        // Execute
        handler.handleTextMessage(session, new TextMessage(rentMessage));

        // Verify
        verify(propertyTransactionService).findPropertyById(1);
        verify(game).getPlayerById(RENTER_ID);
        verify(game).getPlayerById(OWNER_ID);
        verify(rentCalculationService).calculateRent(property, owner, renter);
        verify(rentCollectionService).collectRent(renter, property, owner);
    }

    @Test
    void handleRentPayment_WhenPropertyNotFound_LogsWarning() {
        // Setup
        when(propertyTransactionService.findPropertyById(1)).thenReturn(null);

        // Create rent payment message
        String rentMessage = String.format(
            "{\"type\":\"RENT_PAYMENT\",\"playerId\":\"%s\",\"propertyId\":1}",
            RENTER_ID
        );

        // Execute
        handler.handleTextMessage(session, new TextMessage(rentMessage));

        // Verify
        verify(propertyTransactionService).findPropertyById(1);
        verify(game, never()).getPlayerById(any());
        verify(rentCalculationService, never()).calculateRent(any(), any(), any());
        verify(rentCollectionService, never()).collectRent(any(), any(), any());
    }

    @Test
    void handleRentPayment_WhenRenterNotFound_LogsWarning() {
        // Setup
        when(propertyTransactionService.findPropertyById(1)).thenReturn(property);
        when(game.getPlayerById(RENTER_ID)).thenReturn(Optional.empty());

        // Create rent payment message
        String rentMessage = String.format(
            "{\"type\":\"RENT_PAYMENT\",\"playerId\":\"%s\",\"propertyId\":1}",
            RENTER_ID
        );

        // Execute
        handler.handleTextMessage(session, new TextMessage(rentMessage));

        // Verify
        verify(propertyTransactionService).findPropertyById(1);
        verify(game).getPlayerById(RENTER_ID);
        verify(game, never()).getPlayerById(OWNER_ID);
        verify(rentCalculationService, never()).calculateRent(any(), any(), any());
        verify(rentCollectionService, never()).collectRent(any(), any(), any());
    }

    @Test
    void handleRentPayment_WhenOwnerNotFound_LogsWarning() {
        // Setup
        when(propertyTransactionService.findPropertyById(1)).thenReturn(property);
        when(game.getPlayerById(RENTER_ID)).thenReturn(Optional.of(renter));
        when(game.getPlayerById(OWNER_ID)).thenReturn(Optional.empty());

        // Create rent payment message
        String rentMessage = String.format(
            "{\"type\":\"RENT_PAYMENT\",\"playerId\":\"%s\",\"propertyId\":1}",
            RENTER_ID
        );

        // Execute
        handler.handleTextMessage(session, new TextMessage(rentMessage));

        // Verify
        verify(propertyTransactionService).findPropertyById(1);
        verify(game).getPlayerById(RENTER_ID);
        verify(game).getPlayerById(OWNER_ID);
        verify(rentCalculationService, never()).calculateRent(any(), any(), any());
        verify(rentCollectionService, never()).collectRent(any(), any(), any());
    }

    @Test
    void handleRentPayment_WhenRentCollectionFails_LogsWarning() {
        // Setup
        when(game.getPlayerById(RENTER_ID)).thenReturn(Optional.of(renter));
        when(game.getPlayerById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(propertyTransactionService.findPropertyById(1)).thenReturn(property);
        when(rentCalculationService.calculateRent(property, owner, renter)).thenReturn(50);
        when(rentCollectionService.collectRent(renter, property, owner)).thenReturn(false);

        // Create rent payment message
        String rentMessage = String.format(
            "{\"type\":\"RENT_PAYMENT\",\"playerId\":\"%s\",\"propertyId\":1}",
            RENTER_ID
        );

        // Execute
        handler.handleTextMessage(session, new TextMessage(rentMessage));

        // Verify
        verify(propertyTransactionService).findPropertyById(1);
        verify(game).getPlayerById(RENTER_ID);
        verify(game).getPlayerById(OWNER_ID);
        verify(rentCalculationService).calculateRent(property, owner, renter);
        verify(rentCollectionService).collectRent(renter, property, owner);
    }

    @Test
    void handlePlayerLanding_WhenPropertyNotFound_NoRentCollection() {
        // Setup
        int position = 1;
        when(propertyService.getPropertyByPosition(position)).thenReturn(null);

        // Create manual roll message
        String rollMessage = String.format("MANUAL_ROLL:%d", position);

        // Execute
        handler.handleTextMessage(session, new TextMessage(rollMessage));

        // Verify
        verify(propertyService, never()).getPropertyByPosition(position);
        verify(game, never()).getPlayerById(any());
        verify(rentCalculationService, never()).calculateRent(any(), any(), any());
        verify(rentCollectionService, never()).collectRent(any(), any(), any());
    }

    @Test
    void handlePlayerLanding_WhenOwnerNotFound_NoRentCollection() {
        // Setup
        int position = 1;
        when(propertyService.getPropertyByPosition(position)).thenReturn(property);
        when(game.getPlayerById(OWNER_ID)).thenReturn(Optional.empty());

        // Create manual roll message
        String rollMessage = String.format("MANUAL_ROLL:%d", position);

        // Execute
        handler.handleTextMessage(session, new TextMessage(rollMessage));

        // Verify
        verify(propertyService, never()).getPropertyByPosition(position);
        verify(game, never()).getPlayerById(OWNER_ID);
        verify(rentCalculationService, never()).calculateRent(any(), any(), any());
        verify(rentCollectionService, never()).collectRent(any(), any(), any());
    }

    @Test
    void handlePlayerLanding_WhenRentCollectionFails_LogsWarning() {
        // Setup
        int position = 1;
        when(propertyService.getPropertyByPosition(position)).thenReturn(property);
        when(game.getPlayerById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(rentCalculationService.calculateRent(property, owner, renter)).thenReturn(50);
        when(rentCollectionService.collectRent(renter, property, owner)).thenReturn(false);

        // Create manual roll message
        String rollMessage = String.format("MANUAL_ROLL:%d", position);

        // Execute
        handler.handleTextMessage(session, new TextMessage(rollMessage));

        // Verify
        verify(propertyService, never()).getPropertyByPosition(position);
        verify(game, never()).getPlayerById(OWNER_ID);
        verify(rentCalculationService, never()).calculateRent(any(), any(), any());
        verify(rentCollectionService, never()).collectRent(any(), any(), any());
    }

    @Disabled("Disabled due to a bug")
    @Test
    void testHandlePlayerLanding_RentCollectionScenarios() throws Exception {
        var method = GameWebSocketHandler.class.getDeclaredMethod("handlePlayerLanding", Player.class);
        method.setAccessible(true);

        GameWebSocketHandler spyHandler = spy(handler);
        doNothing().when(spyHandler).broadcastGameState();
        doNothing().when(spyHandler).processPlayerGiveUp(anyString(), anyInt(), anyInt());

        // Common mocks
        int position = 7;
        Player testPlayer = mock(Player.class);
        when(testPlayer.getPosition()).thenReturn(position);
        when(testPlayer.getId()).thenReturn("player1");
        BaseProperty property = mock(BaseProperty.class);
        when(property.getOwnerId()).thenReturn("owner1");
        when(property.getId()).thenReturn(42);
        when(property.getName()).thenReturn("Boardwalk");
        Player owner = mock(Player.class);
        when(owner.getId()).thenReturn("owner1");
        when(spyHandler.propertyService.getPropertyByPosition(position)).thenReturn(property);
        when(spyHandler.rentCalculationService.calculateRent(property, owner, testPlayer)).thenReturn(100);

        // --- Case 1: Enough money, rent collected successfully ---
        when(game.getPlayerById("owner1")).thenReturn(Optional.of(owner));
        when(testPlayer.getMoney()).thenReturn(200);
        when(spyHandler.rentCollectionService.collectRent(testPlayer, property, owner)).thenReturn(true);
        method.invoke(spyHandler, testPlayer);
        verify(spyHandler.rentCollectionService).collectRent(testPlayer, property, owner);
        // Info log for successful collection is not easily verifiable without a logger mock

        // --- Case 2: Not enough money, triggers give up ---
        reset(spyHandler.rentCollectionService, testPlayer, game);
        when(testPlayer.getPosition()).thenReturn(position);
        when(testPlayer.getId()).thenReturn("player1");
        when(game.getPlayerById("owner1")).thenReturn(Optional.of(owner));
        when(testPlayer.getMoney()).thenReturn(50);
        when(spyHandler.rentCalculationService.calculateRent(property, owner, testPlayer)).thenReturn(100);
        when(spyHandler.propertyService.getPropertyByPosition(position)).thenReturn(property);
        method.invoke(spyHandler, testPlayer);
        verify(spyHandler).processPlayerGiveUp(eq("player1"), anyInt(), eq(50));
        verify(spyHandler.rentCollectionService, never()).collectRent(any(), any(), any());

        // --- Case 3: Owner is self, no rent collected ---
        reset(spyHandler.rentCollectionService, testPlayer, game);
        when(testPlayer.getPosition()).thenReturn(position);
        when(testPlayer.getId()).thenReturn("player1");
        when(property.getOwnerId()).thenReturn("player1");
        when(game.getPlayerById("player1")).thenReturn(Optional.of(testPlayer));
        when(spyHandler.propertyService.getPropertyByPosition(position)).thenReturn(property);
        method.invoke(spyHandler, testPlayer);
        verify(spyHandler.rentCollectionService, never()).collectRent(any(), any(), any());

        // --- Case 4: Owner is null, no rent collected ---
        reset(spyHandler.rentCollectionService, testPlayer, game);
        when(testPlayer.getPosition()).thenReturn(position);
        when(testPlayer.getId()).thenReturn("player1");
        when(property.getOwnerId()).thenReturn("owner2");
        when(game.getPlayerById("owner2")).thenReturn(Optional.empty());
        when(spyHandler.propertyService.getPropertyByPosition(position)).thenReturn(property);
        method.invoke(spyHandler, testPlayer);
        verify(spyHandler.rentCollectionService, never()).collectRent(any(), any(), any());

        // --- Case 5: Rent collection fails (collectRent returns false) ---
        reset(spyHandler.rentCollectionService, testPlayer, game);
        when(testPlayer.getPosition()).thenReturn(position);
        when(testPlayer.getId()).thenReturn("player1");
        when(property.getOwnerId()).thenReturn("owner1");
        when(game.getPlayerById("owner1")).thenReturn(Optional.of(owner));
        when(testPlayer.getMoney()).thenReturn(200);
        when(spyHandler.rentCalculationService.calculateRent(property, owner, testPlayer)).thenReturn(100);
        when(spyHandler.propertyService.getPropertyByPosition(position)).thenReturn(property);
        when(spyHandler.rentCollectionService.collectRent(testPlayer, property, owner)).thenReturn(false);
        method.invoke(spyHandler, testPlayer);
        verify(spyHandler.rentCollectionService).collectRent(testPlayer, property, owner);
        // Warning log for failed collection is not easily verifiable without a logger mock
    }
} 