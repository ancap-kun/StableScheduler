package ru.ancap.scheduler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.ancap.scheduler.util.StringArraySerializer;

public class ArraySerializationTest {

    @Test
    public void test() {
        String[] input = {"some:value", "other", ":::::::::::::::::::::", "", "\\", ":"};
        String serialized = StringArraySerializer.serialize(input);
        String[] deserialized = StringArraySerializer.deserialize(serialized);
        Assertions.assertArrayEquals(deserialized, input);
    }
    
}
