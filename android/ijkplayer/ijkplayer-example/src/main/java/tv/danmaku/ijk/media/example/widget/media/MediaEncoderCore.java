package tv.danmaku.ijk.media.example.widget.media;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MediaEncoderCore {
    private static final String MIMETYPE_VIDEO_AVC = "video/avc";
    private static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 5;
    public static final String TAG = "tgtrack";
    private Encoder mVideoEncoder;
    private Encoder mAudioEncoder;
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted = false;
    private static final long TIMEOUT_DEQUEUE_BUFFER_USEC = 10_000; // 10ms
    private boolean mHasAudio;
    private File mOutputFile;


    public static boolean api23() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static boolean api21() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static void info(String format, Object... args) {
        Log.i(TAG, String.format(Locale.getDefault(), format, args));
    }

    public static void warn(String format, Object... args) {
        Log.w(TAG, String.format(Locale.getDefault(), format, args));
    }

    public static void assertTrue(boolean b, String format, Object... args) {
        if (!b) {
            throw new RuntimeException(String.format(Locale.getDefault(), format, args));
        }
    }

    public static void toast(Context context, String format, Object... args) {
        Toast.makeText(context, String.format(Locale.getDefault(), format, args), Toast.LENGTH_SHORT).show();
    }

    public MediaEncoderCore(boolean hasAudio, File outputFile, int width, int height, int bitRate, int pixelFormat) throws IOException {
        mHasAudio = hasAudio;
        mOutputFile = outputFile;
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            throw new IOException("create outputDir failed!");
        }

        mVideoEncoder = new Encoder();
        mVideoEncoder.encoder = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_AVC);
        int colorFormat = IjkConstant.convertPixelFormatToColorFormat(pixelFormat);
        MediaCodecInfo.CodecCapabilities capabilities = mVideoEncoder.encoder.getCodecInfo().getCapabilitiesForType(MIMETYPE_VIDEO_AVC);
        for (int f : capabilities.colorFormats) {
            info("support color format: %s (%s)", f, f == colorFormat);
        }
        MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        info("video format: %s", format);
        mVideoEncoder.encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mVideoEncoder.encoder.start();

        if (mHasAudio) {
            mAudioEncoder = new Encoder();
            MediaFormat audioFormat = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, 44100, 2);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            info("audio format: %s", audioFormat);
            mAudioEncoder.encoder = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
            mAudioEncoder.encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioEncoder.encoder.start();
        }

        mMuxer = new MediaMuxer(outputFile.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    private boolean tryStartMuxer() {
        if (!mMuxerStarted && mVideoEncoder.outputFormat != null
                && !(mHasAudio && mAudioEncoder.outputFormat == null)) {
            mVideoEncoder.trackIndex = mMuxer.addTrack(mVideoEncoder.outputFormat);
            if (mHasAudio) {
                mAudioEncoder.trackIndex = mMuxer.addTrack(mAudioEncoder.outputFormat);
            }
            mMuxer.start();
            mMuxerStarted = true;
            return true;
        }
        return false;
    }

    public void drainVideoEncoder(boolean waitToEnd) {
        mVideoEncoder.drainEncoder(waitToEnd);
    }

    public void drainAudioEncoder(boolean waitToEnd) {
        if (!mHasAudio) return;
        mAudioEncoder.drainEncoder(waitToEnd);
    }

    public void commitVideoFrame(ByteBuffer buffer, long ptsUs, boolean endOfStream) {
        mVideoEncoder.commitFrame(buffer, ptsUs, endOfStream);
    }

    public void commitAudioFrame(ByteBuffer buffer, long ptsUs, boolean endOfStream) {
        if (!mHasAudio) return;
        mAudioEncoder.commitFrame(buffer, ptsUs, endOfStream);
    }

    public void release() {
        info("release");
        if (mVideoEncoder != null) {
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
        if (mHasAudio && mAudioEncoder != null) {
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    File getOutputFile() {
        return mOutputFile;
    }

    private class Encoder {
        MediaCodec encoder;
        MediaFormat outputFormat;
        int trackIndex = -1;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        public void release() {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
                encoder = null;
            }
        }

        public void commitFrame(ByteBuffer buffer, long ptsUs, boolean endOfStream) {
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            int index = encoder.dequeueInputBuffer(TIMEOUT_DEQUEUE_BUFFER_USEC);
            if (index >= 0) {
                if (endOfStream) {
                    encoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    info("sent input EOS");
                } else {
                    int size = buffer.rewind().remaining(); // in jni, have not reset position to 0, so we need to rewind().
                    ByteBuffer inputBuffer = inputBuffers[index];
                    inputBuffer.put(buffer);
                    encoder.queueInputBuffer(index, 0, size, ptsUs, 0);
                    info("submitted frame to encoder, size=%s, ptsUs=%s", size, ptsUs);
                }
            } else {
                warn("input buffer not available");
            }
        }

        public void drainEncoder(boolean waitToEnd) {
            // info("drainEncoder(%s, %s)", this, waitToEnd);
            if (this.outputFormat != null && !mMuxerStarted) {
                info("mMuxer have not start");
                return;
            }
            if (waitToEnd) {
                info("waitToEnd is true");
            }
            ByteBuffer[] outputBuffers = this.encoder.getOutputBuffers();
            while (true) {
                int index = this.encoder.dequeueOutputBuffer(this.bufferInfo, TIMEOUT_DEQUEUE_BUFFER_USEC);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!waitToEnd) {
                        break;
                    } else {
                        info("wait to end");
                    }
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = this.encoder.getOutputBuffers();
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    assertTrue(this.outputFormat == null, "format changed twice");
                    this.outputFormat = this.encoder.getOutputFormat();
                    if (!tryStartMuxer()) {
                        break; // wait muxer to start
                    }
                } else if (index < 0) {
                    warn("unexpected result from encoder.dequeueOutputBuffer: %s", index);
                } else {
                    ByteBuffer buffer = outputBuffers[index];
                    assertTrue(buffer != null, "ouputBuffers[%s] was null", index);
                    if ((this.bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info("ignore BUFFER_FLAG_CODEC_CONFIG");
                        this.bufferInfo.size = 0;
                    }
                    if (this.bufferInfo.size != 0) {
                        // adjust buffer to match bufferInfo
                        buffer.position(this.bufferInfo.offset);
                        buffer.limit(this.bufferInfo.offset + this.bufferInfo.size);

                        mMuxer.writeSampleData(this.trackIndex, buffer, this.bufferInfo);
                        info("sent %s bytes to muxer", this.bufferInfo.size);
                    }
                    this.encoder.releaseOutputBuffer(index, false);
                    if ((this.bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (!waitToEnd) {
                            warn("reached end of stream unexpectedly");
                        } else {
                            info("end of stream reached");
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "Encoder{encoder=" + encoder + ", trackIndex=" + trackIndex + '}';
        }
    }
}
