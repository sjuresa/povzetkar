package com.povzetkar.quarkus.service.transcript;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formats a list of TranscriptSegments into grouped time-text output ((H:mm:ss) text).
 * Segments are grouped to have approximately 350 characters (±30) of text, ideally consisting
 * of whole sentences, with groups separated by time gaps of 80 seconds or more.
 */
public class TimeTextFormatter {

    private static final double TIME_GAP_THRESHOLD = 80.0;
    private static final int TARGET_CHAR_COUNT = 350;
    private static final int CHAR_TOLERANCE = 30;

    public String format(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }

        List<List<TranscriptSegment>> groups = groupByCharCountAndTimeGap(segments);

        return groups.stream()
                .map(this::formatGroup)
                .collect(Collectors.joining("\n"));
    }

    private String formatGroup(List<TranscriptSegment> group) {
        if (group.isEmpty()) {
            return "";
        }
        String timestamp = secondsToTimestamp(group.getFirst().startSeconds());
        String text = group.stream()
                .map(TranscriptSegment::text)
                .collect(Collectors.joining(" "));
        return String.format("(%s) %s", timestamp, text);
    }

    private List<List<TranscriptSegment>> groupByCharCountAndTimeGap(List<TranscriptSegment> segments) {
        List<List<TranscriptSegment>> groups = new ArrayList<>();
        List<TranscriptSegment> currentGroup = new ArrayList<>();
        int charCount = 0;
        TranscriptSegment previous = null;

        for (TranscriptSegment current : segments) {
            if (shouldStartNewGroup(currentGroup, charCount, previous, current)) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
                charCount = 0;
            }
            currentGroup.add(current);
            charCount += current.text().length();
            previous = current;
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

    private boolean shouldStartNewGroup(List<TranscriptSegment> currentGroup, int charCount,
                                        TranscriptSegment previous, TranscriptSegment current) {
        if (previous == null || currentGroup.isEmpty()) {
            return false;
        }
        return isTimeGapExceeded(previous, current)
                || isCharCountExceeded(charCount)
                || (isMinCharCountMet(charCount) && isSentenceEnd(currentGroup));
    }

    private boolean isTimeGapExceeded(TranscriptSegment previous, TranscriptSegment current) {
        return (current.startSeconds() - previous.startSeconds()) >= TIME_GAP_THRESHOLD;
    }

    private boolean isCharCountExceeded(int charCount) {
        return charCount >= TARGET_CHAR_COUNT + CHAR_TOLERANCE;
    }

    private boolean isMinCharCountMet(int charCount) {
        return charCount >= TARGET_CHAR_COUNT - CHAR_TOLERANCE;
    }

    private boolean isSentenceEnd(List<TranscriptSegment> group) {
        String lastText = group.getLast().text().trim();
        return lastText.endsWith(".") || lastText.endsWith("?") || lastText.endsWith("!");
    }

    private String secondsToTimestamp(double seconds) {
        int totalSeconds = (int) seconds;
        int hours = totalSeconds / 3600;
        int minute = (totalSeconds % 3600) / 60;
        int second = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minute, second);
        } else {
            return String.format("%02d:%02d", minute, second);
        }
    }
}
