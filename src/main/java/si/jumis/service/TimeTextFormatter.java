package si.jumis.service;

import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptContent.Fragment;
import io.github.thoroldvix.api.TranscriptFormatter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link TranscriptFormatter} that formats transcript content into a grouped time-text format ((H:mm:ss) text).
 * Fragments are grouped to have approximately 350 characters (±30) of text, ideally consisting of whole sentences,
 * with groups separated by time gaps of 80 seconds or more.
 */
public class TimeTextFormatter implements TranscriptFormatter {
    private static final double TIME_GAP_THRESHOLD = 80.0; // Time gap in seconds to separate groups
    private static final int TARGET_CHAR_COUNT = 350; // Target character count per group
    private static final int CHAR_TOLERANCE = 30; // Tolerance for character count (±30)

    @NotNull
    @Override
    public String format(TranscriptContent transcriptContent) {
        List<Fragment> fragments = transcriptContent.getContent();
        if (fragments.isEmpty()) {
            return "";
        }

        List<List<Fragment>> groups = groupFragmentsByCharCountAndTimeGap(fragments);

        return groups.stream()
                .map(this::formatGroup)
                .collect(Collectors.joining("\n"));
    }

    private String formatGroup(List<Fragment> group) {
        if (group.isEmpty()) {
            return "";
        }
        String timestamp = secondsToSimpleTimeStamp(group.getFirst().getStart());
        String text = group.stream()
                .map(Fragment::getText)
                .collect(Collectors.joining(" "));
        return String.format("(%s) %s", timestamp, text);
    }

    private List<List<Fragment>> groupFragmentsByCharCountAndTimeGap(List<Fragment> fragments) {
        List<List<Fragment>> groups = new ArrayList<>();
        if (fragments == null || fragments.isEmpty()) {
            return groups;
        }

        List<Fragment> currentGroup = new ArrayList<>();
        int charCount = 0;
        Fragment previousFragment = null;

        for (Fragment currentFragment : fragments) {
            if (shouldStartNewGroup(currentGroup, charCount, previousFragment, currentFragment)) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
                charCount = 0;
            }

            currentGroup.add(currentFragment);
            charCount += currentFragment.getText().length();
            previousFragment = currentFragment;
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

    private boolean shouldStartNewGroup(List<Fragment> currentGroup, int charCount, Fragment previousFragment, Fragment currentFragment) {
        if (previousFragment == null || currentGroup.isEmpty()) {
            return false;
        }

        if (isTimeGapExceeded(previousFragment, currentFragment)) {
            return true;
        }

        if (isCharCountExceeded(charCount)) {
            return true;
        }

        return isMinCharCountMet(charCount) && isSentenceEnd(currentGroup);
    }

    private boolean isTimeGapExceeded(Fragment previous, Fragment current) {
        return (current.getStart() - previous.getStart()) >= TIME_GAP_THRESHOLD;
    }

    private boolean isCharCountExceeded(int charCount) {
        return charCount >= TARGET_CHAR_COUNT + CHAR_TOLERANCE;
    }

    private boolean isMinCharCountMet(int charCount) {
        return charCount >= TARGET_CHAR_COUNT - CHAR_TOLERANCE;
    }

    private boolean isSentenceEnd(List<Fragment> group) {
        String lastText = group.getLast().getText().trim();
        return lastText.endsWith(".") || lastText.endsWith("?") || lastText.endsWith("!");
    }

    private String secondsToSimpleTimeStamp(double seconds) {
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