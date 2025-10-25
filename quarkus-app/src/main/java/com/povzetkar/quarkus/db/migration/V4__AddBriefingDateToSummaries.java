package com.povzetkar.quarkus.db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;

public class V4__AddBriefingDateToSummaries extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        // Add the new column with a default value for existing rows
        try (PreparedStatement statement = context.getConnection().prepareStatement(
                "ALTER TABLE summaries ADD COLUMN briefing_date DATE NOT NULL DEFAULT CURRENT_DATE")) {
            statement.execute();
        }
    }
}
