package cz.novoj.jfrdemo.event;

import jdk.jfr.*;

import java.util.Set;

/**
 * Represents a user guess event in a Java Flight Recorder (JFR) session.
 *
 * The GuessEvent class logs the user's guess and the deviation percentage from the target number.
 * It is labeled as "User Guess Event" and categorized under "JFR Demo".
 * It uses the stack trace for the event context.
 */
@Name("UserGuessEvent")
@Label("User Guess Event")
@Category("JFR Demo")
@StackTrace()
public class GuessEvent extends Event {

    /**
     * The user's guess in the JFR demo application's guess game.
     */
    @Label("User Guess")
    final int guess;

    /**
     * The deviation percentage of the user's guess from the target number.
     */
    @Label("Deviation percent")
    final int deviationPercent;

    public GuessEvent(int guess, int targetNumber) {
        this.guess = guess;
        int difference = Math.abs(targetNumber - guess);
        double percentageDifference = (double) difference / targetNumber * 100;
        this.deviationPercent = (int) percentageDifference;
    }

    /**
     * Filters events based on the deviation percentage threshold. If the deviation percentage is greater than
     * the threshold, the event is filtered out.
     *
     * @param filter the DeviationThresholdControl instance containing the threshold for filtering
     * @return true if the deviation percentage is greater than the threshold in the filter, false otherwise
     */
    @SettingDefinition
    @Name("deviationFilter")
    @Label("Deviation Filter")
    public boolean filter(DeviationThresholdControl filter) {
        return filter.getThreshold() < this.deviationPercent;
    }

    /**
     * Records the user's guess event if the event is enabled.
     *
     * @param number the number guessed by the user
     * @param targetNumber the target number that the user is trying to guess
     */
    public static void record(int number, int targetNumber) {
        GuessEvent event = new GuessEvent(number, targetNumber);
        if (event.isEnabled()) {
            event.commit();
        }
    }

    /**
     * A control mechanism for managing deviation threshold settings.
     * This setting control is used to define the threshold for filtering
     * events based on deviation percentage.
     */
    public static class DeviationThresholdControl extends SettingControl {
        private int threshold = 0;  // Default threshold

        public int getThreshold() {
            return threshold;
        }

        @Override
        public String combine(Set<String> settingValues) {
            return settingValues.stream().map(Integer::parseInt).min(Integer::compareTo).orElse(0).toString();
        }

        @Override
        public void setValue(String settingValue) {
            threshold = Integer.parseInt(settingValue);
        }

        @Override
        public String getValue() {
            return Integer.toString(threshold);
        }
    }

}
