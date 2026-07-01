package com.uict.bioverify.repository;

import com.uict.bioverify.model.ExamSession;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExamSessionRepository {

    private ExamSession mapRow(ResultSet rs) throws SQLException {
        ExamSession es = new ExamSession();
        es.setSessionId(rs.getLong("session_id"));
        es.setPaperName(rs.getString("paper_name"));
        es.setPaperCode(rs.getString("paper_code"));
        
        Date date = rs.getDate("exam_date");
        if (date != null) {
            es.setExamDate(date.toLocalDate());
        }
        
        Time start = rs.getTime("start_time");
        if (start != null) {
            es.setStartTime(start.toLocalTime());
        }
        
        Time end = rs.getTime("end_time");
        if (end != null) {
            es.setEndTime(end.toLocalTime());
        }
        
        long invId = rs.getLong("invigilator_id");
        if (!rs.wasNull()) {
            es.setInvigilatorId(invId);
        }
        return es;
    }

    public Optional<ExamSession> findById(Connection conn, Long sessionId) throws SQLException {
        String sql = "SELECT session_id, paper_name, paper_code, exam_date, start_time, end_time, invigilator_id FROM exam_sessions WHERE session_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public ExamSession save(Connection conn, ExamSession session) throws SQLException {
        if (session.getSessionId() != null) {
            String sql = "UPDATE exam_sessions SET paper_name = ?, paper_code = ?, exam_date = ?, start_time = ?, end_time = ?, invigilator_id = ? WHERE session_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, session.getPaperName());
                ps.setString(2, session.getPaperCode());
                ps.setDate(3, Date.valueOf(session.getExamDate()));
                ps.setTime(4, Time.valueOf(session.getStartTime()));
                ps.setTime(5, Time.valueOf(session.getEndTime()));
                if (session.getInvigilatorId() != null) {
                    ps.setLong(6, session.getInvigilatorId());
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                ps.setLong(7, session.getSessionId());
                ps.executeUpdate();
            }
        } else {
            String sql = "INSERT INTO exam_sessions (paper_name, paper_code, exam_date, start_time, end_time, invigilator_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, session.getPaperName());
                ps.setString(2, session.getPaperCode());
                ps.setDate(3, Date.valueOf(session.getExamDate()));
                ps.setTime(4, Time.valueOf(session.getStartTime()));
                ps.setTime(5, Time.valueOf(session.getEndTime()));
                if (session.getInvigilatorId() != null) {
                    ps.setLong(6, session.getInvigilatorId());
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        session.setSessionId(rs.getLong(1));
                    }
                }
            }
        }
        return session;
    }

    public long count(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM exam_sessions";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    public List<ExamSession> findAll(Connection conn) throws SQLException {
        String sql = "SELECT session_id, paper_name, paper_code, exam_date, start_time, end_time, invigilator_id FROM exam_sessions";
        List<ExamSession> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<ExamSession> findByExamDate(Connection conn, LocalDate examDate) throws SQLException {
        String sql = "SELECT session_id, paper_name, paper_code, exam_date, start_time, end_time, invigilator_id FROM exam_sessions WHERE exam_date = ?";
        List<ExamSession> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(examDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    // Registrations logic
    public void registerStudentForSession(Connection conn, Long sessionId, Long studentId) throws SQLException {
        // Prevent duplicate registration
        String checkSql = "SELECT 1 FROM session_registrations WHERE session_id = ? AND student_id = ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setLong(1, sessionId);
            checkPs.setLong(2, studentId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) return; // Already registered
            }
        }

        String sql = "INSERT INTO session_registrations (session_id, student_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, studentId);
            ps.executeUpdate();
        }
    }

    public List<Long> findRegisteredStudentIds(Connection conn, Long sessionId) throws SQLException {
        String sql = "SELECT student_id FROM session_registrations WHERE session_id = ?";
        List<Long> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getLong("student_id"));
                }
            }
        }
        return list;
    }
}
