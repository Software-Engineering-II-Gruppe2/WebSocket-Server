package model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;

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
}