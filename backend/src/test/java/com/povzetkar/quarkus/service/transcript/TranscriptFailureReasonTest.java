package com.povzetkar.quarkus.service.transcript;

import io.github.thoroldvix.api.TranscriptRetrievalException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptFailureReasonTest {

    @Test
    void theLibrarysReasonLineIsTheReason() {
        var e = new TranscriptRetrievalException("abc", "Transcripts are disabled for this video.");

        assertThat(TranscriptFailureReason.of(e)).isEqualTo("Transcripts are disabled for this video.");
    }

    @Test
    void aMultiLineReasonIsFoldedIntoOneLine() {
        var e = new TranscriptRetrievalException("abc",
                "No transcripts were found.\nManually created: []\n  Automatically generated: [de]");

        assertThat(TranscriptFailureReason.of(e))
                .isEqualTo("No transcripts were found. Manually created: [] Automatically generated: [de]");
    }

    @Test
    void withoutAReasonLineTheWholeMessageIsTheReason() {
        assertThat(TranscriptFailureReason.of(new IllegalStateException("first\nsecond")))
                .isEqualTo("first second");
    }

    @Test
    void withoutAMessageTheExceptionTypeIsTheReason() {
        assertThat(TranscriptFailureReason.of(new NullPointerException())).isEqualTo("NullPointerException");
    }

    @Test
    void oneLineFoldsLineBreaksAndSurroundingBlanks() {
        assertThat(TranscriptFailureReason.oneLine(" a\r\n  b \n")).isEqualTo("a b");
    }
}
