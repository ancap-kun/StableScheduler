package ru.ancap.scheduler;

import ru.ancap.scheduler.example.ExampleTask;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Class-based scheduler. Should continue executing declared tasks even after JVM reboot. 
 * Implementors may require the load() method to be called in order to recover 
 * tasks after a JVM reboot/other memory reset. <br/> <br/>
 * Task-class should contain zero-argument constructor and implement Runnable interface. 
 * Example of task is here: {@link ExampleTask} (click middle mouse button on highlighted text in IntelliJ IDEA to view) <br/> <br/>
 * Scheduler can provide himself, task UUID and arguments to instantiated tasks. To use it, make this fields in instantiated class: <br/><br/>
 * {@code private Scheduler caller; } <br/>
 * {@code private String uuid; } <br/>
 * {@code private String[] arguments; } <br/><br/>
 * By the time run() of the task object is called, these fields will be filled. <br/> <br/>
 * You can add not every field, but some of them - in this case the Scheduler will provide only declared fields. <br/>
 * You can also make custom fields - but it's not recommended. The Scheduler thinks themselves when Runnable will instantiate. 
 * The Scheduler has the right to create a new object for each task run, or to reuse it several times.
 */
public interface Scheduler {
    
    /**
     * @param task task class
     * @param absolute task execution time in milliseconds since the Unix Epoch
     * @param arguments arguments, that will be provided to task.
     * @return UUID of task
     */
    String once(Class<?> task, long absolute, String... arguments);

    /**
     * @param task task class
     * @param first first execution time in milliseconds since the Unix Epoch
     * @param period period between executions in milliseconds
     * @param arguments arguments, that will be provided to task
     * @return UUID of task
     */
    String repeat(Class<?> task, long first, long period, String... arguments);
    

    /**
     * Cancels next executions of task. This method doesn't interrupt operations, that executes at moment.
     */
    void cancel(String uuid);
    
}