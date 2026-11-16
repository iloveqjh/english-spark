package app.englishspark.practice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PracticeProgress {
    private final int lessonSize;
    private final Set<String> completedDays;
    private int nextSentence;

    PracticeProgress(int lessonSize, int nextSentence, Set<String> completedDays) {
        this.lessonSize = lessonSize;
        this.nextSentence = Math.max(0, Math.min(nextSentence, lessonSize - 1));
        this.completedDays = new HashSet<>(completedDays);
    }

    int nextSentence() {
        return nextSentence;
    }

    Set<String> completedDays() {
        return Collections.unmodifiableSet(completedDays);
    }

    boolean finishedOn(LocalDate day) {
        return completedDays.contains(day.toString());
    }

    int totalDays() {
        return completedDays.size();
    }

    int streakOn(LocalDate today) {
        LocalDate day = finishedOn(today) ? today : today.minusDays(1);
        int count = 0;
        while (finishedOn(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    int bestStreak() {
        List<String> dates = new ArrayList<>(completedDays);
        Collections.sort(dates);
        LocalDate previous = null;
        int count = 0;
        int best = 0;
        for (String date : dates) {
            LocalDate day = LocalDate.parse(date);
            count = previous != null && day.equals(previous.plusDays(1)) ? count + 1 : 1;
            best = Math.max(best, count);
            previous = day;
        }
        return best;
    }

    Advance advance(LocalDate today) {
        nextSentence++;
        if (nextSentence < lessonSize) {
            return Advance.NEXT_SENTENCE;
        }
        nextSentence = 0;
        return completedDays.add(today.toString()) ? Advance.CHECKED_IN : Advance.REVIEWED;
    }

    enum Advance {
        NEXT_SENTENCE, CHECKED_IN, REVIEWED
    }
}
