package si.jumis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Paths;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    @Test
    @DisplayName("When all properties are valid, getters should return correct values")
    void getters_whenPropertiesAreValid_returnCorrectValues() {
        // Arrange
        AppConfig appConfig = new AppConfig("test-valid.properties");

        // Act & Assert
        assertThat(appConfig.getYoutubeAppName()).isEqualTo("test-app");
        assertThat(appConfig.getUrlsToSummarizePath()).isEqualTo(Paths.get("test_videos.txt"));
        assertThat(appConfig.getAuthorsListPath()).isEqualTo(Paths.get("test_authors.txt"));
        assertThat(appConfig.getTldrlyDir()).isEqualTo(Paths.get("test_tldrly"));
        assertThat(appConfig.getSummariesDir()).isEqualTo(Paths.get("test_summaries"));
        assertThat(appConfig.getBriefingsBaseDir()).isEqualTo(Paths.get("test_tldrly/briefings"));
    }

    @ParameterizedTest
    @MethodSource("requiredPropertyGetters")
    @DisplayName("When a required property is missing, the corresponding getter should throw IllegalStateException")
    void getRequiredProperty_whenPropertyIsMissing_throwsException(Function<AppConfig, ?> getterMethod, String expectedKey) {
        // Arrange
        AppConfig appConfig = new AppConfig("test-invalid.properties");

        // Act & Assert
        var exception = assertThrows(IllegalStateException.class, () -> getterMethod.apply(appConfig));

        assertEquals("Missing or blank required configuration property: " + expectedKey, exception.getMessage());
    }

    private static Stream<Object[]> requiredPropertyGetters() {
        return Stream.of(
                new Object[]{(Function<AppConfig, ?>) AppConfig::getYoutubeAppName, "youtube.app.name"},
                new Object[]{(Function<AppConfig, ?>) AppConfig::getUrlsToSummarizePath, "path.urls.to.summarize"},
                new Object[]{(Function<AppConfig, ?>) AppConfig::getAuthorsListPath, "path.authors.list"},
                // Note: getTldrlyDir is tested by getBriefingsBaseDir
                new Object[]{(Function<AppConfig, ?>) AppConfig::getSummariesDir, "path.summaries.dir"},
                new Object[]{(Function<AppConfig, ?>) AppConfig::getBriefingsBaseDir, "path.briefings.base.dir"}
        );
    }

    @Test
    @DisplayName("When a required property is blank, it should throw IllegalStateException")
    void getRequiredProperty_whenPropertyIsBlank_throwsException() {
        // Arrange
        AppConfig appConfig = new AppConfig("test-blank.properties");

        // Act & Assert
        var exception = assertThrows(IllegalStateException.class, appConfig::getYoutubeAppName);
        assertEquals("Missing or blank required configuration property: youtube.app.name", exception.getMessage());
    }
}
