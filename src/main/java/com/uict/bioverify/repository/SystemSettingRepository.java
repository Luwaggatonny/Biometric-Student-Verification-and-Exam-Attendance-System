package com.uict.bioverify.repository;

import com.uict.bioverify.model.SystemSetting;
import java.sql.*;
import java.util.Optional;

public class SystemSettingRepository {

    public Optional<SystemSetting> findById(Connection conn, String settingKey) throws SQLException {
        String sql = "SELECT setting_key, setting_value FROM system_settings WHERE setting_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, settingKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SystemSetting setting = new SystemSetting(
                        rs.getString("setting_key"),
                        rs.getString("setting_value")
                    );
                    return Optional.of(setting);
                }
            }
        }
        return Optional.empty();
    }

    public SystemSetting save(Connection conn, SystemSetting setting) throws SQLException {
        // Since primary key is a String settingKey, check if it already exists to determine INSERT vs UPDATE
        Optional<SystemSetting> existing = findById(conn, setting.getSettingKey());
        if (existing.isPresent()) {
            String sql = "UPDATE system_settings SET setting_value = ? WHERE setting_key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, setting.getSettingValue());
                ps.setString(2, setting.getSettingKey());
                ps.executeUpdate();
            }
        } else {
            String sql = "INSERT INTO system_settings (setting_key, setting_value) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, setting.getSettingKey());
                ps.setString(2, setting.getSettingValue());
                ps.executeUpdate();
            }
        }
        return setting;
    }

    public long count(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM system_settings";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }
}
