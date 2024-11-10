package cz.novoj.jfrdemo.service;


import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.sse.ServerSentEvent;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.ProducesEventStream;
import com.linecorp.armeria.server.streaming.ServerSentEvents;
import cz.novoj.jfrdemo.event.GuessEvent;
import jdk.jfr.consumer.RecordingStream;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * StartRecording is a class that provides functionality to start a Java Flight Recording (JFR) with a specified duration.
 *
 * The class includes an HTTP endpoint that initiates the JFR recording process and saves the recording to disk.
 * It uses specific settings to capture events like object allocation, garbage collection, CPU load, and thread sleep.
 */
public class EventStreaming {
    /**
     * Executor manages the streaming and also the propagation of events to the client.
     */
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            4, 4, 1000, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10)
    );
    /**
     * Scheduler manages the duration of the recording.
     */
    private static final ScheduledExecutorService SCHEDULER = new ScheduledThreadPoolExecutor(1);
    /**
     * Event queue stores the events that are recorded during the JFR session.
     */
    private static final BlockingQueue<ServerSentEvent> EVENT_QUEUE = new LinkedBlockingQueue<>();
    /**
     * Message to indicate that the stream has finished.
     */
    private static final String STREAM_FINISHED_MESSAGE = "Stream finished.";

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
                return STREAM_FINISHED_MESSAGE.equals(event.event()) ? null : event;
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
                    EVENT_QUEUE.offer(ServerSentEvent.ofEvent(STREAM_FINISHED_MESSAGE));
                    System.out.println(STREAM_FINISHED_MESSAGE);
                    stream.close();
                },
                durationSeconds, TimeUnit.SECONDS
        );

        // and align the request timeout with the recording duration
        ctx.setRequestTimeout(Duration.of(durationSeconds + 10, SECONDS));
        return ServerSentEvents.fromStream(sseStream, EXECUTOR);
    }

}
