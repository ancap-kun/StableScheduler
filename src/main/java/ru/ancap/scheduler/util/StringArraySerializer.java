package ru.ancap.scheduler.util;

import java.util.Base64;

public class StringArraySerializer {

    public static String serialize(String[] array) {
        String[] operated = array.clone();
        for (int i = 0; i < operated.length; i++) {
            operated[i] = new String(Base64.getEncoder().encode(operated[i].getBytes()));
        }
        return String.join(":", operated);
    }

    public static String[] deserialize(String serializedArray) {
        String[] parts = serializedArray.split(":");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = new String(Base64.getDecoder().decode(parts[i].getBytes()));
        }
        return parts;
    }
    
}
