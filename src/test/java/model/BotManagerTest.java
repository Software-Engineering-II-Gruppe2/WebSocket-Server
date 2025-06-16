package model;

import at.aau.serg.monopoly.websoket.PropertyTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.BotManager.BotCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BotManagerTest {

    private Game game;
    private DiceManagerInterface diceManager;
    private PropertyTransactionService pts;
    private BotCallback cb;
    private BotManager botManager;
    private Player bot;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Mocks
        game = mock(Game.class);
        diceManager = mock(DiceManagerInterface.class);
        when(game.getDiceManager()).thenReturn(diceManager);
        when(game.getTurnLock()).thenReturn(new ReentrantLock());

        pts = mock(PropertyTransactionService.class);
        cb = mock(BotCallback.class);

        // Unser Bot-Player
        bot = new Player("bot-id", "Bot 🤖");
        bot.setBot(true);
        bot.setPosition(0);
        bot.setMoney(1500);
        bot.setInJail(false);

        // Game-Stub
        when(game.getCurrentPlayer()).thenReturn(bot);
        when(game.getPlayerById("bot-id")).thenReturn(Optional.of(bot));
        // Next Player: menschlicher Spieler
        Player human = new Player("h-id", "Human");
        human.setBot(false);
        when(game.getNextPlayer()).thenReturn(human);
        // Kein GO-Pass und kein Property-Kauf
        when(game.updatePlayerPosition(anyInt(), eq("bot-id"))).thenReturn(false);
        when(pts.findPropertyByPosition(anyInt())).thenReturn(null);

        botManager = new BotManager(game, pts, cb);
    }

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

    @Test
    void doFullMove_normalRoll_advancesAndUpdates() throws Exception {
        when(diceManager.rollDices()).thenReturn(7);
        when(diceManager.isPasch()).thenReturn(false);

        invokeDoFullMove();

        // 1) Broadcast DICE_ROLL korrekt
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(cb).broadcast(cap.capture());
        ObjectNode rollMsg = (ObjectNode) mapper.readTree(cap.getValue());
        assertEquals("DICE_ROLL", rollMsg.get("type").asText());
        assertEquals(7, rollMsg.get("value").asInt());

        // 2) updateGameState + checkBankruptcy + advanceToNextPlayer
        verify(cb).checkBankruptcy();
        verify(cb).advanceToNextPlayer();
        // 3) updateGameState sollte einmal vor Schluss aufgerufen werden:
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

        // Broadcast DICE_ROLL
        verify(cb).broadcast(anyString());
        // updateGameState vor und nach Rücksetzen
        verify(cb, atLeast(2)).updateGameState();
        verify(cb).checkBankruptcy();
        // Bei Pasch darf kein advanceToNextPlayer() aufgerufen werden
        verify(cb, never()).advanceToNextPlayer();
    }

    @Test
    void handleJailTurn_onPasch_freesAndQueuesWithoutAdvance() throws Exception {
        bot.setInJail(true);
        bot.setJailTurns(1);
        when(diceManager.rollDices()).thenReturn(8);
        when(diceManager.isPasch()).thenReturn(true);

        invokeHandleJailTurn();

        verify(cb).broadcast(argThat(s -> s.contains("würfelt Pasch und ist frei")));
        verify(cb).updateGameState();
        verify(cb).checkBankruptcy();
        // Nach Pasch im Jail: kein advanceToNextPlayer
        verify(cb, never()).advanceToNextPlayer();
    }

    @Test
    void handleJailTurn_noPasch_staysInJailThenPaysBail() throws Exception {
        // 1) Bot sitzt noch im Jail nach erster Nicht-Pasch-Runde
        bot.setInJail(true);
        bot.setJailTurns(2);
        when(diceManager.rollDices()).thenReturn(2);
        when(diceManager.isPasch()).thenReturn(false);

        invokeHandleJailTurn();

        verify(cb).broadcast(argThat(s -> s.contains("sitzt im Gefängnis")));
        verify(cb).updateGameState();
        verify(cb).advanceToNextPlayer();

        // 2) Jetzt letzter Jail-Turn → zahlt Kaution
        reset(cb);
        bot.setJailTurns(1);
        when(diceManager.rollDices()).thenReturn(1);
        when(diceManager.isPasch()).thenReturn(false);

        invokeHandleJailTurn();

        verify(cb).broadcast(argThat(s -> s.contains("zahlt €50 Kaution und ist frei")));
        verify(cb).updateGameState();
        verify(cb).checkBankruptcy();
        verify(cb).advanceToNextPlayer();
    }
}
