package model;

import java.util.ArrayList;
import java.util.List;

public class DiceManager implements DiceManagerInterface {
    private static List<Dice> dices;
    private static List<Integer> rollHistory;
    private List<Integer> lastRollValues;

    public DiceManager() {
        dices = new ArrayList<>();
        rollHistory = new ArrayList<>();
    }

    public void initializeStandardDices() {
        dices.clear();
        rollHistory.clear();
        Dice firstDice = new Dice(6);
        Dice secondDice = new Dice(6);
        dices.add(firstDice);
        dices.add(secondDice);
    }

    public void addDicesToGame(List<Dice> diceList){
        for (Dice dice : diceList) {
            dices.add(dice);
        }
    }

    @Override
    public int rollDices() {
        int rollResult = 0;
        lastRollValues = new ArrayList<>();
        for (Dice dice : dices) {
            int value = dice.roll();
            lastRollValues.add(value);
            rollResult += value;
        }
        rollHistory.add(rollResult);
        return rollResult;
    }

    public List<Integer> getLastRollValues() {
        return lastRollValues;
    }

        @Override
        public boolean isPasch() {
            return lastRollValues != null &&
                    lastRollValues.size() == 2 &&
                    lastRollValues.get(0).equals(lastRollValues.get(1));
        }

    @Override
    public List<Integer> getRollHistory() {
        return rollHistory;
    }

}
