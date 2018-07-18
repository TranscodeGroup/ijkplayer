package tv.danmaku.ijk.media.example.widget.media;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;

import java.io.File;
import java.nio.ByteBuffer;

import tv.danmaku.ijk.media.example.BuildConfig;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.convertNV21ToBitmap;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.saveBufferToFile;
import static tv.danmaku.ijk.media.example.widget.media.MediaEncoderCore.info;
import static tv.danmaku.ijk.media.example.widget.media.MediaEncoderCore.warn;

public class MediaRecorder implements IjkMediaPlayer.OnFrameAvailableListener {
    /* ----- assessed by encoder thread ----- */
    private MediaEncoderCore mEncoder;

    /* ----- assessed by multiple threads ----- */
    private volatile Handler mEncoderHandler;
    private volatile int mVideoFormat = IjkConstant.Format.INVALID;
    private volatile int mVideoWidth = 0;
    private volatile int mVideoHeight = 0;
    private final Object mStateLock = new Object();
    private boolean mRunning;
    private boolean mHandlerReady;

    public MediaRecorder(IjkVideoView videoView) {
        videoView.setOnFrameAvailableListener(this);
    }

    @MainThread
    public boolean startRecording(File outFile) {
        if (mVideoFormat == IjkConstant.Format.INVALID) {
            warn("Video params(format/width/height) no ready");
            return false;
        }
        synchronized (mStateLock) {
            if (mRunning) {
                warn("Encoder thread already running");
                return false;
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
                mEncoder = new MediaEncoderCore(false, outFile, mVideoWidth, mVideoHeight, 1_000_000, mVideoFormat);
            } catch (Throwable e) {
                throw new RuntimeException("init encoder failed!", e);
            }
        });
        return true;
    }

    @MainThread
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

    public void commitVideoFrame(ByteBuffer buffer, long ptsUs) {
        if (!isHandlerReady()) {
            return;
        }
        mEncoderHandler.post(() -> {
            if (BuildConfig.DEBUG) {
                File file = saveBufferToFile(buffer, mVideoWidth, mVideoHeight, mEncoder.getOutputFile().getParentFile().toString());
                if (file != null) {
                    info("save to %s", file);
                }
            }
            mEncoder.drainVideoEncoder(false);
            mEncoder.commitVideoFrame(buffer, ptsUs, false);
        });
    }

    public void commitAudioFrame(ByteBuffer buffer, long ptsUs) {
        if (!isHandlerReady()) {
            return;
        }
        mEncoderHandler.post(() -> {
            mEncoder.drainAudioEncoder(false);
            mEncoder.commitAudioFrame(buffer, ptsUs, false);
        });
    }

    @Override
    public void onVideoFrame(ByteBuffer buffer, double pts, int format, int width, int height) {
        mVideoFormat = format;
        mVideoWidth = width;
        mVideoHeight = height;
        commitVideoFrame(buffer, (long) (pts * 1000 * 1000));
    }

    @Override
    public void onAudioFrame(ByteBuffer buffer, double pts) {
        commitAudioFrame(buffer, (long) (pts * 1000 * 1000));
    }
}
