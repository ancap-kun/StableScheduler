package ru.ancap.scheduler.example;

import lombok.SneakyThrows;
import ru.ancap.scheduler.Scheduler;

public class ExampleTask implements Runnable {

    private Scheduler caller;
    private String uuid;

    @Override
    public void run() {
        int calls = Supplier.database().get();
        if (calls < 5) {
            Supplier.database().increment();
        } else this.caller.cancel(this.uuid);
    }
    
    interface MyDatabase {
        int get();
        void increment();
    }
    
    static class Supplier {
        
        @SneakyThrows
        public static MyDatabase database() {
            return null;
        }
        
    }
    
}
