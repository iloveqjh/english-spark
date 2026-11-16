package app.englishspark.practice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int PAPER = 0xfffcf8ef;
    private static final int WHITE = 0xfffffdf8;
    private static final int INK = 0xff16382e;
    private static final int MUTED = 0xff52655d;
    private static final int GREEN = 0xff245c50;
    private static final int PALE_GREEN = 0xffe5f0e8;
    private static final int PALE_ORANGE = 0xfff9e8d2;
    private static final int ORANGE_INK = 0xff75421d;
    private static final int LINE = 0xffe5e3d8;
    private static final int MIC_REQUEST = 7;
    private static final int MAX_RECORDING_MS = 20_000;
    private static final String DAYS_KEY = "completed_days";
    private static final String ACTIVE_KEY = "active_lesson";
    private static final String LEARNED_KEY = "learned_lessons";
    private static final String WISH_KEY = "reward_wish";
    private static final String WISH_REDEEMED_KEY = "reward_wish_redeemed";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable recordingLimit = () -> stopRecording(true);
    private SharedPreferences preferences;
    private PracticeProgress progress;
    private List<Lessons.Lesson> lessons;
    private Lessons.Lesson lesson;
    private Set<String> learned;
    private PracticeAudio audio;
    private Screen screen = Screen.HOME;
    private boolean originalHeard;
    private boolean revealTranslation;
    private boolean foreground;
    private boolean pendingMicrophone;
    private boolean writingProgress;
    private Playback playback = Playback.NONE;
    private TextView status;
    private TextView translation;
    private Button translate;
    private Button normal;
    private Button slow;
    private Button record;
    private Button replay;
    private Button advance;
    private int quizPosition;
    private int quizCorrect;

    private enum Screen { HOME, LIBRARY, PRACTICE, DONE, REWARDS, QUIZ }
    private enum Playback { NONE, NORMAL, SLOW, RECORDING }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        preferences = getSharedPreferences("practice", MODE_PRIVATE);
        lessons = Lessons.load(this);
        learned = new HashSet<>(preferences.getStringSet(LEARNED_KEY, Collections.emptySet()));
        lesson = findLesson(preferences.getString(ACTIVE_KEY, lessons.get(0).id));
        loadProgress(lesson);
        audio = new PracticeAudio(this);
        if (state != null && state.getBoolean("practicing")) {
            showPractice();
        } else {
            showHome();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        if (screen == Screen.HOME) {
            showHome();
        } else if (screen == Screen.PRACTICE) {
            updatePractice();
        }
    }

    @Override
    protected void onPause() {
        foreground = false;
        boolean interrupted = audio.isRecording() || playback != Playback.NONE;
        stopMedia();
        if (interrupted && screen == Screen.PRACTICE) {
            updatePractice();
            status.setText("已暂停，准备好后继续。");
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopMedia();
        audio.clearRecording();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("practicing", screen == Screen.PRACTICE);
        super.onSaveInstanceState(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (screen == Screen.HOME) {
            super.onBackPressed();
        } else {
            showHome();
        }
    }

    private Set<String> completedDays() {
        return preferences.getStringSet(DAYS_KEY, Collections.emptySet());
    }

    private Lessons.Lesson findLesson(String id) {
        for (Lessons.Lesson item : lessons) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return lessons.get(0);
    }

    private String positionKey(Lessons.Lesson item) {
        return "next_sentence_" + item.id;
    }

    private void loadProgress(Lessons.Lesson item) {
        lesson = item;
        progress = new PracticeProgress(item.sentences.length,
                preferences.getInt(positionKey(item), 0), completedDays());
    }

    private Lessons.Lesson suggestedLesson() {
        if (progress.nextSentence() > 0) {
            return lesson;
        }
        for (Lessons.Lesson item : lessons) {
            if (!learned.contains(item.id)) {
                return item;
            }
        }
        return lesson;
    }

    private void begin(Lessons.Lesson item) {
        dismissKeyboard();
        preferences.edit().putString(ACTIVE_KEY, item.id).apply();
        loadProgress(item);
        resetSentence();
        showPractice();
    }

    private void showHome() {
        dismissKeyboard();
        stopMedia();
        audio.clearRecording();
        screen = Screen.HOME;
        LocalDate today = LocalDate.now();
        boolean checked = progress.finishedOn(today);
        LinearLayout body = page();
        masthead(body, "英语练习");

        LinearLayout hero = row();
        hero.setBackground(shape(GREEN, 26, Color.TRANSPARENT));
        hero.setPadding(dp(19), dp(15), dp(9), dp(15));
        LinearLayout greeting = column(0);
        text(greeting, checked ? "今天的火花亮啦" : "英语小火苗", 13, 0xffffdb96, true, 0);
        text(greeting, checked ? "做得好！" : "今天读什么？", 26, Color.WHITE, true, 6);
        text(greeting, checked ? "想继续的话，挑一课吧。" : "和小火苗一起练口语。", 15, 0xffe4f2e7, false, 7);
        hero.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
        int mascotWidth = Math.max(85, Math.min(119, getResources().getConfiguration().screenWidthDp / 3));
        hero.addView(new FlameCompanion(this), new LinearLayout.LayoutParams(dp(mascotWidth), dp(112)));
        add(body, hero, 15);

        Lessons.Lesson suggested = suggestedLesson();
        int position = preferences.getInt(positionKey(suggested), 0);
        LinearLayout lessonCard = card();
        text(lessonCard, (checked ? "下一次练习 · " : "今天的跟读 · ") + lessonNumber(suggested), 13, GREEN, true, 0);
        text(lessonCard, suggested.title, 25, INK, true, 5);
        text(lessonCard, suggested.sentences.length + " 句", 15, MUTED, false, 6);
        String title = checked ? "开始跟读" : "开始今日跟读";
        if (position > 0) {
            title = "继续跟读 · 已读 " + position + "/" + suggested.sentences.length + " 句";
        } else if (learned.contains(suggested.id)) {
            title = "再练一遍";
        }
        add(lessonCard, button(title, true, () -> begin(suggested)), 16);
        add(body, lessonCard, 16);
        add(body, stats(today), 16);
        if (lessons.size() > 1) {
            add(body, button("全部课程 · " + lessons.size() + " 课", false, this::showLibrary), 14);
        }
        add(body, button("我的火花与奖励", false, this::showRewards), 9);
    }

    private void showRewards() {
        screen = Screen.REWARDS;
        LinearLayout body = page();
        add(body, button("返回首页", false, this::showHome), 0);
        text(body, "我的火花收藏", 26, INK, true, 18);
        add(body, stats(LocalDate.now()), 14);
        int best = progress.bestStreak();
        LinearLayout badges = card();
        text(badges, "最长连续 " + best + " 天", 17, INK, true, 0);
        rewardLine(badges, best, 3, "小火苗徽章");
        rewardLine(badges, best, 7, "一周徽章与心愿券");
        rewardLine(badges, best, 14, "两周徽章");
        rewardLine(badges, best, 30, "一月徽章");
        add(body, badges, 14);
        if (best < 7) {
            return;
        }
        LinearLayout wishCard = card();
        text(wishCard, "我的心愿券", 21, INK, true, 0);
        String wish = preferences.getString(WISH_KEY, "");
        boolean redeemed = preferences.getBoolean(WISH_REDEEMED_KEY, false);
        if (wish.isEmpty()) {
            text(wishCard, "选个小心愿，和家里商量。", 15, MUTED, false, 8);
            add(wishCard, button("写下心愿", true, () -> editWish(wish)), 12);
        } else {
            text(wishCard, wish, 17, INK, false, 8);
            text(wishCard, redeemed ? "已兑现" : "等家里确认", 15, MUTED, false, 7);
            if (!redeemed) {
                Button edit = button("修改心愿", false, () -> editWish(wish));
                Button redeem = button("标为已兑现", true, () -> new AlertDialog.Builder(this)
                        .setTitle("心愿已兑现？")
                        .setMessage("和家里确认后，再点确定。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确定", (dialog, which) -> {
                            preferences.edit().putBoolean(WISH_REDEEMED_KEY, true).apply();
                            showRewards();
                        }).show());
                add(wishCard, twoButtons(edit, redeem), 12);
            }
        }
        add(body, wishCard, 14);
    }

    private void rewardLine(LinearLayout parent, int best, int days, String name) {
        String state = best >= days ? "已获得" : "连续 " + days + " 天解锁";
        text(parent, name + " · " + state, 15, best >= days ? GREEN : MUTED, false, 10);
    }

    private void editWish(String wish) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("比如周末选一道喜欢的菜");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        input.setText(wish);
        LinearLayout field = column(20);
        add(field, input, 0);
        new AlertDialog.Builder(this)
                .setTitle("写下心愿")
                .setView(field)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        preferences.edit().putString(WISH_KEY, value).apply();
                        showRewards();
                    }
                }).show();
    }

    private void dismissKeyboard() {
        View focused = getCurrentFocus();
        if (focused instanceof EditText) {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    private void showLibrary() {
        stopMedia();
        audio.clearRecording();
        screen = Screen.LIBRARY;
        LinearLayout body = page();
        add(body, button("返回首页", false, this::showHome), 0);
        text(body, "全部课程", 28, INK, true, 20);
        int sentenceCount = 0;
        for (Lessons.Lesson item : lessons) {
            sentenceCount += item.sentences.length;
        }
        text(body, "共 " + sentenceCount + " 句 · 已完成 " + learned.size() + " / " + lessons.size() + " 课", 15, MUTED, false, 6);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setHint("搜课程名、英文或中文");
        search.setContentDescription("搜课程名、英文或中文");
        search.setPadding(dp(14), dp(9), dp(14), dp(9));
        search.setMinHeight(dp(50));
        search.setBackground(shape(WHITE, 14, LINE));
        body.setFocusableInTouchMode(true);
        body.requestFocus();
        add(body, search, 12);
        LinearLayout results = column(0);
        add(body, results, 2);
        fillLessonList(results, "");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                fillLessonList(results, text.toString().trim().toLowerCase(Locale.ROOT));
            }
            @Override public void afterTextChanged(Editable text) { }
        });
    }

    private void fillLessonList(LinearLayout results, String query) {
        results.removeAllViews();
        for (Lessons.Lesson item : lessons) {
            if (!matches(item, query)) {
                continue;
            }
            int position = preferences.getInt(positionKey(item), 0);
            String detail = item.sentences.length + " 句";
            if (position > 0) {
                detail += " · 已读 " + position + " 句";
            } else if (learned.contains(item.id)) {
                detail += " · 已完成";
            }
            add(results, button(lessonNumber(item) + "  " + item.title + "\n" + detail, false, () -> begin(item)), 10);
        }
        if (results.getChildCount() == 0) {
            text(results, "没找到，试试别的关键词。", 15, MUTED, false, 14);
        }
    }

    private boolean matches(Lessons.Lesson item, String query) {
        if ((item.title + " " + item.source).toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (Lessons.Sentence sentence : item.sentences) {
            if (sentence.english.toLowerCase(Locale.ROOT).contains(query) || sentence.chinese.contains(query)) {
                return true;
            }
        }
        return false;
    }

    private String lessonNumber(Lessons.Lesson item) {
        return "第 " + (lessons.indexOf(item) + 1) + " 课";
    }

    private void showPractice() {
        screen = Screen.PRACTICE;
        int index = progress.nextSentence();
        Lessons.Sentence current = lesson.sentences[index];
        LinearLayout body = page();
        LinearLayout top = row();
        Button back = button("返回首页", false, this::showHome);
        top.addView(back, new LinearLayout.LayoutParams(dp(110), dp(48)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView position = label("第 " + (index + 1) + " / " + lesson.sentences.length + " 句", 14, GREEN, true);
        top.addView(position, new LinearLayout.LayoutParams(-2, -2));
        add(body, top, 0);
        add(body, stepBar(index), 14);

        LinearLayout sentence = card();
        text(sentence, lessonNumber(lesson) + " · " + lesson.title, 13, GREEN, true, 0);
        TextView phrase = text(sentence, current.english, 33, INK, true, 12);
        phrase.setTextLocale(java.util.Locale.ENGLISH);
        translation = text(sentence, current.chinese, 17, MUTED, false, 8);
        translate = button("看中文", false, () -> {
            revealTranslation = !revealTranslation;
            updateTranslation();
        });
        add(sentence, translate, 12);
        add(body, sentence, 15);
        if (!lesson.source.isEmpty()) {
            TextView credit = text(body, "音频来源 · " + lesson.source, 12, MUTED, false, 8);
            Linkify.addLinks(credit, Linkify.WEB_URLS);
        }
        updateTranslation();

        normal = button("听原音", true, () -> playOriginal(Playback.NORMAL));
        slow = button("慢速重听", false, () -> playOriginal(Playback.SLOW));
        add(body, twoButtons(normal, slow), 16);

        status = text(body, "", 15, MUTED, false, 14);
        status.setMinHeight(dp(47));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        record = button("录音", false, this::toggleRecording);
        replay = button("回听录音", false, this::playTake);
        add(body, twoButtons(record, replay), 0);
        advance = button("我读完了", true, this::advanceSentence);
        add(body, advance, 18);
        updatePractice();
    }

    private void showDone(boolean newCheckIn, LocalDate today) {
        screen = Screen.DONE;
        LinearLayout body = page();
        masthead(body, lesson.title + " · " + lessonNumber(lesson));
        LinearLayout result = card();
        result.addView(new FlameCompanion(this), new LinearLayout.LayoutParams(dp(96), dp(96)));
        text(result, newCheckIn ? "火花点亮啦！" : "这一课读完啦！", 27, INK, true, 8);
        text(result, (newCheckIn ? "今天跟读了 " : "今天已签到，这课又读了 ") + lesson.sentences.length + " 句。", 17, MUTED, false, 9);
        add(body, result, 20);
        add(body, stats(today), 16);
        int streak = progress.streakOn(today);
        if (newCheckIn && (streak == 3 || streak == 7 || streak == 14 || streak == 30)) {
            add(body, button("查看火花与奖励", false, this::showRewards), 12);
        }
        add(body, button("返回首页", true, this::showHome), 18);
        add(body, button("听音选意思", false, this::beginQuiz), 9);
        Lessons.Lesson next = suggestedLesson();
        if (next != lesson) {
            add(body, button("下一课 · " + next.title, false, () -> begin(next)), 9);
        } else {
            add(body, button("再练一遍", false, () -> begin(lesson)), 9);
        }
    }

    private void beginQuiz() {
        quizPosition = 0;
        quizCorrect = 0;
        showQuiz();
    }

    private void showQuiz() {
        stopMedia();
        screen = Screen.QUIZ;
        LinearLayout body = page();
        add(body, button("返回首页", false, this::showHome), 0);
        text(body, "听音选意思", 27, INK, true, 18);
        if (quizPosition == lesson.sentences.length) {
            text(body, "答对 " + quizCorrect + " / " + quizPosition + " 题", 21, GREEN, true, 16);
            add(body, button("再试一次", true, this::beginQuiz), 16);
            return;
        }
        text(body, lesson.title + " · 第 " + (quizPosition + 1) + " / " + lesson.sentences.length + " 题", 15, MUTED, false, 8);
        Lessons.Sentence answer = lesson.sentences[quizPosition];
        List<Button> buttons = new ArrayList<>();
        Button listen = button("听原音", true, () -> playQuizClip(answer.audio, () -> {
            for (Button option : buttons) {
                if (!option.isActivated()) {
                    option.setEnabled(true);
                }
            }
        }));
        add(body, listen, 17);
        TextView feedback = text(body, "先听一句，选出它的意思。", 15, MUTED, false, 12);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        List<Lessons.Sentence> distractors = new ArrayList<>();
        Set<String> usedMeanings = new HashSet<>();
        usedMeanings.add(answer.chinese);
        for (Lessons.Sentence item : lesson.sentences) {
            if (usedMeanings.add(item.chinese)) {
                distractors.add(item);
            }
        }
        Collections.shuffle(distractors);
        List<Lessons.Sentence> choices = new ArrayList<>(distractors.subList(0, Math.min(2, distractors.size())));
        choices.add(answer);
        Collections.shuffle(choices);
        Button next = button(quizPosition + 1 == lesson.sentences.length ? "查看结果" : "下一题", true, () -> {
            quizPosition++;
            showQuiz();
        });
        next.setVisibility(View.GONE);
        for (Lessons.Sentence choice : choices) {
            Button option = button(choice.chinese, false, () -> {
                if (next.getVisibility() == View.VISIBLE) {
                    return;
                }
                boolean correct = choice == answer;
                if (correct) {
                    quizCorrect++;
                }
                feedback.setText((correct ? "答对了。" : "这句的意思是「" + answer.chinese + "」。") + "\n" + answer.english);
                for (Button item : buttons) {
                    item.setEnabled(false);
                    item.setActivated(true);
                }
                next.setVisibility(View.VISIBLE);
            });
            option.setEnabled(false);
            buttons.add(option);
            add(body, option, 10);
        }
        add(body, next, 17);
    }

    private void playQuizClip(int audioId, Runnable onFinished) {
        try {
            audio.playOriginal(audioId, 1f, onFinished, () ->
                    Toast.makeText(this, "声音播放中断，请再试一次。", Toast.LENGTH_SHORT).show());
        } catch (IOException | RuntimeException failure) {
            Toast.makeText(this, "这句暂时播不了，请再试一次。", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateTranslation() {
        translation.setVisibility(revealTranslation ? View.VISIBLE : View.GONE);
        translate.setText(revealTranslation ? "收起中文" : "看中文");
    }

    private void updatePractice() {
        if (screen != Screen.PRACTICE || advance == null) {
            return;
        }
        boolean recording = audio.isRecording();
        normal.setText(playback == Playback.NORMAL ? "停止原音" : originalHeard ? "再听一遍" : "听原音");
        slow.setText(playback == Playback.SLOW ? "停止慢速" : "慢速重听");
        record.setText(recording ? "结束录音" : audio.hasRecording() ? "重新录音" : "录音");
        replay.setText(playback == Playback.RECORDING ? "停止回听" : "回听录音");
        normal.setEnabled(!recording);
        slow.setEnabled(!recording);
        record.setEnabled(!pendingMicrophone);
        replay.setEnabled(!recording && audio.hasRecording());
        advance.setEnabled(originalHeard && !recording && playback == Playback.NONE && !writingProgress);
        if (recording) {
            status.setText("正在录音，读完点「结束录音」。");
        } else if (playback == Playback.RECORDING) {
            status.setText("正在回听你的录音。");
        } else if (playback != Playback.NONE) {
            status.setText(playback == Playback.SLOW ? "正在播放慢速原音 · 0.75 倍" : "正在播放原音。");
        } else if (originalHeard) {
            status.setText(audio.hasRecording() ? "可以回听录音，也可以点「我读完了」。" : "轮到你啦，开口读两遍。");
        } else {
            status.setText("先听一遍，再跟着读。");
        }
    }

    private void playOriginal(Playback speed) {
        if (audio.isRecording()) {
            return;
        }
        if (playback == speed) {
            audio.stopPlaying();
            playback = Playback.NONE;
            updatePractice();
            return;
        }
        try {
            audio.playOriginal(lesson.sentences[progress.nextSentence()].audio, speed == Playback.SLOW ? 0.75f : 1f, () -> {
                playback = Playback.NONE;
                originalHeard = true;
                updatePractice();
            }, this::playbackInterrupted);
            playback = speed;
            updatePractice();
        } catch (IOException | RuntimeException failure) {
            playbackInterrupted();
        }
    }

    private void playTake() {
        if (audio.isRecording() || !audio.hasRecording()) {
            return;
        }
        if (playback == Playback.RECORDING) {
            audio.stopPlaying();
            playback = Playback.NONE;
            updatePractice();
            return;
        }
        try {
            audio.playRecording(() -> {
                playback = Playback.NONE;
                updatePractice();
            }, this::playbackInterrupted);
            playback = Playback.RECORDING;
            updatePractice();
        } catch (IOException | RuntimeException failure) {
            playbackInterrupted();
        }
    }

    private void playbackInterrupted() {
        playback = Playback.NONE;
        updatePractice();
        status.setText("声音播放中断，请再试一次。");
    }

    private void toggleRecording() {
        if (audio.isRecording()) {
            stopRecording(false);
            return;
        }
        if (pendingMicrophone) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMicrophone = true;
            updatePractice();
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != MIC_REQUEST) {
            return;
        }
        pendingMicrophone = false;
        if (screen != Screen.PRACTICE) {
            return;
        }
        updatePractice();
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            status.setText("麦克风已开启，点「录音」开始。");
        } else {
            status.setText("仍然可以直接跟读。想录音时，可在系统设置开启麦克风。");
        }
    }

    private void startRecording() {
        if (!foreground || screen != Screen.PRACTICE) {
            return;
        }
        playback = Playback.NONE;
        try {
            audio.startRecording();
            handler.postDelayed(recordingLimit, MAX_RECORDING_MS);
            updatePractice();
        } catch (IOException | RuntimeException failure) {
            updatePractice();
            status.setText("录音没能启动，可以重试或直接跟读。");
        }
    }

    private void stopRecording(boolean reachedLimit) {
        handler.removeCallbacks(recordingLimit);
        boolean saved = audio.finishRecording();
        updatePractice();
        if (!saved) {
            status.setText("这次录音没保存，可以再录一次。");
        } else if (reachedLimit) {
            status.setText("已录满 20 秒，可以回听。");
        }
    }

    private void stopMedia() {
        handler.removeCallbacks(recordingLimit);
        audio.stopPlaying();
        audio.cancelRecording();
        playback = Playback.NONE;
    }

    private void resetSentence() {
        stopMedia();
        audio.clearRecording();
        originalHeard = false;
        revealTranslation = false;
    }

    private void advanceSentence() {
        if (screen != Screen.PRACTICE || !originalHeard || writingProgress
                || audio.isRecording() || playback != Playback.NONE) {
            return;
        }
        writingProgress = true;
        updatePractice();
        LocalDate today = LocalDate.now();
        PracticeProgress updated = new PracticeProgress(lesson.sentences.length, progress.nextSentence(), progress.completedDays());
        PracticeProgress.Advance result = updated.advance(today);
        Set<String> updatedLearned = new HashSet<>(learned);
        if (result != PracticeProgress.Advance.NEXT_SENTENCE) {
            updatedLearned.add(lesson.id);
        }
        boolean saved = preferences.edit()
                .putInt(positionKey(lesson), updated.nextSentence())
                .putStringSet(LEARNED_KEY, updatedLearned)
                .putString(ACTIVE_KEY, lesson.id)
                .putStringSet(DAYS_KEY, new HashSet<>(updated.completedDays()))
                .commit();
        writingProgress = false;
        if (!saved) {
            updatePractice();
            Toast.makeText(this, "进度没保存，请再试一次。", Toast.LENGTH_LONG).show();
            return;
        }
        progress = updated;
        learned = updatedLearned;
        resetSentence();
        if (result == PracticeProgress.Advance.NEXT_SENTENCE) {
            showPractice();
        } else {
            showDone(result == PracticeProgress.Advance.CHECKED_IN, today);
        }
    }

    private LinearLayout page() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        LinearLayout frame = new LinearLayout(this);
        frame.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.setOrientation(LinearLayout.VERTICAL);
        int available = getResources().getConfiguration().screenWidthDp - 36;
        LinearLayout body = column(0);
        body.setPadding(0, dp(20), 0, dp(28));
        frame.addView(body, new LinearLayout.LayoutParams(dp(Math.max(200, Math.min(460, available))), -2));
        scroll.addView(frame, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        return body;
    }

    private void masthead(LinearLayout body, String caption) {
        LinearLayout row = row();
        row.addView(flame(), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView heading = label(caption, 16, INK, true);
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-2, -2);
        spacing.setMarginStart(dp(10));
        row.addView(heading, spacing);
        add(body, row, 0);
    }

    private ImageView flame() {
        ImageView view = new ImageView(this);
        view.setImageResource(R.drawable.ic_launcher);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private LinearLayout stats(LocalDate today) {
        LinearLayout row = row();
        LinearLayout streak = column(14);
        streak.setBackground(shape(PALE_ORANGE, 18, Color.TRANSPARENT));
        text(streak, "连续跟读", 14, ORANGE_INK, true, 0);
        text(streak, progress.streakOn(today) + " 天", 23, INK, true, 5);
        LinearLayout total = column(14);
        total.setBackground(shape(PALE_GREEN, 18, Color.TRANSPARENT));
        text(total, "累计练习", 14, GREEN, true, 0);
        text(total, progress.totalDays() + " 天", 23, INK, true, 5);
        addPair(row, streak, total);
        return row;
    }

    private LinearLayout stepBar(int position) {
        LinearLayout row = row();
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (int i = 0; i < lesson.sentences.length; i++) {
            View segment = new View(this);
            int color = i < position ? GREEN : i == position ? 0xffd5864e : LINE;
            segment.setBackground(shape(color, 3, Color.TRANSPARENT));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(5), 1);
            if (i > 0) {
                size.setMarginStart(dp(6));
            }
            row.addView(segment, size);
        }
        return row;
    }

    private LinearLayout twoButtons(Button left, Button right) {
        LinearLayout row = row();
        addPair(row, left, right);
        return row;
    }

    private void addPair(LinearLayout row, View left, View right) {
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams rightSpace = new LinearLayout.LayoutParams(0, -2, 1);
        rightSpace.setMarginStart(dp(10));
        row.addView(right, rightSpace);
    }

    private LinearLayout card() {
        LinearLayout card = column(19);
        card.setBackground(shape(WHITE, 23, LINE));
        return card;
    }

    private LinearLayout column(int pad) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));
        return column;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(top);
        parent.addView(child, size);
    }

    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold, int top) {
        TextView view = label(value, size, color, bold);
        add(parent, view, top);
        return view;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        return view;
    }

    private Button button(String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{0xff65746a, primary ? Color.WHITE : GREEN}));
        button.setMinHeight(dp(52));
        button.setPadding(dp(10), dp(9), dp(10), dp(9));
        button.setStateListAnimator(null);
        android.graphics.drawable.StateListDrawable colors = new android.graphics.drawable.StateListDrawable();
        colors.addState(new int[]{-android.R.attr.state_enabled}, shape(0xffe9ede5, 15, Color.TRANSPARENT));
        colors.addState(new int[]{}, shape(primary ? GREEN : PALE_GREEN, 15, Color.TRANSPARENT));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24000000), colors, null));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
