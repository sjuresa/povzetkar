package si.jumis;

import java.time.LocalDate;

/**
 * Holds the application's runtime configuration, parsed from command-line arguments.
 * @param useCase The workflow to execute (S_B, TR, S_URL).
 * @param date    The date for which the workflow should be run.
 * @param dryRun  If true, the application will not perform any actions that modify state.
 */
public record RunConfiguration(String useCase, LocalDate date, boolean dryRun) {
}
