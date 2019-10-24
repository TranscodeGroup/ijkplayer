package tv.danmaku.ijk.media.example.widget.media;

import android.annotation.SuppressLint;
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

/**
 * @see com.android.grafika.VideoEncoderCore
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MediaEncoderCore {
    private static final String MIMETYPE_VIDEO_AVC = "video/avc";
    private static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
    private static final int VIDEO_FPS = 30;
    private static final int VIDEO_GOP = 150;
    private static final int VIDEO_I_FRAME_INTERVAL = VIDEO_GOP / VIDEO_FPS;
    public static final int MIN_OUTPUT_DATA_COUNT = VIDEO_FPS;
    private static final int BYTE_SIZE_PCM_16BIT = 2;
    private static final long TIMEOUT_DEQUEUE_BUFFER_USEC = 10_000; // 10ms
    public static final long MAX_WAIT_TO_END_COUNT = 10_000 / (TIMEOUT_DEQUEUE_BUFFER_USEC / 1000); // wait 10s
    @Nullable
    private Encoder mVideoEncoder;
    @Nullable
    private Encoder mAudioEncoder;
    private MediaMuxer mMuxer;
    private long mOutputDataCount = 0;
    private boolean mMuxerStarted = false;
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

    @SuppressLint("WrongConstant")
    public MediaEncoderCore(boolean hasVideo, boolean hasAudio, File outputFile,
                            int width, int height, int bitRate, int pixelFormat,
                            int audioSampleRate, long channelLayout
    ) throws IOException {
        mOutputFile = outputFile;
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            throw new IOException("create outputDir failed!");
        }

        if (hasVideo) {
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
            mVideoEncoder.inputFormat = format;
            mVideoEncoder.encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mVideoEncoder.encoder.start();
        }

        if (hasAudio) {
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
            // audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            info("audio format: %s", audioFormat);
            mAudioEncoder.inputFormat = audioFormat;
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
                && (mVideoEncoder == null || mVideoEncoder.outputFormat != null)
                && (mAudioEncoder == null || mAudioEncoder.outputFormat != null)) {
            if (mVideoEncoder != null) {
                mVideoEncoder.trackIndex = mMuxer.addTrack(mVideoEncoder.outputFormat);
            }
            if (mAudioEncoder != null) {
                mAudioEncoder.trackIndex = mMuxer.addTrack(mAudioEncoder.outputFormat);
            }
            mMuxer.start();
            mMuxerStarted = true;
            return true;
        }
        return false;
    }

    public Encoder getVideoEncoder() {
        return mVideoEncoder;
    }

    public Encoder getAudioEncoder() {
        return mAudioEncoder;
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

    File getOutputFile() {
        return mOutputFile;
    }

    public long getOutputDataCount() {
        return mOutputDataCount;
    }

    public class Encoder {
        MediaCodec encoder;
        MediaFormat inputFormat;
        MediaFormat outputFormat;
        int trackIndex = -1;
        long ptsOffset = 0;
        private long lastPts = 0;
        private int lastSize = 0;

        public Encoder() {
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        public void release() {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
                encoder = null;
            }
        }

        private void info(String format, Object... args) {
            IjkConstant.info(encoder.getName() + ": " + format, args);
        }

        private String stringOf(MediaCodec.BufferInfo info) {
            return String.format("BufferInfo{offset=%s, size=%s, pts=%s, flags=%s}", info.offset, info.size, info.presentationTimeUs, info.flags);
        }

        private void warn(String format, Object... args) {
            IjkConstant.warn(encoder.getName() + ": " + format, args);
        }

        private void verbose(String format, Object... args) {
            IjkConstant.verbose(encoder.getName() + ": " + format, args);
        }

        public long computeEndOfStreamPts() {
            if (MIMETYPE_AUDIO_AAC.equals(inputFormat.getString(MediaFormat.KEY_MIME))) {
                // for audio, we need to compute pts of the end, otherwise it will report "E/MPEG4Writer﹕timestampUs xxx < lastTimestampUs yyy for Audio track"
                // see: https://stackoverflow.com/questions/18857692/muxing-aac-audio-with-androids-mediacodec-and-mediamuxer
                int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                return lastPts + 1_000_000 * lastSize / (sampleRate * channelCount * BYTE_SIZE_PCM_16BIT);
            }
            return lastPts;
        }

        /**
         * @return Whether commitFrame was successful.
         */
        public boolean commitFrame(ByteBuffer buffer, long ptsUs, boolean endOfStream) {
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            int index = encoder.dequeueInputBuffer(TIMEOUT_DEQUEUE_BUFFER_USEC);
            if (index >= 0) {
                ByteBuffer inputBuffer = inputBuffers[index];
                int offset = inputBuffer.position();
                if (endOfStream) {
                    encoder.queueInputBuffer(index, offset, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    info("sent input EOS");
                } else {
                    int size = buffer.rewind().remaining(); // in jni, have not reset position to 0, so we need to rewind().
                    inputBuffer.put(buffer);
                    encoder.queueInputBuffer(index, offset, size, ptsUs, 0);
                    lastPts = ptsUs;
                    lastSize = size;
                    verbose("submitted frame to encoder, size=%s, ptsUs=%s", size, ptsUs);
                }
                return true;
            } else {
                warn("input buffer not available");
                return false;
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
            long waitToEndCount = 0;
            while (true) {
                int index = this.encoder.dequeueOutputBuffer(this.bufferInfo, TIMEOUT_DEQUEUE_BUFFER_USEC);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!waitToEnd) {
                        break;
                    } else {
                        waitToEndCount++;
                        info("wait to end(%s)", waitToEndCount);
                        // prevent infinite loop. in theory, it is impossible....
                        if (waitToEndCount > MAX_WAIT_TO_END_COUNT) {
                            throw new EncodeException.EndOfStreamFailed("wait to end too long.");
                        }
                    }
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = this.encoder.getOutputBuffers();
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    assertTrue(this.outputFormat == null, "format changed twice");
                    this.outputFormat = this.encoder.getOutputFormat();
                    if (!tryStartMuxer()) {
                        info("wait muxer to start, encoder state: %s, %s", mVideoEncoder, mAudioEncoder);
                        break;
                    } else {
                        info("muxer started");
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
                        if (false) {
                            // adjust pts to start from 0
                            if (this.bufferInfo.presentationTimeUs != 0) { // maybe 0 if END_OF_STREAM
                                if (this.ptsOffset == 0) {
                                    this.ptsOffset = this.bufferInfo.presentationTimeUs;
                                    this.bufferInfo.presentationTimeUs = 0;
                                } else {
                                    this.bufferInfo.presentationTimeUs -= this.ptsOffset;
                                }
                            }
                        }
                        // adjust buffer to match bufferInfo
                        buffer.position(this.bufferInfo.offset);
                        buffer.limit(this.bufferInfo.offset + this.bufferInfo.size);
                        mOutputDataCount++;
                        mMuxer.writeSampleData(this.trackIndex, buffer, this.bufferInfo);
                        verbose("sent %s bytes to muxer on %s", this.bufferInfo.size, this.bufferInfo.presentationTimeUs);
                    }
                    this.encoder.releaseOutputBuffer(index, false);
                    if ((this.bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        // video encoder: end of stream with 0size 0pts
                        // audio encoder: end of stream with 316size 62742999pts
                        // todo: why them diff?
                        if (!waitToEnd) {
                            warn("reached end of stream unexpectedly (info: %s)", stringOf(this.bufferInfo));
                        } else {
                            info("end of stream reached (info: %s)", stringOf(this.bufferInfo));
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "Encoder{encoder=" + encoder + ", outputFormat=" + outputFormat + ", trackIndex=" + trackIndex + '}';
        }
    }
}
