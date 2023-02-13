package ru.ancap.scheduler;

import lombok.*;
import org.jetbrains.annotations.Blocking;
import ru.ancap.scheduler.exception.TaskNotFoundException;
import ru.ancap.scheduler.util.StringArraySerializer;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Blocking
@RequiredArgsConstructor
public class StableScheduler implements Scheduler {

    /**
     * You should provide Connection to a database, where tasks will be stored.
     */
    private final DataSource dataSource;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Runnable> userRunnables = new HashMap<>();
    private final Map<String, Future<?>> cancelPool = new HashMap<>();

    @Getter
    private final Map<String, Task> tasks = new HashMap<>();
    
    
    private static boolean loaded = false;
    
    @Blocking
    @SneakyThrows
    public void load(BiConsumer<String, String> notFoundPolicy) {
        if (StableScheduler.loaded) throw new IllegalStateException(
                "\n"+
                "StableScheduler is already loaded.\n" + 
                "StableScheduler can't be unloaded or loaded in multiple instances on single JVM."
        );
        
        try (PreparedStatement statement = this.dataSource.getConnection().prepareStatement(
                "CREATE TABLE IF NOT EXISTS stable_scheduler_tasks (\n" +
                "uuid VARCHAR(64) PRIMARY KEY,\n" + 
                "task_class VARCHAR(256),\n"+
                "absolute NUMERIC,\n"+ 
                "repeat BOOL,\n"+
                "period NUMERIC,\n"+
                "arguments VARCHAR(1024)\n" +
                ");\n"
        )) {
            statement.execute();
        };

        List<Task> database = this.pullAllFromDatabase(notFoundPolicy);
        for (Task task : database) {
            long currentTimeMillis = System.currentTimeMillis();
            if (task.getAbsolute() < currentTimeMillis) {
                if (!task.isRepeat()) { 
                    this.databaseDelete(task.getUuid()); 
                    continue; 
                } else {
                    long distance = currentTimeMillis - task.getAbsolute();
                    long skipped = distance / task.getPeriod();
                    task = task.withAbsolute(task.getAbsolute() + (task.getPeriod() * (skipped+1)));
                }
            }
            this.jvmSchedule(task);
            this.tasks.put(task.getUuid(), task);
        }
        StableScheduler.loaded = true;
    }

    @Override
    public String once(Class<?> task, long absolute, String... arguments) {
        String uuid = UUID.randomUUID().toString();
        Task taskObject = new Task(uuid, task, absolute, false, 0, arguments);
        this.jvmSchedule(taskObject);
        this.databaseSchedule(taskObject);
        return uuid;
    }

    @Override
    public String repeat(Class<?> task, long first, long period, String... arguments) {
        String uuid = UUID.randomUUID().toString();
        Task taskObject = new Task(uuid, task, first, true, period, arguments);
        this.jvmSchedule(taskObject);
        this.databaseSchedule(taskObject);
        return uuid;
    }

    @Override
    public void cancel(String uuid) {
        this.cancelPool.get(uuid).cancel(false);
        this.databaseDelete(uuid);
    }
    
    private void jvmSchedule(Task task) {
        Runnable runnable = this.prepare(task.getUuid(), task.getTask(), task.getArguments());
        long absolute = task.getAbsolute();
        long current = System.currentTimeMillis();
        long delay = absolute - current;
        if (delay < 0) delay = 0;
        Future<?> future;
        if (task.isRepeat()) {
            future = this.scheduler.scheduleAtFixedRate(
                    runnable,
                    delay,
                    task.getPeriod(),
                    TimeUnit.MILLISECONDS
            );
        } else {
            future = this.scheduler.schedule(
                    runnable,
                    delay,
                    TimeUnit.MILLISECONDS
            );
        }
        this.cancelPool.put(task.getUuid(), future);
    }

    @SneakyThrows
    private void databaseSchedule(Task task) {
        try (PreparedStatement statement = this.dataSource.getConnection().prepareStatement(
                "INSERT INTO stable_scheduler_tasks (uuid, task_class, absolute, repeat, period, arguments) VALUES (?,?,?,?,?,?);\n"
        )) {
            statement.setString(1, task.getUuid());
            statement.setString(2, task.getTask().getName());
            statement.setLong(3, task.getAbsolute());
            statement.setBoolean(4, task.isRepeat());
            statement.setLong(5, task.getPeriod());
            statement.setString(6, StringArraySerializer.serialize(task.getArguments()));
            statement.executeUpdate();
        }
    }

    @SneakyThrows
    private void databaseDelete(String uuid) {
        try (PreparedStatement statement = this.dataSource.getConnection().prepareStatement("DELETE FROM stable_scheduler_tasks WHERE uuid = ?;\n")) {
            statement.setString(1, uuid);
            statement.executeUpdate();
        }
    }

    @SneakyThrows
    private Task pullFromDatabase(String uuid) {
        try (PreparedStatement statement = this.dataSource.getConnection().prepareStatement("SELECT * FROM stable_scheduler_tasks WHERE uuid = ?;\n")) {
            statement.setString(1, uuid);
            ResultSet set = statement.executeQuery();
            if (set.next()) {
                return new Task(
                        set.getString(1),
                        Class.forName(set.getString(2)),
                        set.getLong(3),
                        set.getBoolean(4),
                        set.getLong(5),
                        StringArraySerializer.deserialize(set.getString(6))
                );
            } else {
                throw new TaskNotFoundException(uuid);
            }
        }
        
    }

    @SneakyThrows
    private List<Task> pullAllFromDatabase(BiConsumer<String, String> notFoundPolicy) {
        try (PreparedStatement statement = this.dataSource.getConnection().prepareStatement("SELECT * FROM stable_scheduler_tasks;")) {
            ResultSet set = statement.executeQuery();
            List<Task> tasks = new ArrayList<>();
            while (set.next()) {
                String uuid = null;
                String className = null;
                try {
                    uuid = set.getString(1);
                    className = set.getString(2);
                    tasks.add(new Task(
                            uuid,
                            Class.forName(className),
                            set.getLong(3),
                            set.getBoolean(4),
                            set.getLong(5),
                            StringArraySerializer.deserialize(set.getString(6))
                    ));
                } catch (ClassNotFoundException exception) {
                    assert uuid != null;
                    assert className != null;
                    notFoundPolicy.accept(uuid, className);
                }
            }
            return tasks;
        }
    }
    
    
    private Runnable prepare(String uuid, Class<?> task, String[] arguments) {
        if (this.userRunnables.containsKey(uuid)) return this.userRunnables.get(uuid);
        Runnable instantiated = this.instantiate(uuid, task, arguments);
        this.userRunnables.put(uuid, instantiated);
        return instantiated;
    }

    @SneakyThrows
    private Runnable instantiate(String uuid, Class<?> task, String[] arguments) {
        Runnable runnable = (Runnable) task.getDeclaredConstructor().newInstance();
        this.provideField(runnable, "caller", this);
        this.provideField(runnable, "uuid", uuid);
        this.provideField(runnable, "arguments", arguments);
        return runnable;
    }

    @SneakyThrows
    private void provideField(Object object, String fieldName, Object value) {
        Class<?> class_ = object.getClass();
        try {
            Field field = class_.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (NoSuchFieldException ignored) {}
    }

    @Data
    @With
    private static class Task {
        
        private final String uuid;

        @Getter(AccessLevel.NONE)
        private final Class<?> class_;
        
        private final long absolute;
        
        private final boolean repeat;
        
        /**
         * Must be bigger than 0 for repeat task.
         * Ignored for non-repeat task.
         */
        private final long period; 
        
        private final String[] arguments;
        
        public Class<?> getTask() {
            return class_;
        }
        
    }
    
}
