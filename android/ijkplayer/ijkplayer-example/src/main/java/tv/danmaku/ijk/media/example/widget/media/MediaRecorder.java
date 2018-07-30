package tv.danmaku.ijk.media.example.widget.media;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;

import java.io.File;
import java.nio.ByteBuffer;

import tv.danmaku.ijk.media.example.BuildConfig;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.saveBufferToFile;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.info;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.warn;

public class MediaRecorder implements IjkMediaPlayer.OnFrameAvailableListener {
    /* ----- assessed by encoder thread ----- */
    private MediaEncoderCore mEncoderCore;

    /* ----- assessed by multiple threads ----- */
    private volatile Handler mEncoderHandler;
    private volatile int mVideoFormat = IjkConstant.Format.INVALID;
    private volatile int mVideoWidth = 0;
    private volatile int mVideoHeight = 0;
    private volatile int mAudioSampleRate;
    private volatile long mAudioChannelLayout;
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
                mEncoderCore = new MediaEncoderCore(true, true, outFile,
                        mVideoWidth, mVideoHeight, 1_000_000, mVideoFormat,
                        mAudioSampleRate, mAudioChannelLayout
                );
            } catch (Throwable e) {
                throw new RuntimeException("init encoder failed!", e);
            }
        });
        return true;
    }

    @MainThread
    public void stopRecording() {
        mEncoderHandler.post(() -> {
            signalEndOfStream(mEncoderCore.getVideoEncoder());
            signalEndOfStream(mEncoderCore.getAudioEncoder());
            mEncoderCore.release();
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

    private void commitFrame(boolean isVideo, ByteBuffer buffer, long ptsUs) {
        if (!isHandlerReady()) {
            return;
        }
        mEncoderHandler.post(() -> {
            MediaEncoderCore.Encoder encoder = isVideo ? mEncoderCore.getVideoEncoder() : mEncoderCore.getAudioEncoder();
            if (encoder == null) {
                return;
            }
            if (isVideo && false) {
                File file = saveBufferToFile(buffer, mVideoWidth, mVideoHeight, mEncoderCore.getOutputFile().getParentFile().toString());
                if (file != null) {
                    info("save to %s", file);
                }
            }
            encoder.drainEncoder(false);
            encoder.commitFrame(buffer, ptsUs, false);
        });
    }

    private void signalEndOfStream(MediaEncoderCore.Encoder encoder) {
        if (encoder == null) {
            return;
        }
        encoder.drainEncoder(false);
        // send endOfStream must with correct pts!!
        encoder.commitFrame(null, encoder.getLastPts(), true);
        encoder.drainEncoder(true);
    }

    @Override
    public void onVideoFrame(ByteBuffer buffer, double pts, int format, int width, int height) {
        mVideoFormat = format;
        mVideoWidth = width;
        mVideoHeight = height;
        commitFrame(true, buffer, (long) (pts * 1000 * 1000));
    }

    @Override
    public void onAudioFrame(ByteBuffer buffer, double pts, int sampleRate, long channelLayout) {
        mAudioSampleRate = sampleRate;
        mAudioChannelLayout = channelLayout;
        commitFrame(false, buffer, (long) (pts * 1000 * 1000));
    }
}
