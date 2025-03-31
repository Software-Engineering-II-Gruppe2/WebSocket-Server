package model;

import java.util.List;

public interface DiceManagerInterface {
    public int rollDices();
    public List<Integer> getRollHistory();
    public void addDicesToGame(List<Dice> diceList);
}
