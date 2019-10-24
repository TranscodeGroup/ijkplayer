package tv.danmaku.ijk.media.example.widget.media;

public class EncodeException extends RuntimeException {
    public EncodeException() {
    }

    public EncodeException(String message) {
        super(message);
    }

    public EncodeException(String message, Throwable cause) {
        super(message, cause);
    }

    public EncodeException(Throwable cause) {
        super(cause);
    }


    public static class OutputDataTooLittle extends EncodeException {
        public OutputDataTooLittle(Throwable cause) {
            super(cause);
        }
    }

    public static class EndOfStreamFailed extends EncodeException {
        public EndOfStreamFailed(String message) {
            super(message);
        }
    }
}
