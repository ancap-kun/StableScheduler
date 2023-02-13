package ru.ancap.scheduler.support;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@RequiredArgsConstructor
public class StableScheduleSupport implements ScheduleSupport {
    
    private final DataSource dataSource;
    
    @SneakyThrows
    public StableScheduleSupport load() {
        try (PreparedStatement statement = this.dataSource.getConnection().prepareStatement(
                "CREATE TABLE IF NOT EXISTS scheduler_support_data (\n" +
                        "name VARCHAR(64) PRIMARY KEY,\n" +
                        "declared BOOL\n"+
                        ");\n"
        )) {
            statement.execute();
            return this;
        }
    }
    
    @Override
    @SneakyThrows
    public void declare(String name) {
        String query = "INSERT INTO scheduler_support_data (name, declared)\n" +
                       "VALUES (?,?);\n";
        try (PreparedStatement statement = this.dataSource.getConnection().prepareStatement(query)) {
            statement.setString(1, name);
            statement.setBoolean(2, true);
            statement.executeUpdate();
        }
    }

    @Override
    @SneakyThrows
    public boolean isDeclared(String name) {
        String query = "SELECT * FROM scheduler_support_data\n" +
                       "WHERE name = ?;\n";
        try (PreparedStatement statement = this.dataSource.getConnection().prepareStatement(query)) {
            statement.setString(1, name);
            ResultSet result = statement.executeQuery();
            boolean works = result.next() && result.getBoolean(2);
            return works;
        }
    }
}
