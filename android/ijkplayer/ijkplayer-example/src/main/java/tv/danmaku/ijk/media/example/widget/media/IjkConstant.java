package tv.danmaku.ijk.media.example.widget.media;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.AudioFormat;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static tv.danmaku.ijk.media.player.IjkMediaMeta.AV_CH_LAYOUT_MONO;
import static tv.danmaku.ijk.media.player.IjkMediaMeta.AV_CH_LAYOUT_STEREO;

public class IjkConstant {

    public static final String TAG = "tgtrack";

    private static int SDL_FOURCC(int a, int b, int c, int d) {
        return a | b << 8 | c << 16 | d << 24;
    }

    private static SparseIntArray sPixelFormatToColorFormatAuto = new SparseIntArray();
    private static SparseIntArray sPixelFormatToColorFormatLegacy = new SparseIntArray();

    static {
        sPixelFormatToColorFormatLegacy.put(PixelFormat.AV_PIX_FMT_YUV420P, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        if (api21()) {
            sPixelFormatToColorFormatAuto.put(PixelFormat.AV_PIX_FMT_YUV420P, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        } else {
            sPixelFormatToColorFormatAuto.put(PixelFormat.AV_PIX_FMT_YUV420P, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        }

        sPixelFormatToColorFormatLegacy.put(PixelFormat.AV_PIX_FMT_YUV422P, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Planar);
        if (api23()) {
            sPixelFormatToColorFormatAuto.put(PixelFormat.AV_PIX_FMT_YUV422P, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Flexible);
        } else {
            sPixelFormatToColorFormatAuto.put(PixelFormat.AV_PIX_FMT_YUV422P, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Planar);
        }
    }

    public static int convertPixelFormatToColorFormat(int pixelFormat) {
        return convertPixelFormatToColorFormat(sPixelFormatToColorFormatAuto, pixelFormat);
    }

    public static int convertPixelFormatToColorFormatLegacy(int pixelFormat) {
        return convertPixelFormatToColorFormat(sPixelFormatToColorFormatLegacy, pixelFormat);
    }

    public static int convertToChannelMask(long nativeChannelLayout) {
        if (nativeChannelLayout == AV_CH_LAYOUT_MONO) {
            return AudioFormat.CHANNEL_OUT_MONO;
        } else if (nativeChannelLayout == AV_CH_LAYOUT_STEREO) {
            return AudioFormat.CHANNEL_OUT_STEREO;
        }
        throw new UnsupportedOperationException("unsupported nativeChannelLayout: " + nativeChannelLayout);
    }

    private static int convertPixelFormatToColorFormat(SparseIntArray pixelFormatToColorFormat, int pixelFormat) {
        int colorFormat = pixelFormatToColorFormat.get(pixelFormat, -1);
        if (colorFormat == -1) {
            throw new UnsupportedOperationException("unsupported pixelFormat:" + pixelFormat);
        }
        return colorFormat;
    }

    public static Bitmap convertNV21ToBitmap(ByteBuffer buffer, int width, int height) {
        buffer.rewind();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        YuvImage image = new YuvImage(bytes, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean success = image.compressToJpeg(new Rect(0, 0, width, height), 100, baos);
        byte[] data = baos.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, null);
        return bitmap;
    }

    public static boolean saveBufferToFile(ByteBuffer buffer, File file, boolean append) {
        if (!append && file.exists()) {
            return false;
        }
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            warn("create dir %s filed.", file.getParentFile());
            return false;
        }
        try (FileOutputStream fos = new FileOutputStream(file, append)) {
            byte[] bytes = new byte[buffer.rewind().remaining()];
            buffer.get(bytes);
            fos.write(bytes);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String generateNowTime4File(boolean withMs) {
        String template = "yyyyMMddHHmmss";
        if (withMs) {
            template += "SSS";
        }
        return new SimpleDateFormat(template, Locale.US).format(new Date());
    }

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

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String const2str(Object value, Class clz, String prefix) {
        String constName = "unknown";
        try {
            Field[] fields = clz.getFields();
            for (Field field : fields) {
                int modifiers = field.getModifiers();
                String name = field.getName();
                if (name.startsWith(prefix) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                    if (Objects.equals(field.get(null), value)) {
                        constName = name;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return String.format("%s(%s)", constName, value);
    }

    public interface Format {
        int INVALID = -1;
        int SDL_FF_RV32 = SDL_FOURCC('R', 'V', '3', '2');
    }

    public interface PixelFormat {
        int AV_PIX_FMT_YUV420P = 0;
        int AV_PIX_FMT_YUYV422 = 1;
        int AV_PIX_FMT_RGB24 = 2;
        int AV_PIX_FMT_BGR24 = 3;
        int AV_PIX_FMT_YUV422P = 4;
        int AV_PIX_FMT_YUV444P = 5;
        int AV_PIX_FMT_YUV410P = 6;
        int AV_PIX_FMT_YUV411P = 7;
        int AV_PIX_FMT_GRAY8 = 8;
        int AV_PIX_FMT_MONOWHITE = 9;
        int AV_PIX_FMT_MONOBLACK = 10;
        int AV_PIX_FMT_PAL8 = 11;
        int AV_PIX_FMT_YUVJ420P = 12;
        int AV_PIX_FMT_YUVJ422P = 13;
        int AV_PIX_FMT_YUVJ444P = 14;
    }

    public interface SampleFormat {
        int AV_SAMPLE_FMT_NONE = -1;
        int AV_SAMPLE_FMT_U8 = 0;          ///< unsigned 8 bits
        int AV_SAMPLE_FMT_S16 = 1;         ///< signed 16 bits
        int AV_SAMPLE_FMT_S32 = 2;         ///< signed 32 bits
        int AV_SAMPLE_FMT_FLT = 3;         ///< float
        int AV_SAMPLE_FMT_DBL = 4;         ///< double
        int AV_SAMPLE_FMT_U8P = 5;         ///< unsigned 8 bits, planar
        int AV_SAMPLE_FMT_S16P = 6;        ///< signed 16 bits, planar
        int AV_SAMPLE_FMT_S32P = 7;        ///< signed 32 bits, planar
        int AV_SAMPLE_FMT_FLTP = 8;        ///< float, planar
        int AV_SAMPLE_FMT_DBLP = 9;        ///< double, planar
        int AV_SAMPLE_FMT_S64 = 10;         ///< signed 64 bits
        int AV_SAMPLE_FMT_S64P = 11;        ///< signed 64 bits, planar
        int AV_SAMPLE_FMT_NB = 12;          ///< Number of sample formats. DO NOT USE if linking dynamically
    }

    public static class Value<T> {
        private T value;

        public Value() {
            this(null);
        }

        public Value(T value) {
            this.value = value;
        }

        public T get() {
            return this.value;
        }

        public void set(T value) {
            this.value = value;
        }
    }
}
