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
        long defaultUserId;
        try (Statement select = context.getConnection().createStatement();
             ResultSet rows = select.executeQuery("SELECT id FROM users WHERE name = 'default_user'")) {
            if (rows.next()) {
                defaultUserId = rows.getLong(1);
            } else {
                try (PreparedStatement insert = context.getConnection().prepareStatement(
                        "INSERT INTO users (name, active) VALUES ('default_user', TRUE)", Statement.RETURN_GENERATED_KEYS)) {
                    insert.executeUpdate();
                    ResultSet generatedKeys = insert.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        defaultUserId = generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("Creating user failed, no ID obtained.");
                    }
                }
            }
        }

        // 2. Insert sample YouTube channels (Content Sources)
        int youtubeSourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        String[] channelIdentifiers = {"UCSHZKyawb77ixDdsGog4iWA", "UCHnyfMqiRRG1u-2MsSQLbXA", "UCsXVk37bltHxD1rDPwtNM8Q"};

        String insertContentSourceSql = "INSERT INTO content_sources (source_identifier, source_type_id, name, description, subscriber_count) VALUES (?, ?, '', '', 0) ON CONFLICT (source_identifier, source_type_id) DO NOTHING";
        try (PreparedStatement statement = context.getConnection().prepareStatement(insertContentSourceSql)) {
            for (String identifier : channelIdentifiers) {
                statement.setString(1, identifier);
                statement.setInt(2, youtubeSourceTypeId);
                statement.addBatch();
            }
            statement.executeBatch();
        }

        // 3. Link these content sources to a default topic for the default user
        long defaultTopicId;
        String topicName = "Default Topic";
        try (PreparedStatement select = context.getConnection().prepareStatement(
                "SELECT id FROM topics WHERE user_id = ? AND name = ?")) {
            select.setLong(1, defaultUserId);
            select.setString(2, topicName);
            ResultSet rows = select.executeQuery();
            if (rows.next()) {
                defaultTopicId = rows.getLong(1);
            } else {
                try (PreparedStatement insert = context.getConnection().prepareStatement(
                        "INSERT INTO topics (user_id, name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, defaultUserId);
                    insert.setString(2, topicName);
                    insert.executeUpdate();
                    ResultSet generatedKeys = insert.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        defaultTopicId = generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("Creating topic failed, no ID obtained.");
                    }
                }
            }
        }

        // 4. Link content sources to the default topic
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
