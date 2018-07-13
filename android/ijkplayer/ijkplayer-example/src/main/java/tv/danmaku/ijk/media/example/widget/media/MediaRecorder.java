package tv.danmaku.ijk.media.example.widget.media;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static tv.danmaku.ijk.media.example.widget.media.MediaEncoderCore.info;
import static tv.danmaku.ijk.media.example.widget.media.MediaEncoderCore.warn;

public class MediaRecorder {
    /* ----- assessed by encoder thread ----- */
    private MediaEncoderCore mEncoder;

    /* ----- assessed by multiple threads ----- */
    private volatile Handler mEncoderHandler;
    private final Object mStateLock = new Object();
    private boolean mRunning;
    private boolean mHandlerReady;

    public void startRecording(File outFile, int width, int height, int pixel_format) {
        synchronized (mStateLock) {
            if (mRunning) {
                warn("Encoder thread already running");
                return;
            }
            mRunning = true;
            new Thread(() -> {
                Looper.prepare();
                synchronized (mStateLock) {
                    mEncoderHandler = new Handler();
                    mHandlerReady = true;
                    mStateLock.notifyAll();
                }
                Looper.loop();
                info("Encoder thread exiting");
                synchronized (mStateLock) {
                    mHandlerReady = mRunning = false;
                    mEncoderHandler = null;
                }
            }, "MediaRecorder>Encoder Thread").start();
            while (!mHandlerReady) {
                try {
                    mStateLock.wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        mEncoderHandler.post(() -> {
            try {
                mEncoder = new MediaEncoderCore(outFile, width, height, 1_000_000, pixel_format);
            } catch (IOException e) {
                throw new RuntimeException("init encoder failed!", e);
            }
        });
    }

    public void stopRecording() {
        mEncoderHandler.post(() -> {
            mEncoder.commitVideoFrame(null, 0, true);
            mEncoder.commitAudioFrame(null, 0, true);
            mEncoder.drainVideoEncoder(true);
            mEncoder.drainAudioEncoder(true);
            mEncoder.release();
            Looper.myLooper().quit();
        });
    }

    public boolean isRecording() {
        synchronized (mStateLock) {
            return mRunning;
        }
    }

    public boolean isHandlerReady() {
        synchronized (mStateLock) {
            return mHandlerReady;
        }
    }

    public void queueEvent(Runnable r) {
        if (!isHandlerReady()) {
            warn("mEncoderHandler no ready");
            return;
        }
        mEncoderHandler.post(r);
    }

    public void commitVideoFrame(ByteBuffer buffer, long pts) {
        // todo invoke to commit frame data
        if (!isHandlerReady()) {
            return;
        }
        mEncoderHandler.post(() -> {
            mEncoder.commitVideoFrame(buffer, pts, false);
        });
    }
    public void commitAudioFrame(ByteBuffer buffer, long pts) {
        if (!isHandlerReady()) {
            return;
        }
        mEncoderHandler.post(() -> {
            mEncoder.commitAudioFrame(buffer, pts, false);
        });
    }
}
