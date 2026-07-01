package com.uict.bioverify.repository;

import com.uict.bioverify.model.Student;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentRepository {

    private Student mapRow(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setStudentId(rs.getLong("student_id"));
        s.setFullName(rs.getString("full_name"));
        s.setRegNumber(rs.getString("reg_number"));
        s.setProgram(rs.getString("program"));
        s.setYear(rs.getInt("year"));
        s.setRole(rs.getString("role"));
        s.setFeesCleared(rs.getBoolean("fees_cleared"));
        
        Timestamp enroll = rs.getTimestamp("enrollment_date");
        if (enroll != null) {
            s.setEnrollmentDate(enroll.toLocalDateTime());
        }
        
        Timestamp verify = rs.getTimestamp("last_verified_date");
        if (verify != null) {
            s.setLastVerifiedDate(verify.toLocalDateTime());
        }
        
        return s;
    }

    public Optional<Student> findById(Connection conn, Long studentId) throws SQLException {
        String sql = "SELECT student_id, full_name, reg_number, program, year, role, fees_cleared, enrollment_date, last_verified_date FROM students WHERE student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Student> findByRegNumber(Connection conn, String regNumber) throws SQLException {
        String sql = "SELECT student_id, full_name, reg_number, program, year, role, fees_cleared, enrollment_date, last_verified_date FROM students WHERE reg_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean existsByRegNumber(Connection conn, String regNumber) throws SQLException {
        String sql = "SELECT 1 FROM students WHERE reg_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Student save(Connection conn, Student student) throws SQLException {
        if (student.getStudentId() != null) {
            return update(conn, student);
        }
        String sql = "INSERT INTO students (full_name, reg_number, program, year, role, fees_cleared, enrollment_date, last_verified_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, student.getFullName());
            ps.setString(2, student.getRegNumber());
            ps.setString(3, student.getProgram());
            ps.setInt(4, student.getYear());
            ps.setString(5, student.getRole());
            ps.setBoolean(6, student.getFeesCleared());
            
            LocalDateTime enroll = student.getEnrollmentDate();
            ps.setTimestamp(7, enroll != null ? Timestamp.valueOf(enroll) : Timestamp.valueOf(LocalDateTime.now()));
            
            LocalDateTime verify = student.getLastVerifiedDate();
            ps.setTimestamp(8, verify != null ? Timestamp.valueOf(verify) : null);
            
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    student.setStudentId(rs.getLong(1));
                }
            }
        }
        return student;
    }

    public Student update(Connection conn, Student student) throws SQLException {
        String sql = "UPDATE students SET full_name = ?, reg_number = ?, program = ?, year = ?, role = ?, fees_cleared = ?, last_verified_date = ? WHERE student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getFullName());
            ps.setString(2, student.getRegNumber());
            ps.setString(3, student.getProgram());
            ps.setInt(4, student.getYear());
            ps.setString(5, student.getRole());
            ps.setBoolean(6, student.getFeesCleared());
            
            LocalDateTime verify = student.getLastVerifiedDate();
            ps.setTimestamp(7, verify != null ? Timestamp.valueOf(verify) : null);
            ps.setLong(8, student.getStudentId());
            ps.executeUpdate();
        }
        return student;
    }

    public long count(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM students";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    public List<Student> findAll(Connection conn) throws SQLException {
        String sql = "SELECT student_id, full_name, reg_number, program, year, role, fees_cleared, enrollment_date, last_verified_date FROM students";
        List<Student> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }
}
