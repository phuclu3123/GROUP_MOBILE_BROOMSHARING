package vn.edu.ueh.broomsharing.payment;

import java.nio.file.*;
import java.util.*;

// Cấu hình riêng của server; biến môi trường ghi đè file .env trong thư mục backend.
public final class Config {
    private final Map<String, String> values = new HashMap<>();

    public Config(Path path) throws Exception {
        if (Files.isRegularFile(path)) {
            for (String line : Files.readAllLines(path)) {
                String[] pair = line.trim().replaceFirst("^export ", "").split("=", 2);
                if (pair.length != 2 || pair[0].startsWith("#")) continue;
                String value = pair[1].trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'"))))
                    value = value.substring(1, value.length() - 1);
                values.put(pair[0].trim(), value);
            }
        }
        values.putAll(System.getenv());
    }

    public String get(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    public String required(String key) {
        String value = get(key, "");
        if (value.isBlank()) throw new IllegalStateException("Thiếu cấu hình server: " + key);
        return value;
    }
}
