package model;

import data.DiceRollMessage;
import model.properties.BaseProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BotManager {

    private final Game game;
    private final ObjectMapper objectMapper;
    private final Logger logger = Logger.getLogger(BotManager.class.getName());
    private final ScheduledExecutorService scheduler;

    public interface BotCallback {
        void broadcast(String message);
        void updateGameState();
    }

    private final BotCallback callback;

    public BotManager(Game game,
                      ObjectMapper objectMapper,
                      BotCallback callback) {
        this(game, objectMapper, callback, Executors.newSingleThreadScheduledExecutor());
    }

    // Für Tests
    BotManager(Game game,
               ObjectMapper objectMapper,
               BotCallback callback,
               ScheduledExecutorService scheduler) {
        this.game         = game;
        this.objectMapper = objectMapper;
        this.callback     = callback;
        this.scheduler    = scheduler;
    }


    public void handleBotTurn() {
        Player current = game.getCurrentPlayer();
        if (current == null || !current.isBot() ) return;

        logger.info("Bot " + current.getName() + " is taking their turn...");

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    int roll = game.getDiceManager().rollDices();
                    boolean isPasch = game.getDiceManager().isPasch();

                    logger.info("Bot rolled: " + roll + " | Pasch: " + isPasch);

                    current.setHasRolledThisTurn(!isPasch);

                    DiceRollMessage drm = new DiceRollMessage(current.getId(), roll, false, isPasch);
                    String json = objectMapper.writeValueAsString(drm);
                    callback.broadcast(json);

                    boolean passedGo = game.updatePlayerPosition(roll, current.getId());
                    if (passedGo) {
                        callback.broadcast("Player " + current.getName() + " passed GO and collected €200");
                    }

                    // Kaufentscheidung
                    BaseProperty property = game.getPropertyService().getPropertyByPosition(current.getPosition());
                    if (property != null && game.getPropertyTransactionService().canBuyProperty(current, property.getId())) {
                        boolean bought = game.getPropertyTransactionService().buyProperty(current, property.getId());
                        if (bought) {
                            callback.broadcast("{\"type\":\"PROPERTY_BOUGHT\",\"message\":\"" +
                                    current.getName() + " bought " + property.getName() + "\"}");
                        }
                    }

                    // Zug beenden
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            logger.info("Bot ends turn.");
                            game.nextPlayer();
                            callback.updateGameState();

                            // Prüfe gleich den nächsten Spieler → falls wieder Bot
                            handleBotTurn();
                        }
                    }, 1000);

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Bot turn error: " + e.getMessage(), e);
                }
            }
        }, 1000);
    }
}
