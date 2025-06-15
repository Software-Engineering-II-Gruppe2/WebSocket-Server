package model;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import at.aau.serg.monopoly.websoket.PropertyTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.BotManager.BotCallback;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@ExtendWith(MockitoExtension.class)
class BotManagerTest {

    /* ─── Mocks ────────────────────────────────────────── */

    @Mock Game                 game;
    @Mock DiceManagerInterface diceManager;
    @Mock PropertyTransactionService pts;
    @Mock BotCallback          cb;

    /* ─── SUT ──────────────────────────────────────────── */

    private BotManager botManager;
    private Player     bot;

    /* ─── Test-Setup ───────────────────────────────────── */

    @BeforeEach
    void init() {
        /* 1) Bot-Spieler anlegen */
        bot = new Player("p1", "Botty");
        bot.setBot(true);
        bot.setConnected(true);

        /* 2) Spiel stutzen */
        when(game.getCurrentPlayer()).thenReturn(bot);
        when(game.getPlayerById("p1")).thenReturn(Optional.of(bot));
        when(game.getNextPlayer()).thenReturn(new Player("h1", "Human"));
        when(game.getDiceManager()).thenReturn(diceManager);
        when(game.getTurnLock()).thenReturn(new ReentrantLock());

        /* 3) Dice-Stub */
        when(diceManager.rollDices()).thenReturn(6);
        when(diceManager.isPasch()).thenReturn(false);
        when(game.updatePlayerPosition(anyInt(), anyString())).thenReturn(false);

        /* 4) Keine kaufbaren Felder */
        when(pts.findPropertyByPosition(anyInt())).thenReturn(null);

        /* 5) System-under-test */
        botManager = new BotManager(game, pts, cb);
    }

    /* ─── Tests ────────────────────────────────────────── */

    @Test
    void botBroadcasts_whenConnected() {
        botManager.start();                                // Bot in die Queue

        await().atMost(4, TimeUnit.SECONDS)                // BOT_DELAY_SEC + Puffer
                .untilAsserted(() ->
                        verify(cb, atLeastOnce())
                                .broadcast(contains("\"type\":\"DICE_ROLL\"")));
    }
@Disabled
    @Test
    void botBroadcasts_evenWhenDisconnected() {
        bot.setConnected(false);                           // offline, aber Bot bleibt Bot

        botManager.start();

        await().atMost(4, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(cb, atLeastOnce())
                                .broadcast(contains("\"type\":\"DICE_ROLL\"")));
    }
}
