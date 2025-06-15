package model;

import model.cards.MoveCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Optional;

class GameTest {
    private Game game;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        game = new Game();
        player1 = new Player("player1", "Player 1");
        player2 = new Player("player2", "Player 2");
    }

    @Test
    void testAddPlayer() {
        game.addPlayer("1", "Player 1");
        game.addPlayer("2", "Player 2");

        assertThat(game.getPlayers()).hasSize(2);
        assertThat(game.getPlayers().get(0).getName()).isEqualTo("Player 1");
        assertThat(game.getPlayers().get(1).getName()).isEqualTo("Player 2");
    }

    @Test
    void testPlayerMoney() {
        game.addPlayer("1", "Player 1");
        Player player = game.getPlayers().get(0);

        assertThat(player.getMoney()).isEqualTo(1500); // Starting money

        player.addMoney(500);
        assertThat(player.getMoney()).isEqualTo(2000);

        player.subtractMoney(300);
        assertThat(player.getMoney()).isEqualTo(1700);
    }

    @Test
    void removeNonExistingPlayerDoesNothing() {
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.setCurrentPlayerIndex(0);

        game.removePlayer("Z"); // no such player

        // players unchanged
        assertThat(game.getPlayers())
                .extracting(Player::getId)
                .containsExactly("A", "B");
        // index unchanged
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("A");
    }

    @Test
    void removePlayerBeforeCurrentShiftsIndexDown() {
        // players: [A, B, C, D], current = C
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.addPlayer("C", "Carol");
        game.addPlayer("D", "Dave");
        game.setCurrentPlayerIndex(2);

        game.removePlayer("B");

        // now players [A, C, D], so C moves to idx=1
        assertThat(game.getPlayers()).extracting(Player::getId)
                .containsExactly("A", "C", "D");
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("C");
    }

    @Test
    void removePlayerAfterCurrentKeepsIndex() {
        // players: [A, B, C, D], current = B
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.addPlayer("C", "Carol");
        game.addPlayer("D", "Dave");
        game.setCurrentPlayerIndex(1);

        game.removePlayer("D");

        // players [A, B, C], current still B at idx=1
        assertThat(game.getPlayers()).extracting(Player::getId)
                .containsExactly("A", "B", "C");
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("B");
    }

    @Test
    void removeCurrentPlayerNotLastHandsTurnToNext() {
        // players: [A, B, C], current = B
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.addPlayer("C", "Carol");
        game.setCurrentPlayerIndex(1);
        game.getPlayers().get(2).setHasRolledThisTurn(true);

        game.removePlayer("B"); // remove current

        // new players [A, C]; currentIndex remains 1 → points at C
        assertThat(game.getPlayers()).extracting(Player::getId)
                .containsExactly("A", "C");
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("C");
        assertThat(game.getCurrentPlayer().hasRolledThisTurn()).isFalse();
    }

    @Test
    void removeCurrentPlayerLastWrapsIndexToZero() {
        // players: [A, B, C], current = C
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.addPlayer("C", "Carol");
        game.setCurrentPlayerIndex(2);
        game.getPlayers().get(0).setHasRolledThisTurn(true);

        game.removePlayer("C"); // remove last

        // players [A, B]; wrap currentIndex to 0 → A's turn
        assertThat(game.getPlayers()).extracting(Player::getId)
                .containsExactly("A", "B");
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("A");
        assertThat(game.getCurrentPlayer().hasRolledThisTurn()).isFalse();
    }


    @Test
    void testPlayerTurnOrder() {
        game.addPlayer("1", "Player 1");
        game.addPlayer("2", "Player 2");
        game.addPlayer("3", "Player 3");

        assertThat(game.getCurrentPlayer().getName()).isEqualTo("Player 1");

        game.nextPlayer();
        assertThat(game.getCurrentPlayer().getName()).isEqualTo("Player 2");

        game.nextPlayer();
        assertThat(game.getCurrentPlayer().getName()).isEqualTo("Player 3");

        game.nextPlayer();
        assertThat(game.getCurrentPlayer().getName()).isEqualTo("Player 1"); // Should wrap around
    }

    @Test
    void testGetPlayerInfo() {
        game.addPlayer("1", "Player 1");
        game.addPlayer("2", "Player 2");

        var playerInfo = game.getPlayerInfo();

        assertThat(playerInfo).hasSize(2);
        assertThat(playerInfo.get(0).getName()).isEqualTo("Player 1");
        assertThat(playerInfo.get(1).getName()).isEqualTo("Player 2");
        assertThat(playerInfo.get(0).getMoney()).isEqualTo(1500);
        assertThat(playerInfo.get(1).getMoney()).isEqualTo(1500);
    }

    @Test
    void testUpdatePlayerMoney() {
        game.addPlayer("1", "Player 1");
        game.addPlayer("2", "Player 2");

        // Test adding money
        game.updatePlayerMoney("1", 500);
        assertThat(game.getPlayers().get(0).getMoney()).isEqualTo(2000); // 1500 + 500

        // Test subtracting money
        game.updatePlayerMoney("1", -300);
        assertThat(game.getPlayers().get(0).getMoney()).isEqualTo(1700); // 2000 - 300

        // Test zero amount (should not change money)
        game.updatePlayerMoney("1", 0);
        assertThat(game.getPlayers().get(0).getMoney()).isEqualTo(1700);

        // Test non-existent player ID (should not throw exception)
        game.updatePlayerMoney("999", 100);
        assertThat(game.getPlayers()).hasSize(2); // Should still have 2 players
    }

    @Test
    void testRemovePlayerWorks(){
        game.addPlayer("1", "Player 1");
        game.addPlayer("2", "Player 2");
        game.removePlayer("1");
        assertThat(game.getPlayers()).hasSize(1);
        assertThat(game.getPlayers().get(0).getName()).isEqualTo("Player 2");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void testUpdatePlayerPositionUpdatesCorrectPlayer(int turn) {
        game.addPlayer("1", "Player 1");
        game.addPlayer("2", "Player 2");

        game.updatePlayerPosition(turn, "1");
        assertEquals(turn, game.getPlayerById("1").get().getPosition());
        assertEquals(0, game.getPlayerById("2").get().getPosition());
    }

    @Test
    void testUpdatePlayerPositionReturnsCorrectBoolean(){
        game.addPlayer("1", "Player 1");
        boolean returnValue = game.updatePlayerPosition(25, "1");
        assertFalse(returnValue);
        returnValue = game.updatePlayerPosition(14, "1");
        assertFalse(returnValue);
        returnValue = game.updatePlayerPosition(1, "1");
        assertTrue(returnValue);
    }

    @ParameterizedTest
    @ValueSource(ints = {39, 40, 41, 60, 79, 80, 81, 500})
    void testUpdatePlayerPositionValueCannotBeGreaterThanFourty(int roll){
        game.addPlayer("1", "Player 1");
        game.updatePlayerPosition(roll, "1");
        assertThat(game.getPlayerById("1").get().getPosition()).isLessThan(40);
    }

    @Test
    void testUpdatePlayerPositionHandlesOverflowCorrectly(){
        game.addPlayer("1", "Player 1");
        game.updatePlayerPosition(39, "1");
        assertEquals(39, game.getPlayerById("1").get().getPosition());
        game.updatePlayerPosition(1, "1");
        assertEquals(0, game.getPlayerById("1").get().getPosition());
        game.updatePlayerPosition(50, "1");
        assertEquals(10, game.getPlayerById("1").get().getPosition());
    }

    @Test
    void givenEmptyPlayersList_whenCheckingPlayerTurn_thenShouldReturnFalse() {
        // Arrange
        // Game is already empty from setUp

        // Act & Assert
        assertFalse(game.isPlayerTurn("anyPlayerId"));
    }

    @Test
    void givenCurrentPlayer_whenCheckingPlayerTurn_thenShouldReturnTrue() {
        // Arrange
        game.addPlayer(player1.getId(), player1.getName());
        game.addPlayer(player2.getId(), player2.getName());

        // Act & Assert
        assertTrue(game.isPlayerTurn(player1.getId()));
    }

    @Test
    void givenNonCurrentPlayer_whenCheckingPlayerTurn_thenShouldReturnFalse() {
        // Arrange
        game.addPlayer(player1.getId(), player1.getName());
        game.addPlayer(player2.getId(), player2.getName());

        // Act & Assert
        assertFalse(game.isPlayerTurn(player2.getId()));
    }

    @Test
    void givenInvalidCurrentPlayerIndex_whenCheckingPlayerTurn_thenShouldReturnFalse() throws Exception {
        // Arrange
        game.addPlayer(player1.getId(), player1.getName());
        
        // Use reflection to set an invalid currentPlayerIndex
        Field currentPlayerIndexField = Game.class.getDeclaredField("currentPlayerIndex");
        currentPlayerIndexField.setAccessible(true);
        currentPlayerIndexField.set(game, 999); // Set to an index way beyond the players list size

        // Act & Assert
        assertFalse(game.isPlayerTurn(player1.getId()));
    }

    @Test
    void testPassingGoAddsMoney() {
        // Arrange
        game.addPlayer("1", "Player 1");
        Player player = game.getPlayers().get(0);
        int initialMoney = player.getMoney();

        // Act - Move player to position 39 (one before GO)
        game.updatePlayerPosition(39, "1");
        // Move player 1 step to pass GO
        boolean passedGo = game.updatePlayerPosition(1, "1");

        // Assert
        assertTrue(passedGo);
        assertEquals(initialMoney + 200, player.getMoney());
    }

    @Test
    void testPassingGoMultipleTimes() {
        // Arrange
        game.addPlayer("1", "Player 1");
        Player player = game.getPlayers().get(0);
        int initialMoney = player.getMoney();

        // Act - Move player around the board multiple times
        game.updatePlayerPosition(40, "1"); // First pass
        game.updatePlayerPosition(40, "1"); // Second pass
        game.updatePlayerPosition(40, "1"); // Third pass

        // Assert
        assertEquals(initialMoney + (200 * 3), player.getMoney());
    }

    @Test
    void testNotPassingGoDoesNotAddMoney() {
        // Arrange
        game.addPlayer("1", "Player 1");
        Player player = game.getPlayers().get(0);
        int initialMoney = player.getMoney();

        // Act - Move player without passing GO
        boolean passedGo = game.updatePlayerPosition(10, "1");

        // Assert
        assertFalse(passedGo);
        assertEquals(initialMoney, player.getMoney());
    }

    @Test
    void testPassingGoWithLargeRoll() {
        // Arrange
        game.addPlayer("1", "Player 1");
        Player player = game.getPlayers().get(0);
        int initialMoney = player.getMoney();

        // Act - Move player with a large roll that passes GO
        boolean passedGo = game.updatePlayerPosition(50, "1");

        // Assert
        assertTrue(passedGo);
        assertEquals(initialMoney + 200, player.getMoney());
        assertEquals(10, player.getPosition()); // Should wrap around to position 10
    }

    @Test
    void testEndGameDurationCalculation() {
        Game gameDurationCalc = new Game();
        gameDurationCalc.start();

        int duration = gameDurationCalc.endGame("player1");

        assertTrue(duration >= 0);
    }

    @Test
    void testDetermineWinnerWithEqualMoney() {
        Game gameWinnerEqualMoney = new Game();
        gameWinnerEqualMoney.addPlayer("1", "Player1");
        gameWinnerEqualMoney.addPlayer("2", "Player2");

        gameWinnerEqualMoney.updatePlayerMoney("1", -500);
        gameWinnerEqualMoney.updatePlayerMoney("2", -500);

        String winner = gameWinnerEqualMoney.determineWinner();
        assertNotNull(winner);
    }

    @Test
    void testSendToJail() {
        game.addPlayer("p1", "Player 1");
        game.addPlayer("p2", "Player 2");

        game.sendToJail("p1");
        Optional<Player> jailedPlayer = game.getPlayerById("p1");

        assertTrue(jailedPlayer.isPresent());
        assertTrue(jailedPlayer.get().isInJail());
        assertEquals(10, jailedPlayer.get().getPosition());
        assertEquals(2, jailedPlayer.get().getJailTurns());

        Optional<Player> freePlayer = game.getPlayerById("p2");
        assertTrue(freePlayer.isPresent());
        assertFalse(freePlayer.get().isInJail());
    }

    @Test
    void testSendToJailInvalidPlayer() {
        game.addPlayer("p1", "Player 1");
        game.sendToJail("invalid_id");

        Optional<Player> player = game.getPlayerById("p1");
        assertTrue(player.isPresent());
        assertFalse(player.get().isInJail());
    }

    @Test
    void testJailCardLogic() {
        MoveCard jailCard = new MoveCard();
        jailCard.setField(10); // Jail position

        game.addPlayer("p1", "Player 1");
        jailCard.apply(game, "p1");

        Optional<Player> player = game.getPlayerById("p1");
        assertTrue(player.isPresent());
        assertTrue(player.get().isInJail());
        assertEquals(10, player.get().getPosition());
        assertEquals(2, player.get().getJailTurns());
    }

  @Test
  void testGiveUpCurrentPlayerRemovedAdvancesTurnCorrectly() {
        // Arrange: Player B's turn
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.addPlayer("C", "Carol");
        game.setCurrentPlayerIndex(1);
        game.getCurrentPlayer().setHasRolledThisTurn(true);

        // Act: B gives up
        game.giveUp("B");

        // Assert: players list is now [A, C]
        assertThat(game.getPlayers())
                .extracting(Player::getId)
                .containsExactly("A", "C");
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("C");
        assertThat(game.getCurrentPlayer().hasRolledThisTurn()).isFalse();
    }

    @Test
    void testGiveUpNonCurrentBeforeIndexShiftsTurnBack() {
        // Arrange: Player C’s turn
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.addPlayer("C", "Carol");
        game.setCurrentPlayerIndex(2);

        // Act: B gives up
        game.giveUp("B");

        // Assert: players now [A, C]
        assertThat(game.getPlayers())
                .extracting(Player::getId)
                .containsExactly("A", "C");
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("C");
    }

    @Test
    void testGiveUpNonCurrentAfterIndexLeavesTurnAlone() {
        // Arrange: Player A's turn
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.addPlayer("C", "Carol");
        game.setCurrentPlayerIndex(0);

        // Act: C gives up
        game.giveUp("C");

        // Assert: players now [A, B]
        assertThat(game.getPlayers())
                .extracting(Player::getId)
                .containsExactly("A", "B");
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("A");
    }

    @Test
    void testGiveUpLastRemainingPlayer() {
        // Arrange: Player A’s turn
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "Bob");
        game.setCurrentPlayerIndex(0);

        // Act: A gives up
        game.giveUp("A");

        // Assert: only B remains
        assertThat(game.getPlayers())
                .extracting(Player::getId)
                .containsExactly("B");
        assertThat(game.getCurrentPlayer().getId()).isEqualTo("B");
    }
    @Test
    void testReplaceDisconnectedWithBot() {
        game.addPlayer("A", "Alice");
        assertFalse(game.getPlayerById("A").get().isBot());

        game.markPlayerDisconnected("A");
        game.replaceDisconnectedWithBot("A");

        Optional<Player> bot = game.getPlayerById("A");
        assertTrue(bot.isPresent());
        assertTrue(bot.get().isBot());
    }


    @Test
    void testReconnectMarksPlayerAsConnected() {
        game.addPlayer("A", "Alice");
        game.markPlayerDisconnected("A");

        // Spieler sollte jetzt nicht mehr verbunden sein
        assertFalse(game.getPlayerById("A").get().isConnected());

        game.markPlayerConnected("A");

        // Spieler ist wieder verbunden
        assertTrue(game.getPlayerById("A").get().isConnected());
    }



    @Test
    void testStartBotTurnSkipsToNextIfBot() {
        game.addPlayer("A", "Alice");
        game.addPlayer("B", "BotB");
        game.getPlayerById("B").get().setBot(true);

        game.setCurrentPlayerIndex(1); // Bot ist dran
        game.nextPlayer();

        assertEquals("A", game.getCurrentPlayer().getId());
    }

}