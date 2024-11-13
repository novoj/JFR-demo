package cz.novoj.jfrdemo.event;

import jdk.jfr.*;

/**
 * Represents an event that captures the target number in a Java Flight Recorder (JFR) session.
 *
 * The TargetNumberEvent class is designed to periodically record the target number
 * for the purpose of monitoring and analysis using the JFR (Java Flight Recorder).
 *
 * The event is labeled with "Target Number Reveal Event" and categorized under "JFR Demo".
 * It does not capture stack traces and is periodic in nature.
 */
@Name("TargetNumberEvent")
@Label("Target Number Reveal Event")
@Description("The target number (guessed number) for the JFR demo application.")
@Category("JFR Demo")
@Period()
@StackTrace(false)
public class TargetNumberEvent extends Event {
    /**
     * The target number to be guessed by users in the JFR demo application's guess game.
     */
    @Label("Target Number")
    final int targetNumber;

    public TargetNumberEvent(int targetNumber) {
        this.targetNumber = targetNumber;
    }

    /**
     * Enables the periodic recording of the target number as a custom event in the Java Flight Recorder (JFR).
     *
     * @param targetNumber the target number to be recorded periodically in the JFR session
     */
    public static void enableRecording(int targetNumber) {
        final TargetNumberEvent event = new TargetNumberEvent(targetNumber);
        FlightRecorder.addPeriodicEvent(TargetNumberEvent.class, event::commit);
    }

}
