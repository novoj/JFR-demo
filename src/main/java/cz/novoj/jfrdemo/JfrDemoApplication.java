package cz.novoj.jfrdemo;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.sse.ServerSentEvent;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.ProducesEventStream;
import com.linecorp.armeria.server.streaming.ServerSentEvents;
import cz.novoj.jfrdemo.event.GuessEvent;
import cz.novoj.jfrdemo.event.TargetNumberEvent;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingStream;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.SECONDS;

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

    /**
     * StartRecording is a class that provides functionality to start a Java Flight Recording (JFR) with a specified duration.
     *
     * The class includes an HTTP endpoint that initiates the JFR recording process and saves the recording to disk.
     * It uses specific settings to capture events like object allocation, garbage collection, CPU load, and thread sleep.
     */
    static class StartRecording {
        /**
         * An atomic integer to generate unique recording IDs for each JFR recording.
         */
        private final AtomicInteger recordingId = new AtomicInteger(0);

        @Get("/jfr-record/{durationSeconds}")
        public String startRecording(@Param("durationSeconds") int durationSeconds) throws IOException {
            // create a new recording
            final int recordingId = this.recordingId.incrementAndGet();
            Recording recording = new Recording();

            // record only specific events
            recording.enable("jdk.ObjectAllocationInNewTLAB").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.ObjectAllocationOutsideTLAB").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.GarbageCollection").withThreshold(Duration.ofMillis(10));
            recording.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(500));
            recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
            recording.enable(GuessEvent.class).withoutThreshold();

            final Path destination = Path.of("recording-" + recordingId + ".jfr");
            destination.toFile().delete();
            recording.setName("JFR Demo Recording #" + recordingId);
            recording.setDestination(destination);
            recording.setDuration(Duration.of(durationSeconds, SECONDS));
            recording.setToDisk(true);

            // start the recording
            recording.start();
            return "Recording #" + recordingId + " started\n";
        }

    }

    /**
     * StartRecording is a class that provides functionality to start a Java Flight Recording (JFR) with a specified duration.
     *
     * The class includes an HTTP endpoint that initiates the JFR recording process and saves the recording to disk.
     * It uses specific settings to capture events like object allocation, garbage collection, CPU load, and thread sleep.
     */
    static class EventStreaming {
        private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(4, 4, 1000, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
        private static final ScheduledExecutorService SCHEDULER = new ScheduledThreadPoolExecutor(1);
        private static final BlockingQueue<ServerSentEvent> EVENT_QUEUE = new LinkedBlockingQueue<>();

        @Get(value = "/jfr-stream/{durationSeconds}")
        @ProducesEventStream
        public HttpResponse startStreaming(@Param("durationSeconds") int durationSeconds, ServiceRequestContext ctx) {
            // create a new recording stream
            final RecordingStream stream = new RecordingStream();

            // record only specific events
            stream.enable("jdk.ObjectAllocationInNewTLAB").withThreshold(Duration.ofMillis(1));
            stream.enable("jdk.ObjectAllocationOutsideTLAB").withThreshold(Duration.ofMillis(1));
            stream.enable("jdk.GarbageCollection").withThreshold(Duration.ofMillis(10));
            stream.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(500));
            stream.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
            stream.enable(GuessEvent.class).withoutThreshold();

            // when events are recorded, add them to the event queue
            stream.onEvent(recordedEvent -> {
                final ServerSentEvent event = ServerSentEvent.ofData(recordedEvent.toString());
                EVENT_QUEUE.offer(event);
            });

            // Create a stream from the event queue
            final Stream<ServerSentEvent> sseStream = Stream.generate(() -> {
                try {
                    final ServerSentEvent event = EVENT_QUEUE.take();
                    // we need to return null to stop the stream
                    return "Stream finished.".equals(event.event()) ? null : event;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }).takeWhile(Objects::nonNull);

            // start the streaming
            EXECUTOR.submit(stream::start);
            SCHEDULER.schedule(
                () -> {
                    // finish the recording
                    stream.stop();
                    System.out.println("Stream finished.");
                    stream.close();
                    sseStream.close();
                    EVENT_QUEUE.offer(ServerSentEvent.ofEvent("Stream finished."));
                },
                durationSeconds, TimeUnit.SECONDS
            );

            // and align the request timeout with the recording duration
            ctx.setRequestTimeout(Duration.of(durationSeconds + 10, SECONDS));
            return ServerSentEvents.fromStream(sseStream, EXECUTOR);
        }

    }

}
