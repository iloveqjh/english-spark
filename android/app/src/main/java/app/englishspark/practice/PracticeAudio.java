package app.englishspark.practice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;

final class PracticeAudio {
    private final Context context;
    private final AudioManager audioManager;
    private final File recording;
    private final File draft;
    private final AudioAttributes speech = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private MediaPlayer player;
    private AudioFocusRequest focusRequest;
    private MediaRecorder recorder;
    private long recordingStartedAt;

    PracticeAudio(Context context) {
        this.context = context;
        audioManager = context.getSystemService(AudioManager.class);
        recording = new File(context.getCacheDir(), "practice-recording.m4a");
        draft = new File(context.getCacheDir(), "practice-draft.m4a");
        clearRecording();
    }

    boolean hasRecording() {
        return recording.isFile() && recording.length() > 0;
    }

    boolean isRecording() {
        return recorder != null;
    }

    void playOriginal(int rawId, float speed, Runnable onFinished, Runnable onInterrupted) throws IOException {
        stopPlaying();
        MediaPlayer original = MediaPlayer.create(context, rawId, speech, AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (original == null) {
            throw new IOException("Lesson audio could not be opened");
        }
        play(original, speed, onFinished, onInterrupted);
    }

    void playRecording(Runnable onFinished, Runnable onInterrupted) throws IOException {
        stopPlaying();
        MediaPlayer take = new MediaPlayer();
        try {
            take.setAudioAttributes(speech);
            take.setDataSource(recording.getAbsolutePath());
            take.prepare();
        } catch (IOException | RuntimeException failure) {
            take.release();
            throw failure;
        }
        play(take, 1f, onFinished, onInterrupted);
    }

    private void play(MediaPlayer next, float speed, Runnable onFinished, Runnable onInterrupted) throws IOException {
        player = next;
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(speech)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0 && player == next) {
                        stopPlaying();
                        onInterrupted.run();
                    }
                })
                .build();
        try {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                throw new IOException("Audio focus unavailable");
            }
            next.setOnCompletionListener(done -> {
                if (player == done) {
                    stopPlaying();
                    onFinished.run();
                }
            });
            next.setOnErrorListener((failed, what, extra) -> {
                if (player == failed) {
                    stopPlaying();
                    onInterrupted.run();
                }
                return true;
            });
            if (speed != 1f) {
                next.setPlaybackParams(new PlaybackParams().allowDefaults().setSpeed(speed).setPitch(1f));
            }
            next.start();
        } catch (IOException | RuntimeException failure) {
            stopPlaying();
            throw failure;
        }
    }

    void stopPlaying() {
        if (player != null) {
            MediaPlayer previous = player;
            player = null;
            previous.release();
        }
        if (focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
    }

    @SuppressWarnings("deprecation")
    void startRecording() throws IOException {
        stopPlaying();
        draft.delete();
        MediaRecorder next = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC);
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            next.setAudioChannels(1);
            next.setOutputFile(draft.getAbsolutePath());
            next.prepare();
            next.start();
            recorder = next;
            recordingStartedAt = SystemClock.elapsedRealtime();
        } catch (IOException | RuntimeException failure) {
            next.release();
            draft.delete();
            throw failure;
        }
    }

    boolean finishRecording() {
        if (recorder == null) {
            return false;
        }
        boolean longEnough = SystemClock.elapsedRealtime() - recordingStartedAt >= 500;
        boolean saved = releaseRecorder() && longEnough && draft.length() > 0 && draft.renameTo(recording);
        draft.delete();
        return saved;
    }

    private boolean releaseRecorder() {
        MediaRecorder previous = recorder;
        recorder = null;
        try {
            previous.stop();
            return true;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            previous.release();
        }
    }

    void cancelRecording() {
        if (recorder != null) {
            releaseRecorder();
        }
        draft.delete();
    }

    void clearRecording() {
        cancelRecording();
        recording.delete();
    }
}
