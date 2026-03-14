package com.povzetkar.quarkus.db.migration;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;

public class V2__InsertSourceTypes extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        String sql = "INSERT INTO source_types (id, name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING";
        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (SourceTypeEnum sourceType : SourceTypeEnum.values()) {
                if (sourceType == SourceTypeEnum.DEFAULT) {
                    continue;
                }
                statement.setInt(1, sourceType.getId());
                statement.setString(2, sourceType.name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
