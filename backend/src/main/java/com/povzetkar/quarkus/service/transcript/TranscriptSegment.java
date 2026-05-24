package com.povzetkar.quarkus.service.transcript;

/**
 * Internal exchange type for transcript content. Both providers map their native
 * response format to this record before passing to TimeTextFormatter.
 */
public record TranscriptSegment(double startSeconds, String text) {
}
