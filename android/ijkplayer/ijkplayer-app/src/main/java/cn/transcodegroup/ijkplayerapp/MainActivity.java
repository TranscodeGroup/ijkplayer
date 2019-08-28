package cn.transcodegroup.ijkplayerapp;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
        rootLayout.addView(videoView);
        rootLayout.addView(layout);
        setContentView(rootLayout);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, SettingsFragment.newInstance(), "tag")
                    .commit();
        }

        videoView.setVideoPath("rtmp://118.89.52.73:1935/live/3601_2");
        videoView.start();
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
