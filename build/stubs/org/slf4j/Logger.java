package org.slf4j;
public interface Logger {
    void info(String msg);
    void info(String format, Object... args);
    void warn(String msg);
    void warn(String format, Object... args);
    void error(String msg);
    void error(String format, Object... args);
}
