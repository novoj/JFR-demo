package cz.novoj.jfrdemo;

import com.linecorp.armeria.server.Server;
import cz.novoj.jfrdemo.event.GuessEvent;
import cz.novoj.jfrdemo.event.TargetNumberEvent;
import cz.novoj.jfrdemo.service.EventStreaming;
import cz.novoj.jfrdemo.service.GuessService;
import cz.novoj.jfrdemo.service.StartRecording;
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
    public static final int TARGET_NUMBER = new Random().nextInt(10) + 1;

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
                .annotatedService(new StartRecording())
                .annotatedService(new EventStreaming())
                .build();

        // register periodic event to record the target number every chunk
        TargetNumberEvent.enableRecording(TARGET_NUMBER);
        // before JVM shutdown, stop the server gracefully
        server.closeOnJvmShutdown();
        // start the server
        server.start().join();
        // inform the user about the server's endpoint
        System.out.println("Try me on http://localhost:8080/guess/{number}");
        System.out.println("Start recording on http://localhost:8080/jfr-record/{durationMillis}");
        System.out.println("Start streaming on http://localhost:8080/jfr-stream/{durationMillis}");
    }

}
