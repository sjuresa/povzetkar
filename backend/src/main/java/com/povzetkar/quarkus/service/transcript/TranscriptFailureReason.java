package com.povzetkar.quarkus.service.transcript;

/**
 * Why a transcript fetch failed, as one log-friendly line (POV-235). The youtube-transcript-api library puts the
 * reason on a second line ({@code "Could not retrieve transcript for the video: <url>.\nReason: <reason>"}); that
 * reason is taken, otherwise the whole message.
 */
final class TranscriptFailureReason {

    private static final String REASON_MARKER = "Reason: ";

    private TranscriptFailureReason() {
    }

    /** The library's reason if the message has one, otherwise the message; the exception type if there is none. */
    static String of(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        int marker = message.indexOf("\n" + REASON_MARKER);
        return oneLine(marker < 0 ? message : message.substring(marker + 1 + REASON_MARKER.length()));
    }

    /** Line breaks and the blanks around them folded into single spaces, trimmed; {@code null} stays {@code null}. */
    static String oneLine(String text) {
        return text == null ? null : text.strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
