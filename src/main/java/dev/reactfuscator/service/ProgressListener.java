package dev.reactfuscator.service;

public interface ProgressListener {
    void progress(double fraction, String message);

    void log(String message);
}
