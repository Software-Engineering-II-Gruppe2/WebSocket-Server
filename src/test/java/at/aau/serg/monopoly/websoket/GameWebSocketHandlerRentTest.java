package at.aau.serg.monopoly.websoket;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.Player;
import model.Game;
import model.properties.BaseProperty;
import model.properties.HouseableProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.Mockito.*;

class GameWebSocketHandlerRentTest {

    // fixme this shows that the websocket has too many game specific dependencies for decisions
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
    private ObjectMapper objectMapper;
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
        objectMapper = new ObjectMapper();
        
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
    void handleRentPayment_WhenValid_CollectsRent() throws IOException {
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
    void handleRentPayment_WhenPropertyNotFound_LogsWarning() throws IOException {
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
    void handleRentPayment_WhenRenterNotFound_LogsWarning() throws IOException {
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
    void handleRentPayment_WhenOwnerNotFound_LogsWarning() throws IOException {
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
    void handleRentPayment_WhenRentCollectionFails_LogsWarning() throws IOException {
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
    void handlePlayerLanding_WhenPropertyNotFound_NoRentCollection() throws IOException {
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
    void handlePlayerLanding_WhenOwnerNotFound_NoRentCollection() throws IOException {
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
    void handlePlayerLanding_WhenRentCollectionFails_LogsWarning() throws IOException {
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
} 