package tv.danmaku.ijk.media.example.widget.media;

public class IjkConstant {
    private static int SDL_FOURCC(int a, int b, int c, int d) {
        return a | b << 8 | c << 16 | d << 24;
    }

    public interface Format {
        int SDL_FF_RV32 = SDL_FOURCC('R', 'V', '3', '2');
    }
}
