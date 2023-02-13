package ru.ancap.scheduler.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.StandardException;

@AllArgsConstructor
@Getter
public class TaskNotFoundException extends RuntimeException {
    
    private final String uuid;
    
}
