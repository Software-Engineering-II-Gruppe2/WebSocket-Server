package model;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.BotManager.BotCallback;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotManagerTest {


    @Mock  Game                game;
    @Mock  DiceManagerInterface diceManager;
    @Mock  BotCallback         cb;

    private ScheduledExecutorService scheduler;   // unser echter Pool
    private BotManager               botManager;
    private Player                   bot;

    @BeforeEach
    void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor();   // Pool anlegen

        bot = new Player("p1", "Botty");
        bot.setBot(true);
        bot.setConnected(true);

        when(game.getCurrentPlayer()).thenReturn(bot);
        when(game.getDiceManager()).thenReturn(diceManager);
        when(diceManager.rollDices()).thenReturn(6);
        when(diceManager.isPasch()).thenReturn(false);
        when(game.updatePlayerPosition(anyInt(), anyString())).thenReturn(false);

        botManager = new BotManager(game, new ObjectMapper(), cb, scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();   // Threads sauber beenden
    }
    @Disabled ("Will be inspected later")
    @Test
    void botBroadcasts_whenConnected() {
        botManager.handleBotTurn();

        await().atMost(2, SECONDS)
                .untilAsserted(() ->
                        verify(cb, atLeastOnce()).broadcast(anyString()));
    }
    @Disabled ("Will be inspected later")
    @Test
    void botBroadcasts_evenWhenDisconnected() {
        bot.setConnected(false);          // Bot gilt als "Offline"
        botManager.handleBotTurn();

        await().atMost(2, SECONDS)
                .untilAsserted(() ->
                        verify(cb, atLeastOnce()).broadcast(anyString()));
    }

}
