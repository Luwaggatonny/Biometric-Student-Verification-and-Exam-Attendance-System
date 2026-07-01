package com.uict.bioverify.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    // In-memory list of audit log entries, thread-safe
    private final List<AuditLogEntry> auditLogs = new CopyOnWriteArrayList<>();
    private static final int MAX_LOGS = 200;

    public void logEvent(String category, String description) {
        AuditLogEntry entry = new AuditLogEntry(category, description);
        
        // Keep list size within limit
        if (auditLogs.size() >= MAX_LOGS) {
            auditLogs.remove(0);
        }
        auditLogs.add(entry);

        // Also write to standard logs
        logger.info("[AUDIT] [{}] - {}", category, description);
    }

    public List<AuditLogEntry> getLogs() {
        // Return a copy, reversed (latest first)
        List<AuditLogEntry> reversed = new ArrayList<>();
        for (int i = auditLogs.size() - 1; i >= 0; i--) {
            reversed.add(auditLogs.get(i));
        }
        return reversed;
    }

    public static class AuditLogEntry {
        private final String timestamp;
        private final String category; // AUTH, REGISTER, SCANNER, VERIFICATION, ATTENDANCE
        private final String description;

        public AuditLogEntry(String category, String description) {
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            this.category = category;
            this.description = description;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }
    }
}
