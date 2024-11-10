package cz.novoj.jfrdemo.service;


import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import cz.novoj.jfrdemo.JfrDemoApplication;
import cz.novoj.jfrdemo.event.GuessEvent;

/**
 * The GuessService class provides an HTTP endpoint for users to guess the target number.
 */
public class GuessService {

    /**
     * Provides an HTTP endpoint for users to guess the target number.
     *
     * @param number the number guessed by the user
     * @return a response indicating whether the guess was correct, too high, or too low
     */
    @Get("/guess/{number}")
    public String guess(@Param("number") int number) {
        // record the user's guess as a custom JFR event
        GuessEvent.record(number, JfrDemoApplication.TARGET_NUMBER);

        // provide hints based on the user's guess
        if (number == JfrDemoApplication.TARGET_NUMBER) {
            return "Correct! You've guessed the number. \uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\n";
        } else if (number < JfrDemoApplication.TARGET_NUMBER) {
            return "Higher! ☝️\n";
        } else {
            return "Lower! \uD83D\uDC47\n";
        }
    }
}
