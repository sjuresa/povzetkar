package com.povzetkar.quarkus.db.migration;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class V3__InsertInitialContentSources extends BaseJavaMigration {

    private static final String[] YOUTUBE_CHANNEL_IDS = {"UCSHZKyawb77ixDdsGog4iWA", "UCHnyfMqiRRG1u-2MsSQLbXA", "UCsXVk37bltHxD1rDPwtNM8Q"};

    @Override
    public void migrate(Context context) throws Exception {
        // 1. Insert a default user if one does not exist
        long defaultUserId = getOrCreateUser(context, "default_user", "default_user");

        // 2. Insert sample YouTube channels (Content Sources)
        int youtubeSourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        insertContentSources(context, youtubeSourceTypeId, YOUTUBE_CHANNEL_IDS);

        // 3. Link these content sources to a default topic for the default user
        long defaultTopicId = getOrCreateTopic(context, defaultUserId, "Default Topic");

        // 4. Link content sources to the default topic
        linkContentSourcesToTopic(context, defaultTopicId, YOUTUBE_CHANNEL_IDS);
    }

    private long getOrCreateUser(Context context, String username, String name) throws SQLException {
        // This is a PostgreSQL-specific technique for an atomic "get-or-create" operation.
        // The ON CONFLICT DO UPDATE SET name = users.name clause performs a no-op update
        // if the user already exists, allowing the RETURNING id clause to always return the ID
        // without a separate SELECT statement and avoiding potential race conditions.
        String sql = "INSERT INTO users (username, name, active) VALUES (?, ?, TRUE) " +
                "ON CONFLICT (username) DO UPDATE SET username = users.username " +
                "RETURNING id";
        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                } else {
                    // This path should not be reached if the SQL is correct and the user exists or is created.
                    throw new SQLException(String.format("Could not get or create user with username '%s'.", username));
                }
            }
        }
    }

    private long getOrCreateTopic(Context context, long userId, String topicName) throws SQLException {
        // This is a PostgreSQL-specific technique for an atomic "get-or-create" operation.
        // The ON CONFLICT DO UPDATE clause with a no-op update allows the RETURNING id clause
        // to always return the ID without a separate SELECT statement, avoiding potential race conditions.
        String sql = "INSERT INTO topics (user_id, name) VALUES (?, ?) " +
                "ON CONFLICT (user_id, name) DO UPDATE SET name = topics.name " +
                "RETURNING id";
        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, topicName);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                } else {
                    // This path should not be reached if the SQL is correct and the topic exists or is created.
                    throw new SQLException(String.format("Could not get or create topic '%s' for user ID %d.", topicName, userId));
                }
            }
        }
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
