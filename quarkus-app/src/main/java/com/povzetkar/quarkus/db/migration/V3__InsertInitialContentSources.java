package com.povzetkar.quarkus.db.migration;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

public class V3__InsertInitialContentSources extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        // 1. Insert a default user if one does not exist
        long defaultUserId = getOrCreateUser(context, "default_user", "default_user");

        // 2. Insert sample YouTube channels (Content Sources)
        int youtubeSourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        String[] channelIdentifiers = {"UCSHZKyawb77ixDdsGog4iWA", "UCHnyfMqiRRG1u-2MsSQLbXA", "UCsXVk37bltHxD1rDPwtNM8Q"};
        insertContentSources(context, youtubeSourceTypeId, channelIdentifiers);

        // 3. Link these content sources to a default topic for the default user
        long defaultTopicId = getOrCreateTopic(context, defaultUserId, "Default Topic");

        // 4. Link content sources to the default topic
        linkContentSourcesToTopic(context, defaultTopicId, channelIdentifiers);
    }

    private long getOrCreateUser(Context context, String username, String name) throws SQLException {
        // Attempt to insert, ignoring conflicts
        try (PreparedStatement insert = context.getConnection().prepareStatement(
                "INSERT INTO users (username, name, active) VALUES (?, ?, TRUE) ON CONFLICT (username) DO NOTHING")) {
            insert.setString(1, username);
            insert.setString(2, name);
            insert.executeUpdate();
        }

        // Always select to get the ID, whether it was just inserted or already existed
        try (PreparedStatement select = context.getConnection().prepareStatement("SELECT id FROM users WHERE username = ?")) {
            select.setString(1, username);
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    return rows.getLong(1);
                } else {
                    throw new SQLException("User with username '" + username + "' not found after insert attempt.");
                }
            }
        }
    }

    private long getOrCreateTopic(Context context, long userId, String topicName) throws SQLException {
        long topicId;
        try (PreparedStatement select = context.getConnection().prepareStatement(
                "SELECT id FROM topics WHERE user_id = ? AND name = ?")) {
            select.setLong(1, userId);
            select.setString(2, topicName);
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    topicId = rows.getLong(1);
                } else {
                    try (PreparedStatement insert = context.getConnection().prepareStatement(
                            "INSERT INTO topics (user_id, name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        insert.setLong(1, userId);
                        insert.setString(2, topicName);
                        insert.executeUpdate();
                        try (ResultSet generatedKeys = insert.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                topicId = generatedKeys.getLong(1);
                            } else {
                                throw new SQLException("Creating topic failed, no ID obtained.");
                            }
                        }
                    }
                }
            }
        }
        return topicId;
    }

    private void insertContentSources(Context context, int youtubeSourceTypeId, String[] channelIdentifiers) throws SQLException {
        String insertContentSourceSql = "INSERT INTO content_sources (source_identifier, source_type_id, name, description, subscriber_count) VALUES (?, ?, '', '', 0) ON CONFLICT (source_identifier, source_type_id) DO NOTHING";
        try (PreparedStatement statement = context.getConnection().prepareStatement(insertContentSourceSql)) {
            for (String identifier : channelIdentifiers) {
                statement.setString(1, identifier);
                statement.setInt(2, youtubeSourceTypeId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void linkContentSourcesToTopic(Context context, long defaultTopicId, String[] channelIdentifiers) throws SQLException {
        String linkSql = "INSERT INTO content_source_topics (content_source_id, topic_id) SELECT cs.id, ? FROM content_sources cs WHERE cs.source_identifier = ? ON CONFLICT (content_source_id, topic_id) DO NOTHING";
        try (PreparedStatement statement = context.getConnection().prepareStatement(linkSql)) {
            for (String identifier : channelIdentifiers) {
                statement.setLong(1, defaultTopicId);
                statement.setString(2, identifier);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
