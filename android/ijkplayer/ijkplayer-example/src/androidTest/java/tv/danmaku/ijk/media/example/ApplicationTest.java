package tv.danmaku.ijk.media.example;

import android.app.Application;
import android.test.ApplicationTestCase;

import org.junit.Test;

import tv.danmaku.ijk.media.example.widget.media.MediaEncoderCore;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends ApplicationTestCase<Application> {
    public ApplicationTest() {
        super(Application.class);
    }
    @Test
    public void testCodec(){
        MediaEncoderCore.printEncoder();
    }
}