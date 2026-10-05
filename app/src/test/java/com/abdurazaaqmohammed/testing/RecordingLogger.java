package com.abdurazaaqmohammed.testing;

import com.reandroid.apk.APKLogger;

import java.util.ArrayList;
import java.util.List;

/** Collects log messages so tests can assert on what the app reported. */
public final class RecordingLogger implements APKLogger {

    private final List<String> messages = new ArrayList<>();
    private final List<Throwable> errors = new ArrayList<>();

    @Override
    public void logMessage(String s) {
        messages.add(s);
    }

    @Override
    public void logError(String s, Throwable throwable) {
        messages.add(s);
        errors.add(throwable);
    }

    @Override
    public void logVerbose(String s) {
        messages.add(s);
    }

    public List<String> messages() {
        return messages;
    }

    public List<Throwable> errors() {
        return errors;
    }

    public boolean contains(String fragment) {
        return indexOf(fragment) >= 0;
    }

    public int indexOf(String fragment) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }
}
