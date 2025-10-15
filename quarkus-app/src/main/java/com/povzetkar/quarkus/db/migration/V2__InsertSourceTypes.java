package com.povzetkar.quarkus.db.migration;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;

public class V2__InsertSourceTypes extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        for (SourceTypeEnum sourceType : SourceTypeEnum.values()) {
            try (PreparedStatement statement = context.getConnection().prepareStatement(
                    "INSERT INTO source_types (id, name) VALUES (?, ?)")) {
                statement.setInt(1, sourceType.getId());
                statement.setString(2, sourceType.name());
                statement.execute();
            }
        }
    }
}
