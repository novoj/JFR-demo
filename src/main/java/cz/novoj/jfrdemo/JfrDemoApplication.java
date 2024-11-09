package cz.novoj.jfrdemo;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import cz.novoj.jfrdemo.event.GuessEvent;
import cz.novoj.jfrdemo.event.TargetNumberEvent;
import jdk.jfr.FlightRecorder;

import java.util.Random;

/**
 * The JfrDemoApplication class is the entry point for the JFR (Java Flight Recorder) demo application.
 * It initializes a server and registers custom JFR events (TargetNumberEvent and GuessEvent).
 * The application exposes an HTTP endpoint for users to guess a randomly generated target number.
 *
 * The GuessService class provides the endpoint "/guess/{number}", which accepts a number as a path parameter.
 * It responds with hints ("Higher!", "Lower!" or "Correct!") based on the guessed number compared to the target number.
 *
 * This application also demonstrates the use of periodic events and recording user actions with JFR.
 */
public class JfrDemoApplication {
    /**
     * The target number to be guessed by users in the JFR demo application's guess game.
     * This number is randomly generated within the range of 1 to 10 at the start of the application and remains constant.
     */
    private static final int TARGET_NUMBER = new Random().nextInt(10) + 1;

    /**
     * Registers the custom JFR events (TargetNumberEvent and GuessEvent) with the FlightRecorder so that they are
     * included in the JFR recordings.
     */
    static {
        FlightRecorder.register(TargetNumberEvent.class);
        FlightRecorder.register(GuessEvent.class);
    }

    /**
     * The main method serves as the entry point for the JFR (Java Flight Recorder) demo application.
     * It initializes and starts the Armeria HTTP server on port 8080 that provides an endpoint
     * for users to guess a randomly generated target number.
     * The method also sets up custom JFR events for monitoring and shuts down the server gracefully on JVM shutdown.
     *
     * @param args Command line arguments (not used in this application).
     */
    public static void main(String[] args) {
        final Server server = Server.builder()
                .http(8080)
                .annotatedService(new GuessService())
                .build();

        // register periodic event to record the target number every chunk
        TargetNumberEvent.enableRecording(TARGET_NUMBER);
        // before JVM shutdown, stop the server gracefully
        server.closeOnJvmShutdown();
        // start the server
        server.start().join();
        // inform the user about the server's endpoint
        System.out.println("Try me on http://localhost:8080/guess/{number}");
    }

    /**
     * The GuessService class provides an HTTP endpoint for users to guess the target number.
     */
    static class GuessService {

        /**
         * Provides an HTTP endpoint for users to guess the target number.
         *
         * @param number the number guessed by the user
         * @return a response indicating whether the guess was correct, too high, or too low
         */
        @Get("/guess/{number}")
        public String guess(@Param("number") int number) {
            // record the user's guess as a custom JFR event
            GuessEvent.record(number, TARGET_NUMBER);

            // provide hints based on the user's guess
            if (number == TARGET_NUMBER) {
                return "Correct! You've guessed the number. \uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\n";
            } else if (number < TARGET_NUMBER) {
                return "Higher! ☝️\n";
            } else {
                return "Lower! \uD83D\uDC47\n";
            }
        }
    }

}
