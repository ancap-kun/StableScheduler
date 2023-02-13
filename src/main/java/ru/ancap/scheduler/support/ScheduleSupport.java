package ru.ancap.scheduler.support;

import ru.ancap.scheduler.Scheduler;

import java.util.function.Consumer;

/**
 * Support for running periodic tasks. Due to the fact, that you have to schedule periodic tasks
 * only once, you can use it to check, does you already schedules your task or no.
 */
public interface ScheduleSupport {
    
    void declare(String name);
    boolean isDeclared(String name);

    /**
     * Executes runnable and declares task, if not declared already.
     */
    default void upreg(String name, Runnable runnable) {
        if (this.isDeclared(name)) return;
        runnable.run();
        this.declare(name);
    }
    
}
