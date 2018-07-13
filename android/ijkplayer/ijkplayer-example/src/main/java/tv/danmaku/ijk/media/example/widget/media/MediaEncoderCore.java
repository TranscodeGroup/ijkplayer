package tv.danmaku.ijk.media.example.widget.media;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.HandlerThread;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MediaEncoderCore {
    private static final String MIMETYPE_VIDEO_AVC = "video/avc";
    private static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
    private static final int COLOR_FormatRGBAFlexible = api23()
            ? MediaCodecInfo.CodecCapabilities.COLOR_FormatRGBAFlexible
            : ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
            ? MediaCodecInfo.CodecCapabilities.COLOR_Format32bitBGRA8888
            : MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888;
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 5;
    public static final String TAG = "tgtrack";
    private Encoder mVideoEncoder;
    private Encoder mAudioEncoder;
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted = false;
    private static final long TIMEOUT_DEQUEUE_BUFFER_USEC = 10_000; // 10ms


    public static boolean api23() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
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

    public MediaEncoderCore(File outputFile, int width, int height, int bitRate, int pixel_format) throws IOException {
        mVideoEncoder = new Encoder();
        if (pixel_format != IjkConstant.Format.SDL_FF_RV32) {
            throw new IllegalArgumentException("pixel format only support SDL_FF_RV32");
        }
        MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatRGBAFlexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        info("video format: %s", format);
        mVideoEncoder.encoder = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_AVC);
        mVideoEncoder.encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mVideoEncoder.encoder.start();

        mAudioEncoder = new Encoder();
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, 44100, 2);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        info("audio format: %s", audioFormat);
        mAudioEncoder.encoder = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
        mAudioEncoder.encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.encoder.start();

        mMuxer = new MediaMuxer(outputFile.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    private boolean tryStartMuxer() {
        if (!mMuxerStarted && mVideoEncoder.outputFormat != null && mAudioEncoder.outputFormat != null) {
            mVideoEncoder.trackIndex = mMuxer.addTrack(mVideoEncoder.outputFormat);
            mAudioEncoder.trackIndex = mMuxer.addTrack(mAudioEncoder.outputFormat);
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
        mAudioEncoder.drainEncoder(waitToEnd);
    }

    public void commitVideoFrame(ByteBuffer buffer, long pts, boolean endOfStream) {
        mVideoEncoder.commitFrame(buffer, pts, endOfStream);
    }

    public void commitAudioFrame(ByteBuffer buffer, long pts, boolean endOfStream) {
        mAudioEncoder.commitFrame(buffer, pts, endOfStream);
    }

    public void release() {
        info("release");
        if (mVideoEncoder != null) {
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
        if (mAudioEncoder != null) {
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
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

        public void commitFrame(ByteBuffer buffer, long pts, boolean endOfStream) {
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            int index = encoder.dequeueInputBuffer(TIMEOUT_DEQUEUE_BUFFER_USEC);
            if (index >= 0) {
                if (endOfStream) {
                    encoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    info("sent input EOS");
                } else {
                    int size = buffer.remaining();
                    long ptsUs = pts;
                    ByteBuffer inputBuffer = inputBuffers[index];
                    inputBuffer.put(buffer);
                    encoder.queueInputBuffer(index, 0, size, ptsUs, 0);
                    info("submitted frame to encoder, size=%s", size);
                }
            } else {
                info("input buffer not available");
            }
        }

        public void drainEncoder(boolean waitToEnd) {
            info("drainEncoder(%s, %s)", this, waitToEnd);
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
