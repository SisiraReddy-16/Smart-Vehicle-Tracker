package com.garagepulse.servlet;

import com.garagepulse.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Small shared check so every servlet that touches a specific vehicle's
 * data (services, insurance, PUC) confirms it actually belongs to the
 * logged-in user before reading or writing anything.
 *
 * IMPORTANT: SQLException is propagated (not swallowed) so a genuine
 * database outage surfaces as "database unreachable" instead of the
 * misleading "that vehicle doesn't belong to you" 403.
 */
final class VehicleOwnership {

    private VehicleOwnership() { }

    static boolean belongsToUser(int vehicleId, int userId) throws SQLException {
        String sql = "SELECT 1 FROM vehicles WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vehicleId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Looks up which vehicle a service/insurance/PUC record id belongs to, or -1 if it doesn't exist. */
    static int vehicleIdForRecord(String table, int recordId) throws SQLException {
        String sql = "SELECT vehicle_id FROM " + table + " WHERE id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, recordId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("vehicle_id") : -1;
            }
        }
    }
}
