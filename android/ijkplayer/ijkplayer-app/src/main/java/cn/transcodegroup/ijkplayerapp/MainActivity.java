package cn.transcodegroup.ijkplayerapp;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.File;
import java.util.Date;

import tv.danmaku.ijk.media.example.fragments.SettingsFragment;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.example.widget.media.MediaRecorder;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private MediaRecorder mMediaRecorder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        FrameLayout layout = new FrameLayout(this);
        IjkVideoView videoView = new IjkVideoView(this);
        layout.setId(R.id.fragmentContainer);
        rootLayout.addView(videoView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2));
        rootLayout.addView(layout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        rootLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]");
            }
        });
        setContentView(rootLayout);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, SettingsFragment.newInstance(), "tag")
                    .commit();
        }

        videoView.setBackgroundColor(0x11ff0000);
        videoView.getGestureHelper().setMinZoom(0.5f);
        // videoView.setVideoPath("http://192.240.127.34:1935/live/cs19.stream/play.m3u8"); // yuvj420p
        videoView.setVideoPath("http://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear1/prog_index.m3u8"); // yuv420p
        videoView.start();
        videoView.setKeepScreenOn(true);
        mMediaRecorder = new MediaRecorder(videoView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("recreate").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add("record").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getTitle().toString()) {
            case "recreate":
                recreate();
                break;
            case "record":
                if (mMediaRecorder.isRecording()) {
                    mMediaRecorder.stopRecording();
                } else {
                    mMediaRecorder.startRecording(new File(getExternalFilesDir(null), new Date().toString() + ".mp4"), new MediaRecorder.Callback() {
                        @Override
                        public void onStarted(MediaRecorder.EncodeThread thread) {
                            Log.d(TAG, "onStarted() called with: thread = [" + thread + "]");
                        }

                        @Override
                        public void onFailed(@Nullable MediaRecorder.EncodeThread thread, Exception e) {
                            Log.e(TAG, "onFailed() called with: thread = [" + thread + "], e = [" + e + "]");
                            e.printStackTrace();
                        }

                        @Override
                        public void onCompleted(MediaRecorder.EncodeThread thread, boolean reasonIsFormatChanged) {
                            Log.d(TAG, "onCompleted() called with: thread = [" + thread + "], reasonIsFormatChanged = [" + reasonIsFormatChanged + "]");
                        }
                    });
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
