package tv.danmaku.ijk.media.example.widget.media;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import tv.danmaku.ijk.media.example.widget.media.IjkConstant.Value;

import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.api21;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.assertTrue;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.const2str;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.convertPixelFormatToColorFormat;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.convertPixelFormatToColorFormatLegacy;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.convertToChannelMask;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.info;
import static tv.danmaku.ijk.media.example.widget.media.IjkConstant.warn;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MediaEncoderCore {
    private static final String MIMETYPE_VIDEO_AVC = "video/avc";
    private static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
    private static final int VIDEO_FPS = 30;
    private static final int VIDEO_GOP = 150;
    private static final int VIDEO_I_FRAME_INTERVAL = VIDEO_GOP / VIDEO_FPS;
    private Encoder mVideoEncoder;
    private Encoder mAudioEncoder;
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted = false;
    private static final long TIMEOUT_DEQUEUE_BUFFER_USEC = 10_000; // 10ms
    private boolean mHasAudio;
    private File mOutputFile;
    private static MediaCodecInfo[] sAvailableAvcCodecs;

    static {
        sAvailableAvcCodecs = getAvailableCodecs(MIMETYPE_VIDEO_AVC);
    }

    public static MediaCodecInfo[] getAvailableCodecs(String mimeType) {
        List<MediaCodecInfo> infoList = new ArrayList<>();
        int count = MediaCodecList.getCodecCount();
        for (int i = 0; i < count; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] supportedTypes = codecInfo.getSupportedTypes();
            for (String type : supportedTypes) {
                if (type.equalsIgnoreCase(mimeType)) {
                    infoList.add(codecInfo);
                }
            }
        }
        return infoList.toArray(new MediaCodecInfo[infoList.size()]);
    }

    public static void printEncoder() {
        int count = MediaCodecList.getCodecCount();
        for (int i = 0; i < count; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(MIMETYPE_VIDEO_AVC)) {
                    MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(type);
                    String[] colorFormatNames = new String[capabilities.colorFormats.length];
                    for (int j = 0; j < capabilities.colorFormats.length; j++) {
                        colorFormatNames[j] = const2str(capabilities.colorFormats[j], MediaCodecInfo.CodecCapabilities.class, "COLOR_Format");
                    }
                    info("%s: \n%s", codecInfo.getName(), TextUtils.join("\n", colorFormatNames));
                }
            }
        }
    }

    public MediaEncoderCore(boolean hasAudio, File outputFile,
                            int width, int height, int bitRate, int pixelFormat,
                            int audioSampleRate, long channelLayout
    ) throws IOException {
        mHasAudio = hasAudio;
        mOutputFile = outputFile;
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            throw new IOException("create outputDir failed!");
        }

        mVideoEncoder = new Encoder();
        Value<Integer> outColorFormat = new Value<>();
        MediaCodecInfo videoCodecInfo = selectAvcCodec(pixelFormat, outColorFormat);
        mVideoEncoder.encoder = MediaCodec.createByCodecName(videoCodecInfo.getName());
        MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, outColorFormat.get());
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL);
        info("video format: %s", format);
        mVideoEncoder.encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mVideoEncoder.encoder.start();

        if (mHasAudio) {
            mAudioEncoder = new Encoder();
            int channelCount;
            switch (convertToChannelMask(channelLayout)) {
                case AudioFormat.CHANNEL_OUT_MONO:
                    channelCount = 1;
                    break;
                case AudioFormat.CHANNEL_OUT_STEREO:
                    channelCount = 2;
                    break;
                default:
                    throw new IllegalArgumentException("unsupported channelLayout: " + channelLayout);
            }
            MediaFormat audioFormat = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, audioSampleRate, channelCount);
            // audioFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT); // only support PCM16
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            info("audio format: %s", audioFormat);
            mAudioEncoder.encoder = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
            mAudioEncoder.encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioEncoder.encoder.start();
        }

        mMuxer = new MediaMuxer(outputFile.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    @NonNull
    private MediaCodecInfo selectAvcCodec(int pixelFormat, Value<Integer> outColorFormat) {
        int colorFormat = convertPixelFormatToColorFormatLegacy(pixelFormat);
        MediaCodecInfo codecInfo = selectAvcCodecByColorFormat(colorFormat, outColorFormat);
        if (codecInfo != null) {
            return codecInfo;
        }
        if (api21()) {
            int colorFormatMaybeFlexible = convertPixelFormatToColorFormat(pixelFormat);
            codecInfo = selectAvcCodecByColorFormat(colorFormatMaybeFlexible, outColorFormat);
            if (codecInfo != null) {
                // return codecInfo;
                throw new UnsupportedOperationException(/*TODO 2018-07-25*/"todo support flexible format");
            }
        }
        throw new IllegalArgumentException("unsupported pixelFormat: " + pixelFormat);
    }

    @Nullable
    private MediaCodecInfo selectAvcCodecByColorFormat(int colorFormat, Value<Integer> outColorFormat) {
        for (MediaCodecInfo codecInfo : sAvailableAvcCodecs) {
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(MIMETYPE_VIDEO_AVC);
            for (int format : capabilities.colorFormats) {
                if (format == colorFormat) {
                    outColorFormat.set(format);
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private boolean tryStartMuxer() {
        if (!mMuxerStarted
                && mVideoEncoder.outputFormat != null
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
