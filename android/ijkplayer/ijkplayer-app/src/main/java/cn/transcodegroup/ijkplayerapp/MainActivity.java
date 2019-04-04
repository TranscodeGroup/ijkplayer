package cn.transcodegroup.ijkplayerapp;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.FrameLayout;

import tv.danmaku.ijk.media.example.widget.media.IjkVideoView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout layout = new FrameLayout(this);
        IjkVideoView videoView = new IjkVideoView(this);
        layout.addView(videoView);
        setContentView(layout);
        videoView.setVideoPath("rtmp://118.89.52.73:1935/live/3601_2");
        videoView.start();
    }
}
