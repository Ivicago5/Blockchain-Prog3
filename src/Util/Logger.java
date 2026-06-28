package Util;

import java.text.SimpleDateFormat;

public class Logger {

    private static final String RED = "\u001B[31m";
    private static final String GREEN =  "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String RESET = "\u001B[0m";

    private static final SimpleDateFormat dataFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public enum logLevel {
        Debug,
        Warn,
        Error,
        Info
    };
    private static synchronized void log(String message, logLevel level){
        String date = dataFormat.format(System.currentTimeMillis());
        String name = Thread.currentThread().getName();
        String messagePrefix = "[" + date + "][" + name + "] " + level + " : ";

        switch (level) {
            case Debug -> messagePrefix = BLUE + messagePrefix + RESET;
            case Info -> messagePrefix = GREEN + messagePrefix + RESET;
            case Warn -> messagePrefix = YELLOW + messagePrefix + RESET;
            case Error -> messagePrefix = RED + messagePrefix + RESET;
        }

        System.out.println(messagePrefix + message);
    }

    public static void info(String message) {
        log(message, logLevel.Info);
    }
    public static void warn(String message) {
        log(message, logLevel.Warn);
    }
    public static void error(String message) {
        log(message, logLevel.Error);
    }
    public static void debug(String message) {
        log(message, logLevel.Debug);
    }
}

