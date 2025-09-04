package com.povzetkar.quarkus.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class DatabaseMigrator {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/povzetkar";
    private static final String DB_USER = "user";
    private static final String DB_PASSWORD = "password";

    public static void main(String[] args) {
        System.out.println("Starting database migration...");

        Map<String, String> topicFiles = new HashMap<>();
        topicFiles.put("ai", "C:/dev/AI/AIauthors/povzetkar/authors_to_query-ai.txt");
        topicFiles.put("crypto", "C:/dev/AI/AIauthors/povzetkar/authors_to_query-crypto.txt");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false); // Start transaction

            // Ensure 'YOUTUBE' source type exists
            long youtubeSourceTypeId = getOrCreateSourceType(conn, "YOUTUBE");

            for (Map.Entry<String, String> entry : topicFiles.entrySet()) {
                String topicName = entry.getKey();
                String fileName = entry.getValue();

                System.out.println("Processing topic: " + topicName + " from file: " + fileName);

                long topicId = getOrCreateTopic(conn, topicName);

                // Assuming authors_to_query.txt files are in the project root
                try (Stream<String> lines = Files.lines(Paths.get(fileName))) {
                    lines.forEach(channelId -> {
                        try {
                            long channelDbId = getOrCreateChannel(conn, channelId.trim(), youtubeSourceTypeId);
                            linkChannelToTopic(conn, channelDbId, topicId);
                        } catch (SQLException e) {
                            System.err.println("Error processing channel " + channelId + ": " + e.getMessage());
                        }
                    });
                } catch (IOException e) {
                    System.err.println("Error reading file " + fileName + ": " + e.getMessage());
                }
            }
            conn.commit(); // Commit transaction
            System.out.println("Database migration completed successfully.");

        } catch (SQLException e) {
            System.err.println("Database migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static long getOrCreateSourceType(Connection conn, String sourceTypeName) throws SQLException {
        String selectSql = "SELECT id FROM source_types WHERE name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, sourceTypeName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        String insertSql = "INSERT INTO source_types (name, created_at) VALUES (?, NOW()) RETURNING id";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setString(1, sourceTypeName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }
        throw new SQLException("Failed to get or create source type: " + sourceTypeName);
    }

    private static long getOrCreateTopic(Connection conn, String topicName) throws SQLException {
        String selectSql = "SELECT id FROM topics WHERE name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, topicName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        String insertSql = "INSERT INTO topics (name, created_at) VALUES (?, ?) RETURNING id";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setString(1, topicName);
            pstmt.setTimestamp(2, new Timestamp(System.currentTimeMillis())); // Set created_at from Java
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }
        throw new SQLException("Failed to get or create topic: " + topicName);
    }

    private static long getOrCreateChannel(Connection conn, String sourceChannelId, long sourceTypeId) throws SQLException {
        String selectSql = "SELECT id FROM channels WHERE source_channel_id = ? AND source_type_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, sourceChannelId);
            pstmt.setLong(2, sourceTypeId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        // Placeholder values for name, description, subscriber_count
        String insertSql = "INSERT INTO channels (source_channel_id, source_type_id, name, description, subscriber_count, created_at) VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setString(1, sourceChannelId);
            pstmt.setLong(2, sourceTypeId);
            pstmt.setString(3, "Unknown Channel Name"); // Placeholder
            pstmt.setString(4, "No description available."); // Placeholder
            pstmt.setInt(5, 0); // Placeholder
            pstmt.setTimestamp(6, new Timestamp(System.currentTimeMillis())); // Set created_at from Java
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }
        throw new SQLException("Failed to get or create channel: " + sourceChannelId);
    }

    private static void linkChannelToTopic(Connection conn, long channelId, long topicId) throws SQLException {
        String selectSql = "SELECT channel_id, topic_id FROM channel_topics WHERE channel_id = ? AND topic_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setLong(1, channelId);
            pstmt.setLong(2, topicId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // Link already exists, do nothing
                return;
            }
        }

        String insertSql = "INSERT INTO channel_topics (channel_id, topic_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setLong(1, channelId);
            pstmt.setLong(2, topicId);
            pstmt.executeUpdate();
        }
    }
}
