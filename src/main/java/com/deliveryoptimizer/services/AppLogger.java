package com.deliveryoptimizer.services;

import com.deliveryoptimizer.datastructures.DoublyLinkedList;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AppLogger {

    public enum LogLevel {
        INFO("#318BFF"),
        SUCCESS("#35D98A"),
        WARNING("#FFB547"),
        ERROR("#FF5364");

        private final String colorHex;
        LogLevel(String colorHex) { this.colorHex = colorHex; }
        public String getColorHex() { return colorHex; }
    }

    public static class LogEntry {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
        private final LocalTime timestamp = LocalTime.now();
        private final LogLevel level;
        private final String source;
        private final String message;

        public LogEntry(LogLevel level, String source, String message) {
            this.level = level != null ? level : LogLevel.INFO;
            this.source = source != null ? source : "System";
            this.message = message != null ? message : "";
        }

        public String getFormattedTimestamp() { return timestamp.format(FMT); }
        public LogLevel getLevel() { return level; }
        public String getSource() { return source; }
        public String getMessage() { return message; }
    }

    public interface LogListener {
        void onLogPublished(LogEntry entry);
    }

    private static final AppLogger INSTANCE = new AppLogger();
    private final DoublyLinkedList<LogEntry> history = new DoublyLinkedList<>();
    private final List<LogListener> listeners = new CopyOnWriteArrayList<>();

    private AppLogger() {}

    public static AppLogger getInstance() { return INSTANCE; }
    public void addListener(LogListener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }

    public void log(LogLevel level, String source, String message) {
        LogEntry entry = new LogEntry(level, source, message);
        synchronized (history) {
            history.addLast(entry);
            while (history.size() > 1200) history.removeFirst();
        }
        for (LogListener l : listeners) l.onLogPublished(entry);
    }

    public void info(String src, String msg) { log(LogLevel.INFO, src, msg); }
    public void success(String src, String msg) { log(LogLevel.SUCCESS, src, msg); }
    public void warning(String src, String msg) { log(LogLevel.WARNING, src, msg); }
    public void error(String src, String msg) { log(LogLevel.ERROR, src, msg); }

    public List<LogEntry> getHistorySnapshot() {
        synchronized (history) {
            List<LogEntry> copy = new ArrayList<>(history.size());
            for (LogEntry e : history) copy.add(e);
            return copy;
        }
    }

    public void clear() {
        synchronized (history) { history.clear(); }
    }
}
