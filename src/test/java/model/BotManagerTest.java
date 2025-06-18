package model;

import at.aau.serg.monopoly.websoket.PropertyService;
import at.aau.serg.monopoly.websoket.PropertyTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.BotManager.BotCallback;
import model.Game.DiceRollResult;
import model.properties.BaseProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BotManagerTest {

    private Game game;
    private DiceManagerInterface diceManager;
    private PropertyTransactionService pts;
    private BotCallback cb;
    private BotManager botManager;
    private Player bot;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Mocks & stubs
        game = mock(Game.class);
        diceManager = mock(DiceManagerInterface.class);
        when(game.getDiceManager()).thenReturn(diceManager);
        when(game.getTurnLock()).thenReturn(new ReentrantLock());

        pts = mock(PropertyTransactionService.class);
        cb  = mock(BotCallback.class);

        // Human player for next turn
        Player human = new Player("h-id", "Human");
        human.setBot(false);
        when(game.getNextPlayer()).thenReturn(human);

        // Bot player (System Under Test)
        bot = new Player("bot-id", "Bot 🤖");
        bot.setBot(true);
        bot.setPosition(0);
        bot.setMoney(1500);
        bot.setInJail(false);

        // Game stubs
        when(game.getCurrentPlayer()).thenReturn(bot);
        when(game.getPlayerById("bot-id")).thenReturn(Optional.of(bot));
        when(game.updatePlayerPosition(anyInt(), eq("bot-id"))).thenReturn(false);
        when(pts.findPropertyByPosition(anyInt())).thenReturn(null);

        botManager = new BotManager(game, pts, cb);
    }

    // ────────────────── Reflection helpers ──────────────────
    private void invokeDoFullMove() throws Exception {
        Method m = BotManager.class.getDeclaredMethod("doFullMove", Player.class);
        m.setAccessible(true);
        m.invoke(botManager, bot);
    }

    private void invokeHandleJailTurn() throws Exception {
        Method m = BotManager.class.getDeclaredMethod("handleJailTurn", Player.class);
        m.setAccessible(true);
        m.invoke(botManager, bot);
    }

    // ───────────────────────────── Tests ─────────────────────────────

    @Test
    void doFullMove_normalRoll_advancesAndUpdates() throws Exception {
        when(diceManager.rollDices()).thenReturn(7);
        when(diceManager.isPasch()).thenReturn(false);

        invokeDoFullMove();

        // 1) Broadcast DICE_ROLL
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(cb).broadcast(cap.capture());
        ObjectNode rollMsg = (ObjectNode) mapper.readTree(cap.getValue());
        assertEquals("DICE_ROLL", rollMsg.get("type").asText());
        assertEquals(7, rollMsg.get("value").asInt());

        // 2) Essential callbacks
        verify(cb).checkBankruptcy();
        verify(cb).advanceToNextPlayer();
        verify(cb, atLeastOnce()).updateGameState();
    }

    @Test
    void doFullMove_passedGo_broadcastsSystem() throws Exception {
        when(game.updatePlayerPosition(5, "bot-id")).thenReturn(true);
        when(diceManager.rollDices()).thenReturn(5);
        when(diceManager.isPasch()).thenReturn(false);

        invokeDoFullMove();

        verify(cb, atLeastOnce()).broadcast(argThat(s -> s.contains("passed GO")));
    }

    @Test
    void doFullMove_withPasch_queuesAgainAndDoesNotAdvance() throws Exception {
        when(diceManager.rollDices()).thenReturn(4);
        when(diceManager.isPasch()).thenReturn(true);

        invokeDoFullMove();

        verify(cb).broadcast(anyString());                 // DICE_ROLL
        verify(cb, atLeast(2)).updateGameState();          // vor & nach Zurücksetzen
        verify(cb).checkBankruptcy();
        verify(cb, never()).advanceToNextPlayer();         // kein Zugende wegen Pasch
    }

    @Test
    void handleJailTurn_onPasch_freesAndQueuesWithoutAdvance() throws Exception {
        // Arrange – Bot sitzt im Gefängnis
        bot.setInJail(true);
        bot.setJailTurns(1);

        // Game liefert Pasch
        when(game.handleDiceRoll("bot-id"))
                .thenReturn(new DiceRollResult(8, false, true));

        invokeHandleJailTurn();

        verify(cb).broadcast(argThat(s -> s.contains("würfelt Pasch und ist frei")));
        verify(cb).updateGameState();
        verify(cb).checkBankruptcy();
        verify(cb, never()).advanceToNextPlayer();
    }

    @Test
    void handleJailTurn_noPasch_staysInJailThenPaysBail() throws Exception {
        // ---------------- Runde 1: bleibt im Gefängnis ----------------
        bot.setInJail(true);
        bot.setJailTurns(2);

        when(game.handleDiceRoll("bot-id"))
                .thenReturn(
                        new DiceRollResult(2, false, false),   // 1. Aufruf
                        new DiceRollResult(1, false, false)    // 2. Aufruf
                );

        // Erster Jail-Turn (kein Pasch) – sitzt weiter
        invokeHandleJailTurn();

        verify(cb).broadcast(argThat(s -> s.contains("sitzt im Gefängnis")));
        verify(cb).updateGameState();
        verify(cb).advanceToNextPlayer();

        // ---------------- Runde 2: zahlt Kaution & ist frei ----------------
        reset(cb);                     // nur Callback‑Mock zurücksetzen
        bot.setJailTurns(1);          // eine Runde übrig

        invokeHandleJailTurn();

        verify(cb).broadcast(argThat(s -> s.contains("zahlt €50 Kaution und ist frei")));
        verify(cb).updateGameState();
        verify(cb).checkBankruptcy();
        verify(cb).advanceToNextPlayer();
    }


    @Test
    void botManager_shouldAdvanceToNextPlayer_ifNoPasch() throws Exception {
        // Eigenes Game‑Mock mit minimalen Abhängigkeiten
        Game game = mock(Game.class);
        PropertyTransactionService pts = mock(PropertyTransactionService.class);

        Player bot = new Player("bot123", "Bot 🤖");
        bot.setBot(true);
        bot.setMoney(1000);
        bot.setPosition(1);

        BaseProperty property = mock(BaseProperty.class);
        when(property.getId()).thenReturn(42);
        when(property.getOwnerId()).thenReturn(null);
        when(pts.findPropertyByPosition(bot.getPosition())).thenReturn(property);
        when(pts.canBuyProperty(bot, 42)).thenReturn(false); // kein Kauf

        when(game.getPlayerById("bot123")).thenReturn(Optional.of(bot));
        when(game.getCurrentPlayer()).thenReturn(bot);
        when(game.getNextPlayer()).thenReturn(new Player("p2", "Mensch"));

        DiceManagerInterface diceManager = mock(DiceManagerInterface.class);
        when(diceManager.rollDices()).thenReturn(4);
        when(diceManager.isPasch()).thenReturn(false);
        when(game.getDiceManager()).thenReturn(diceManager);
        when(game.updatePlayerPosition(4, "bot123")).thenReturn(false);

        when(game.getTurnLock()).thenReturn(new ReentrantLock());

        BotCallback cb = mock(BotCallback.class);

        BotManager botManager = new BotManager(game, pts, cb);

        // doFullMove via Reflection
        Method m = BotManager.class.getDeclaredMethod("doFullMove", Player.class);
        m.setAccessible(true);
        m.invoke(botManager, bot);

        verify(cb).advanceToNextPlayer();
        verify(cb, atLeastOnce()).updateGameState();
    }
}
