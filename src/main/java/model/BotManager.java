package model;

import at.aau.serg.monopoly.websoket.PropertyTransactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.properties.BaseProperty;

import java.util.concurrent.*;
import java.util.logging.Logger;




public class BotManager {



    /** Vom Handler bereitgestellte Funktionen, die der Bot aufrufen darf. */
    public interface BotCallback {
        void broadcast(String msg);          // Chat / Systemmeldung an alle
        void updateGameState();              // kompletten Spielstand pushen
        void advanceToNextPlayer();          // Zug an nächsten Spieler übergeben
        void checkBankruptcy();
    }

    /* ────────────────── Felder ────────────────── */

    private static final Logger log = Logger.getLogger(BotManager.class.getName());
    private static final long BOT_DELAY_SEC = 3;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Game game;
    private final PropertyTransactionService pts;
    private final BotCallback cb;

    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "BotThread");
                t.setDaemon(true);
                return t;
            });

    /* ────────────────── Konstruktor ────────────────── */

    public BotManager(Game game,
                      PropertyTransactionService pts,
                      BotCallback cb) {
        this.game = game;
        this.pts  = pts;
        this.cb   = cb;
    }

    /* ────────────────── Lebenszyklus ────────────────── */

    /** startet die Dauerschleife (einmal nach Game-Start aufrufen) */
    public void start() {
        // 1) Aktuellen Spieler ermitteln
        Player current = game.getCurrentPlayer();
        if (current == null) return;

        // 2) Falls es ein Bot ist → in die Queue einreihen
        if (current.isBot()) {
            // Wir benutzen dieselbe Methode, die auch der Handler später
            // aufruft, damit die Logik an einer Stelle bleibt.
            queueBotTurn(current.getId());
        }
    }

    /** sofort beenden (z.B. wenn das Spiel endet) */
    public void shutdown() {
        exec.shutdownNow();
    }

    /* ────────────────── Externe Trigger ────────────────── */

    /**
     * Wird vom Handler aufgerufen, wenn *nach* einem Bot-Zug
     * direkt der nächste Bot dran ist → sofort verarbeiten.
     */
    public void queueBotTurn(String botId) {
        exec.schedule(
                () -> processSpecificBot(botId),
                BOT_DELAY_SEC,
                TimeUnit.SECONDS);
    }

    private void processSpecificBot(String botId) {
        if (!game.getTurnLock().tryLock()) return;
        try {
            Player p = game.getPlayerById(botId).orElse(null);
            if (p != null && p.isBot()) {
                doFullMove(p);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } finally {
            game.getTurnLock().unlock();
        }
    }

    /** Ein *vollständiger* Bot-Zug (würfeln, ziehen, kaufen, evtl. Ende). */
    private void doFullMove(Player bot) throws JsonProcessingException {

        if (bot.isInJail()) {
            handleJailTurn(bot);   // unten ändern wir das ebenfalls
            return;
        }

        log.info(() -> "Bot-Turn für " + bot.getName());

        /* 1 ─ Würfeln + Positionslogik einmalig im Model */
        Game.DiceRollResult res = game.handleDiceRoll(bot.getId());
        int     roll     = res.roll();
        boolean pasch    = res.pasch();
        boolean passedGo = res.passedGo();

        /* 2 ─ Broadcast des Wurfs */
        ObjectNode rollMsg = mapper.createObjectNode();
        rollMsg.put("type",     "DICE_ROLL");
        rollMsg.put("playerId", bot.getId());
        rollMsg.put("value",    roll);
        rollMsg.put("manual",   false);
        rollMsg.put("isPasch",  pasch);
        cb.broadcast(mapper.writeValueAsString(rollMsg));

        log.info(() -> " → Würfel: " + roll +
                " (" + (pasch ? "Pasch" : "Summe") + ")");

        /* 3 ─ GO-Nachricht */
        if (passedGo) {
            cb.broadcast("SYSTEM: " + bot.getName() +
                    " passed GO and collected €200");
        }

        /* 4 ─ Grundstück kaufen */
        tryBuyCurrentField(bot);

        /* 5 ─ Status + Bankrott-Check */
        bot.setHasRolledThisTurn(true);
        cb.updateGameState();
        cb.checkBankruptcy();

        /* 6 ─ Pasch? = sofort neuer Wurf */
        if (pasch) {
            bot.setHasRolledThisTurn(false);
            cb.updateGameState();
            queueBotTurn(bot.getId());
            return;
        }

        /* 7 ─ Zugende */
        Player next = game.getNextPlayer();
        if (next.isBot()) {
            exec.schedule(() -> {
                cb.advanceToNextPlayer();
                /* Bot startet sich dann selbst */
            }, 1, TimeUnit.SECONDS);
        } else {
            cb.advanceToNextPlayer();
            cb.updateGameState();
        }
    }



    /** Prüft, ob kaufbar, kauft und meldet das. */
    private void tryBuyCurrentField(Player bot) {

        BaseProperty field =
                pts.findPropertyByPosition(bot.getPosition());
        if (field == null || field.getOwnerId() != null) {
            return;                     // nichts zu kaufen
        }

        // 1) Kann/Möchte der Bot kaufen?
        if (!pts.canBuyProperty(bot, field.getId())) {
            return;
        }

        // 2) Kaufen
        boolean bought = pts.buyProperty(bot, field.getId());
        if (!bought) {
            return;                     // Sicherheits-Exit
        }

        // 3. Nachricht an alle schicken
        try {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("type", "PROPERTY_BOUGHT");
            msg.put(
                    "message",
                    "Player " + bot.getName() + " 🤖 bought property " + field.getName()
            );
            cb.broadcast(mapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.severe("Could not broadcast bot purchase");
        }

        // 4) Spielstand sofort aktualisieren
        cb.updateGameState();
    }


    /** Bot-Zug im Gefängnis */
    private void handleJailTurn(Player bot) throws JsonProcessingException {


        Game.DiceRollResult res = game.handleDiceRoll(bot.getId());
        int roll = res.roll();
        boolean pasch = res.pasch();

        ObjectNode msg = mapper.createObjectNode();
        msg.put("type",     "DICE_ROLL");
        msg.put("playerId", bot.getId());
        msg.put("value",    roll);
        msg.put("manual",   false);
        msg.put("isPasch",  pasch);
        cb.broadcast(mapper.writeValueAsString(msg));

        /*  2) Pasch? → sofort frei + normales Weiterziehen */
        if (pasch) {
            bot.setInJail(false);
            bot.setJailTurns(0);

            cb.broadcast("SYSTEM: " + bot.getName() + " 🤖 würfelt Pasch und ist frei!");
            game.updatePlayerPosition(roll, bot.getId());    // normal ziehen
            tryBuyCurrentField(bot);

            cb.updateGameState();
            cb.checkBankruptcy();

            /* noch einmal würfeln, weil Pasch ⇒ Bot bleibt am Zug */
            queueBotTurn(bot.getId());
            return;
        }

        /* ---------- 3) Kein Pasch: Runden-Counter herunterzählen ---------- */
        bot.reduceJailTurns();          // -> 2 … 0

        if (bot.isInJail()) {
            // sitzt weiter (Runde 1 oder 2)
            cb.broadcast("SYSTEM: " + bot.getName() + " 🤖 sitzt im Gefängnis (" +
                    bot.getJailTurns() + " Runde(n) übrig)");
            cb.updateGameState();

            /* Zug beenden: nächster Spieler */
            cb.advanceToNextPlayer();
            planNextBotIfNeeded();      // siehe Hilfsmethode unten
            return;
        }


        bot.setInJail(false);
        game.updatePlayerMoney(bot.getId(), -50);

        cb.broadcast("SYSTEM: " + bot.getName() +
                " 🤖 zahlt €50 Kaution und ist frei!");


        game.updatePlayerPosition(roll, bot.getId());
        tryBuyCurrentField(bot);

        cb.updateGameState();
        cb.checkBankruptcy();

        /* Zug ist vorbei (kein Pasch) → nächster Spieler */
        cb.advanceToNextPlayer();
        planNextBotIfNeeded();
    }

    /* Hilfsmethode: falls der neue Current-Player ein Bot ist → einplanen */
    private void planNextBotIfNeeded() {
        Player next = game.getCurrentPlayer();
        if (next != null && next.isBot()) {
            queueBotTurn(next.getId());
        }
    }


}