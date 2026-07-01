package com.uict.bioverify.repository;

import com.uict.bioverify.model.AttendanceLog;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttendanceLogRepository {

    private AttendanceLog mapRow(ResultSet rs) throws SQLException {
        AttendanceLog log = new AttendanceLog();
        log.setLogId(rs.getLong("log_id"));
        log.setStudentId(rs.getLong("student_id"));
        log.setSessionId(rs.getLong("session_id"));
        
        Timestamp verTime = rs.getTimestamp("verification_time");
        if (verTime != null) {
            log.setVerificationTime(verTime.toLocalDateTime());
        }
        
        log.setMatchScore(rs.getInt("match_score"));
        log.setStatus(rs.getString("status"));
        return log;
    }

    public AttendanceLog save(Connection conn, AttendanceLog log) throws SQLException {
        if (log.getLogId() != null) {
            String sql = "UPDATE attendance_logs SET student_id = ?, session_id = ?, verification_time = ?, match_score = ?, status = ? WHERE log_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, log.getStudentId());
                ps.setLong(2, log.getSessionId());
                
                LocalDateTime vt = log.getVerificationTime();
                ps.setTimestamp(3, vt != null ? Timestamp.valueOf(vt) : Timestamp.valueOf(LocalDateTime.now()));
                
                ps.setInt(4, log.getMatchScore());
                ps.setString(5, log.getStatus());
                ps.setLong(6, log.getLogId());
                ps.executeUpdate();
            }
        } else {
            String sql = "INSERT INTO attendance_logs (student_id, session_id, verification_time, match_score, status) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, log.getStudentId());
                ps.setLong(2, log.getSessionId());
                
                LocalDateTime vt = log.getVerificationTime();
                ps.setTimestamp(3, vt != null ? Timestamp.valueOf(vt) : Timestamp.valueOf(LocalDateTime.now()));
                
                ps.setInt(4, log.getMatchScore());
                ps.setString(5, log.getStatus());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        log.setLogId(rs.getLong(1));
                    }
                }
            }
        }
        return log;
    }

    public Optional<AttendanceLog> findByStudentAndSession(Connection conn, Long studentId, Long sessionId) throws SQLException {
        String sql = "SELECT log_id, student_id, session_id, verification_time, match_score, status FROM attendance_logs WHERE student_id = ? AND session_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<AttendanceLog> findBySession(Connection conn, Long sessionId) throws SQLException {
        String sql = "SELECT log_id, student_id, session_id, verification_time, match_score, status FROM attendance_logs WHERE session_id = ?";
        List<AttendanceLog> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<AttendanceLog> findByStudent(Connection conn, Long studentId) throws SQLException {
        String sql = "SELECT log_id, student_id, session_id, verification_time, match_score, status FROM attendance_logs WHERE student_id = ?";
        List<AttendanceLog> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public boolean existsByStudentAndSession(Connection conn, Long studentId, Long sessionId) throws SQLException {
        String sql = "SELECT 1 FROM attendance_logs WHERE student_id = ? AND session_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long countBySessionAndStatus(Connection conn, Long sessionId, String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attendance_logs WHERE session_id = ? AND status = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }

    public long countBySession(Connection conn, Long sessionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attendance_logs WHERE session_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }
}
