package com.uict.bioverify.repository;

import com.uict.bioverify.model.User;
import java.sql.*;
import java.util.Optional;

public class UserRepository {

    public Optional<User> findByUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT user_id, full_name, username, password_hash, role FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getLong("user_id"));
                    user.setFullName(rs.getString("full_name"));
                    user.setUsername(rs.getString("username"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setRole(rs.getString("role"));
                    return Optional.of(user);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findById(Connection conn, Long userId) throws SQLException {
        String sql = "SELECT user_id, full_name, username, password_hash, role FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getLong("user_id"));
                    user.setFullName(rs.getString("full_name"));
                    user.setUsername(rs.getString("username"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setRole(rs.getString("role"));
                    return Optional.of(user);
                }
            }
        }
        return Optional.empty();
    }

    public User save(Connection conn, User user) throws SQLException {
        if (user.getUserId() != null) {
            String sql = "UPDATE users SET full_name = ?, username = ?, password_hash = ?, role = ? WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user.getFullName());
                ps.setString(2, user.getUsername());
                ps.setString(3, user.getPasswordHash());
                ps.setString(4, user.getRole());
                ps.setLong(5, user.getUserId());
                ps.executeUpdate();
            }
        } else {
            String sql = "INSERT INTO users (full_name, username, password_hash, role) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.getFullName());
                ps.setString(2, user.getUsername());
                ps.setString(3, user.getPasswordHash());
                ps.setString(4, user.getRole());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        user.setUserId(rs.getLong(1));
                    }
                }
            }
        }
        return user;
    }

    public long count(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }
}
