package app.englishspark.practice;

import android.content.Context;
import android.content.res.Resources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

final class Lessons {
    static List<Lesson> load(Context context) {
        Resources resources = context.getResources();
        List<Lesson> result = new ArrayList<>();
        Set<String> lessonIds = new HashSet<>();
        try (InputStream input = resources.openRawResource(R.raw.lesson_catalog)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            JSONArray catalog = new JSONArray(new String(output.toByteArray(), StandardCharsets.UTF_8));
            if (catalog.length() == 0) {
                throw new JSONException("Empty catalog");
            }
            for (int i = 0; i < catalog.length(); i++) {
                JSONObject entry = catalog.getJSONObject(i);
                JSONArray entries = entry.getJSONArray("sentences");
                if (entries.length() == 0) {
                    throw new JSONException("Empty lesson");
                }
                Sentence[] sentences = new Sentence[entries.length()];
                for (int j = 0; j < entries.length(); j++) {
                    JSONObject row = entries.getJSONObject(j);
                    int audio = resources.getIdentifier(row.getString("audio"), "raw", context.getPackageName());
                    if (audio == 0) {
                        throw new JSONException("Missing lesson audio: " + row.getString("audio"));
                    }
                    sentences[j] = new Sentence(row.getString("english"), row.getString("chinese"), audio);
                }
                String id = entry.getString("id");
                if (id.isEmpty() || !lessonIds.add(id)) {
                    throw new JSONException("Missing or duplicate lesson ID");
                }
                result.add(new Lesson(id, entry.getString("title"), entry.optString("source"), sentences));
            }
            return Collections.unmodifiableList(result);
        } catch (IOException | JSONException failure) {
            throw new IllegalStateException("Invalid bundled lesson catalog", failure);
        }
    }

    static final class Lesson {
        final String id;
        final String title;
        final String source;
        final Sentence[] sentences;

        Lesson(String id, String title, String source, Sentence[] sentences) {
            this.id = id;
            this.title = title;
            this.source = source;
            this.sentences = sentences;
        }
    }

    static final class Sentence {
        final String english;
        final String chinese;
        final int audio;

        Sentence(String english, String chinese, int audio) {
            this.english = english;
            this.chinese = chinese;
            this.audio = audio;
        }
    }
}
