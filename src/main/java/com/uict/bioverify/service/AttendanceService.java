package com.uict.bioverify.service;

import com.uict.bioverify.model.AttendanceLog;
import com.uict.bioverify.model.ExamSession;
import com.uict.bioverify.model.Student;
import com.uict.bioverify.repository.AttendanceLogRepository;
import com.uict.bioverify.repository.StudentRepository;
import com.uict.bioverify.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AttendanceService {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceLogRepository attendanceLogRepository;
    private final StudentRepository studentRepository;
    private final AuditService auditService;

    public AttendanceService(AttendanceLogRepository attendanceLogRepository, 
                             StudentRepository studentRepository, 
                             AuditService auditService) {
        this.attendanceLogRepository = attendanceLogRepository;
        this.studentRepository = studentRepository;
        this.auditService = auditService;
    }

    // Record verification attempt and log attendance using manual transactions
    public AttendanceLog recordVerification(Student student, ExamSession session, int matchScore, boolean verified, boolean feesHold) {
        String status = "FAILED";
        if (verified) {
            status = "VERIFIED";
        } else if (feesHold) {
            status = "FEES_HOLD";
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Begin manual transaction

            // Check for duplicate attendance in the same session
            Optional<AttendanceLog> existingLog = attendanceLogRepository.findByStudentAndSession(conn, student.getStudentId(), session.getSessionId());
            if (existingLog.isPresent()) {
                auditService.logEvent("ATTENDANCE", "Duplicate attendance attempt by student " + student.getRegNumber() + " for paper " + session.getPaperCode());
                throw new IllegalStateException("Student has already attempted verification for this session.");
            }

            // Save attendance log
            AttendanceLog log = new AttendanceLog(student.getStudentId(), session.getSessionId(), matchScore, status);
            log.setVerificationTime(LocalDateTime.now());
            AttendanceLog savedLog = attendanceLogRepository.save(conn, log);

            // Update student's last verified date
            student.setLastVerifiedDate(LocalDateTime.now());
            studentRepository.update(conn, student);

            // Log to security audit
            auditService.logEvent("VERIFICATION", String.format(
                "Student %s (%s) verified for %s. Score: %d. Status: %s",
                student.getFullName(), student.getRegNumber(), session.getPaperCode(), matchScore, status
            ));

            conn.commit(); // Transaction committed successfully
            return savedLog;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    logger.warn("Transaction failed. Performing rollback.");
                    conn.rollback(); // Transaction rollback on failure
                } catch (SQLException ex) {
                    logger.error("Failed to rollback transaction", ex);
                }
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Database error recording verification: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                DatabaseConnection.releaseConnection(conn);
            }
        }
    }

    public List<AttendanceLog> getSessionLogs(ExamSession session) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            return attendanceLogRepository.findBySession(conn, session.getSessionId());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch session logs: " + e.getMessage(), e);
        } finally {
            DatabaseConnection.releaseConnection(conn);
        }
    }

    // Compiles stats for dashboard
    public Map<String, Object> getSessionStats(ExamSession session) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            long totalStudents = studentRepository.count(conn);
            long verified = attendanceLogRepository.countBySessionAndStatus(conn, session.getSessionId(), "VERIFIED");
            long feesHold = attendanceLogRepository.countBySessionAndStatus(conn, session.getSessionId(), "FEES_HOLD");
            long failed = attendanceLogRepository.countBySessionAndStatus(conn, session.getSessionId(), "FAILED");
            long totalAttempts = attendanceLogRepository.countBySession(conn, session.getSessionId());
            long pending = Math.max(0, totalStudents - verified - feesHold);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalStudents", totalStudents);
            stats.put("verifiedCount", verified);
            stats.put("feesHoldCount", feesHold);
            stats.put("failedCount", failed);
            stats.put("totalAttempts", totalAttempts);
            stats.put("pendingCount", pending);

            return stats;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load session stats: " + e.getMessage(), e);
        } finally {
            DatabaseConnection.releaseConnection(conn);
        }
    }
}
