package tv.danmaku.ijk.media.example.widget.media;

import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;

import tv.danmaku.ijk.media.example.BuildConfig;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.copyLimited;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.generateNowTime4File;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.saveBufferToFile;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.verbose;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.warn;

/**
 * @see com.android.grafika.TextureMovieEncoder2
 */
public class MediaRecorder implements IjkMediaPlayer.OnFrameAvailableListener {

    private final Formats mFormats = new Formats();
    private volatile EncodeThread mEncodeThread;

    public MediaRecorder(IjkVideoView videoView) {
        videoView.setOnFrameAvailableListener(this);
    }

    public void startRecording(File outFile, Callback callback) {
        if (mEncodeThread == null) {
            if (mFormats.isInvalid()) {
                callback.onFailed(null, new IllegalStateException("Video params(format/width/height) no ready"));
                return;
            }
            mEncodeThread = new EncodeThread(outFile, mFormats, callback);
        }
    }

    public boolean isRecording() {
        return mEncodeThread != null;
    }

    public void stopRecording() {
        EncodeThread encodeThread = mEncodeThread;
        mEncodeThread = null;
        if (encodeThread != null) {
            encodeThread.exit();
        }
    }

    @Override
    public void onVideoFrame(ByteBuffer buffer, double pts, int format, int width, int height) {
        mFormats.videoFormat = format;
        mFormats.videoWidth = width;
        mFormats.videoHeight = height;
        EncodeThread encodeThread = mEncodeThread;
        if (encodeThread != null) {
            encodeThread.onVideoFrame(buffer, pts, format, width, height);
        }

    }

    @Override
    public void onAudioFrame(ByteBuffer buffer, double pts, int sampleRate, long channelLayout) {
        mFormats.audioSampleRate = sampleRate;
        mFormats.audioChannelLayout = channelLayout;
        EncodeThread encodeThread = mEncodeThread;
        if (encodeThread != null) {
            encodeThread.onAudioFrame(buffer, pts, sampleRate, channelLayout);
        }
    }

    /**
     * callback in child thread
     */
    public interface Callback {
        void onStarted(EncodeThread thread);

        void onFailed(@Nullable EncodeThread thread, Exception e);

        void onCompleted(EncodeThread thread, boolean reasonIsFormatChanged);
    }

    public static class EncodeThread extends HandlerThread implements IjkMediaPlayer.OnFrameAvailableListener {
        private final Handler mHandler;
        private final File mOutFile;
        private final Formats mFormats;
        private final Callback mCallback;
        private MediaEncoderCore mEncoderCore;
        private volatile boolean mFormatChanged = false;

        public EncodeThread(File outFile, Formats formats, Callback callback) {
            super("encode thread: " + outFile.getName());
            mOutFile = outFile;
            mFormats = formats.clone(); // need clone
            mCallback = callback;
            this.start(); // must start(), then we can getLooper().
            mHandler = new Handler(this.getLooper());
        }

        @Override
        public void run() {
            Exception error = null;
            try {
                mEncoderCore = new MediaEncoderCore(true, true, mOutFile,
                        mFormats.videoWidth, mFormats.videoHeight, 1_000_000, mFormats.videoFormat,
                        mFormats.audioSampleRate, mFormats.audioChannelLayout
                );
                mCallback.onStarted(this);
                super.run(); // loop
            } catch (Exception e) {
                error = e;
            } finally {
                if (mEncoderCore != null) {
                    try {
                        mEncoderCore.release();
                    } catch (Exception e) {
                        if (error == null) {
                            error = e;
                        } else {
                            warn("double exception(%s, %s), this second exception be discard...", error, e);
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                verbose("mOutFile.length(): %s, outputDataCount: %s, thumbnail: %s",
                        mOutFile.length(),
                        // when failed, generally, outDataCount < 10
                        mEncoderCore != null ? mEncoderCore.getOutputDataCount() : -1,
                        // when failed, generally, thumbnail is null
                        ThumbnailUtils.createVideoThumbnail(mOutFile.getPath(), MediaStore.Images.Thumbnails.MINI_KIND)
                );
            }
            if (error != null) {
                // delete file when failed
                if (mOutFile.exists()) {
                    mOutFile.delete();
                }
                if (error instanceof IllegalStateException && mEncoderCore != null && mEncoderCore.getOutputDataCount() < MediaEncoderCore.MIN_OUTPUT_DATA_COUNT) {
                    error = new EncodeException.OutputDataTooLittle(error);
                }
                mCallback.onFailed(this, error);
            } else {
                mCallback.onCompleted(this, mFormatChanged);
            }
        }

        private void commitFrame(final boolean isVideo, ByteBuffer c_buffer, final long ptsUs) {
            final ByteBuffer buffer = copyLimited(c_buffer);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    MediaEncoderCore.Encoder encoder = isVideo ? mEncoderCore.getVideoEncoder() : mEncoderCore.getAudioEncoder();
                    if (encoder == null) {
                        return;
                    }
                    if (false) {
                        File outputFile = mEncoderCore.getOutputFile();
                        File file = new File(outputFile.getParentFile(), isVideo
                                ? String.format(Locale.US, "%s_%sx%s.yuv", generateNowTime4File(false), mFormats.videoWidth, mFormats.videoHeight)
                                : outputFile.getName().split("\\.")[0] + ".pcm");
                        boolean append = !isVideo;
                        if (saveBufferToFile(buffer, file, append)) {
                            verbose("save to %s", file);
                        }
                    }
                    encoder.drainEncoder(false);
                    encoder.commitFrame(buffer, ptsUs, false);
                }
            });
        }

        private void signalEndOfStream(MediaEncoderCore.Encoder encoder) {
            if (encoder == null) {
                return;
            }
            encoder.drainEncoder(false);
            int retryCount = 0;
            while (true) {
                // send endOfStream must with correct pts!!
                boolean committed = encoder.commitFrame(null, encoder.computeEndOfStreamPts(), true);
                if (committed) {
                    break;
                }
                if (retryCount++ < 100) { // retry 100 times until success...
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    throw new EncodeException.EndOfStreamFailed("signal end Of Stream failed.");
                }
            }
            encoder.drainEncoder(true);
        }

        @Override
        public void onVideoFrame(ByteBuffer buffer, double pts, int format, int width, int height) {
            if (mFormatChanged) {
                return;
            }
            if (mFormats.videoFormat == format && mFormats.videoWidth == width && mFormats.videoHeight == height) {
                commitFrame(true, buffer, (long) (pts * 1000 * 1000));
            } else {
                warn("video format changed(%s->%s, %s->%s, %s->%s), auto exit.",
                        mFormats.videoFormat, format,
                        mFormats.videoWidth, width,
                        mFormats.videoHeight, height
                );
                mFormatChanged = true;
                exit();
            }
        }

        @Override
        public void onAudioFrame(ByteBuffer buffer, double pts, int sampleRate, long channelLayout) {
            if (mFormatChanged) {
                return;
            }
            if (mFormats.audioSampleRate == sampleRate && mFormats.audioChannelLayout == channelLayout) {
                commitFrame(false, buffer, (long) (pts * 1000 * 1000));
            } else {
                warn("audio format changed(%s->%s, %s->%s), auto exit.",
                        mFormats.audioSampleRate, sampleRate,
                        mFormats.audioChannelLayout, channelLayout
                );
                mFormatChanged = true;
                exit();
            }
        }

        public void exit() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    signalEndOfStream(mEncoderCore.getVideoEncoder());
                    signalEndOfStream(mEncoderCore.getAudioEncoder());
                    Looper.myLooper().quit();
                }
            });
        }

        public File getOutFile() {
            return mOutFile;
        }
    }

    private class Formats implements Cloneable {
        volatile int videoFormat = IjkConstant.Format.INVALID;
        volatile int videoWidth = 0;
        volatile int videoHeight = 0;
        volatile int audioSampleRate = 0;
        volatile long audioChannelLayout;

        public boolean isInvalid() {
            return videoFormat == IjkConstant.Format.INVALID || audioSampleRate == 0;
        }

        @Override
        protected Formats clone() {
            try {
                return (Formats) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }
}
