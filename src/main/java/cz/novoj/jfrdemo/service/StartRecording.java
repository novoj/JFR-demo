package cz.novoj.jfrdemo.service;


import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import cz.novoj.jfrdemo.event.GuessEvent;
import jdk.jfr.Recording;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * StartRecording is a class that provides functionality to start a Java Flight Recording (JFR) with a specified duration.
 * <p>
 * The class includes an HTTP endpoint that initiates the JFR recording process and saves the recording to disk.
 * It uses specific settings to capture events like object allocation, garbage collection, CPU load, and thread sleep.
 */
public class StartRecording {
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
