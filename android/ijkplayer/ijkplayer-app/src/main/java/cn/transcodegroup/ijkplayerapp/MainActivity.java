package cn.transcodegroup.ijkplayerapp;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import tv.danmaku.ijk.media.example.fragments.SettingsFragment;
import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;

public class MainActivity extends AppCompatActivity {

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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("recreate").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if ("recreate".equals(item.getTitle())) {
            recreate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
