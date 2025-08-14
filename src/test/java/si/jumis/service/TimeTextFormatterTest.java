package si.jumis.service;

import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeTextFormatterTest {

    private static final TranscriptContent EMPTY_CONTENT = new TranscriptContent(List.of());
    private TranscriptContent content;

    @BeforeEach
    void setUp() {
        List<TranscriptContent.Fragment> fragments = List.of(
                new TranscriptContent.Fragment("No recession,", 16.0, 3.0),
                new TranscriptContent.Fragment("no cry,", 19.0, 3.0),
                new TranscriptContent.Fragment("no recession,", 22.0, 3.0),
                new TranscriptContent.Fragment("no cry.", 25.0, 15.0),
                new TranscriptContent.Fragment("me remember when hard work still used to", 40.0, 3.0),
                new TranscriptContent.Fragment("pay. Could I buy a yard, raise the youth", 43.0, 3.0),
                new TranscriptContent.Fragment("and live? Okay. But then the bankers get", 46.0, 2.0),
                new TranscriptContent.Fragment("greedy, flip up the game. Gold standard", 48.0, 3.0),
                new TranscriptContent.Fragment("mash up, it never be the same. Boom.", 51.0, 2.0),
                new TranscriptContent.Fragment("Economy government never want them spend", 53.0, 4.0),
                new TranscriptContent.Fragment("money drop the interest rate.", 57.0, 3.0),
                new TranscriptContent.Fragment("Central bank printing money start lose weight", 60.0, 3.0),
                new TranscriptContent.Fragment("convinc", 63.0, 12.0),
                new TranscriptContent.Fragment("but only if it really legit.", 75.0, 4.0),
                new TranscriptContent.Fragment("No recession.", 79.0, 3.0),
                new TranscriptContent.Fragment("No crime.", 82.0, 3.0),
                new TranscriptContent.Fragment("No recession.", 85.0, 4.0),
                new TranscriptContent.Fragment("No crime.", 89.0, 2.0),
                new TranscriptContent.Fragment("Change up the definition.", 91.0, 4.0),
                new TranscriptContent.Fragment("So slide,", 95.0, 3.0),
                new TranscriptContent.Fragment("no recession,", 98.0, 3.0),
                new TranscriptContent.Fragment("no crime.", 101.0, 3.0),
                new TranscriptContent.Fragment("Life did sweet, people get rich. Time", 104.0, 3.0),
                new TranscriptContent.Fragment("feel nice. Living big pand not thinking", 107.0, 3.0),
                new TranscriptContent.Fragment("about the price. No turning back default", 110.0, 3.0),
                new TranscriptContent.Fragment("and knock we gate but the genie up too", 113.0, 2.0),
                new TranscriptContent.Fragment("late that fate.", 115.0, 2.0)
        );
        content = new TranscriptContent(fragments);
    }

    @Test
    void timeTextFormatter() {
        TranscriptFormatter formatter = new TimeTextFormatter();

        String expected = """
                (00:16) No recession, no cry, no recession, no cry. me remember when hard work still used to pay. Could I buy a yard, raise the youth and live? Okay. But then the bankers get greedy, flip up the game. Gold standard mash up, it never be the same. Boom. Economy government never want them spend money drop the interest rate. Central bank printing money start lose weight convinc but only if it really legit.
                (01:19) No recession. No crime. No recession. No crime. Change up the definition. So slide, no recession, no crime. Life did sweet, people get rich. Time feel nice. Living big pand not thinking about the price. No turning back default and knock we gate but the genie up too late that fate.""";

        String actual = formatter.format(content);

        assertThat(actual).isEqualToNormalizingNewlines(expected);
    }

    @Test
    void timeTextFormatterNoContent() {
        TranscriptFormatter formatter = new TimeTextFormatter();

        String expected = "";

        String actual = formatter.format(EMPTY_CONTENT);

        assertThat(actual).isEqualToNormalizingNewlines(expected);
    }
}