package com.uict.bioverify.repository;

import com.uict.bioverify.model.Fingerprint;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FingerprintRepository {

    private Fingerprint mapRow(ResultSet rs) throws SQLException {
        Fingerprint fp = new Fingerprint();
        fp.setFingerprintId(rs.getLong("fingerprint_id"));
        fp.setStudentId(rs.getLong("student_id"));
        fp.setTemplateData(rs.getBytes("template_data"));
        fp.setFingerPosition(rs.getInt("finger_position"));
        
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            fp.setCreatedAt(created.toLocalDateTime());
        }
        return fp;
    }

    public List<Fingerprint> findByStudentId(Connection conn, Long studentId) throws SQLException {
        String sql = "SELECT fingerprint_id, student_id, template_data, finger_position, created_at FROM fingerprints WHERE student_id = ?";
        List<Fingerprint> list = new ArrayList<>();
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

    public Optional<Fingerprint> findByStudentIdAndFingerPosition(Connection conn, Long studentId, Integer fingerPosition) throws SQLException {
        String sql = "SELECT fingerprint_id, student_id, template_data, finger_position, created_at FROM fingerprints WHERE student_id = ? AND finger_position = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setInt(2, fingerPosition);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Fingerprint save(Connection conn, Fingerprint fingerprint) throws SQLException {
        if (fingerprint.getFingerprintId() != null) {
            String sql = "UPDATE fingerprints SET student_id = ?, template_data = ?, finger_position = ? WHERE fingerprint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, fingerprint.getStudentId());
                ps.setBytes(2, fingerprint.getTemplateData());
                ps.setInt(3, fingerprint.getFingerPosition());
                ps.setLong(4, fingerprint.getFingerprintId());
                ps.executeUpdate();
            }
        } else {
            String sql = "INSERT INTO fingerprints (student_id, template_data, finger_position, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, fingerprint.getStudentId());
                ps.setBytes(2, fingerprint.getTemplateData());
                ps.setInt(3, fingerprint.getFingerPosition());
                
                LocalDateTime created = fingerprint.getCreatedAt();
                ps.setTimestamp(4, created != null ? Timestamp.valueOf(created) : Timestamp.valueOf(LocalDateTime.now()));
                
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        fingerprint.setFingerprintId(rs.getLong(1));
                    }
                }
            }
        }
        return fingerprint;
    }

    public void delete(Connection conn, Fingerprint fingerprint) throws SQLException {
        if (fingerprint.getFingerprintId() == null) return;
        String sql = "DELETE FROM fingerprints WHERE fingerprint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fingerprint.getFingerprintId());
            ps.executeUpdate();
        }
    }

    public List<Fingerprint> findFingerprintsForSession(Connection conn, Long sessionId) throws SQLException {
        String sql = "SELECT f.fingerprint_id, f.student_id, f.template_data, f.finger_position, f.created_at " +
                     "FROM fingerprints f " +
                     "JOIN session_registrations sr ON f.student_id = sr.student_id " +
                     "WHERE sr.session_id = ?";
        List<Fingerprint> list = new ArrayList<>();
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
}
