/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.xiaopengwang.mediaplayerdemo20170607;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.Toast;

import com.conviva.api.Client;
import com.conviva.api.ContentMetadata;
import com.conviva.api.ConvivaException;
import com.conviva.api.player.PlayerStateManager;
import com.example.xiaopengwang.mediaplayerdemo20170607.helper.CVMediaPlayerInterface;
import com.example.xiaopengwang.mediaplayerdemo20170607.helper.ConvivaSessionManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnCompletionListener, MediaController.MediaPlayerControl  {

    PlayerStateManager mStateManager;
    CVMediaPlayerInterface mPlayerInterface;

    private MediaPlayer mPlayer;
    private MediaController mMediaController = null;
    private SurfaceView mSurfaceView;
    private Uri contentUri;

    private int mSeekToMs = -1;
    private boolean mIsBackPressed = false;

    // Bitrate values used to simulate bitrate event
    private int[] bitrateKbps = {300, 600, 900, 1200};
    private int index = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        /* Screen always on */
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFormat(PixelFormat.UNKNOWN);
        setContentView(R.layout.player);

        Intent intent = getIntent();
        contentUri = intent.getData();
        String mGatewayUrl = intent.getStringExtra("gatewayUrl");
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mPlayer = new MediaPlayer();
        //2.创建session，在此之前实例化PlayerStateManager对象。创建session表示对一段视频的检测开始
        mStateManager = ConvivaSessionManager.getPlayerStateManager();
        //自定义Tag
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("key", "value");
        //视频的基本信息
        ContentMetadata convivaMetaData = new ContentMetadata();
        convivaMetaData.assetName = "mediaplayer test video";
        convivaMetaData.custom = tags;
//            convivaMetaData.defaultBitrateKbps = -1;
        try {
            if (mStateManager != null)
                mStateManager.setBitrateKbps(-1);
            convivaMetaData.defaultResource = "AKAMAI";
            convivaMetaData.viewerId = "Test Viewer";
            convivaMetaData.applicationName = "ConvivaAndroidSDKVideoView";
            convivaMetaData.streamUrl = contentUri.toString();
            convivaMetaData.streamType = ContentMetadata.StreamType.LIVE;
            convivaMetaData.duration = 0;
            convivaMetaData.encodedFrameRate = -1;
        } catch (ConvivaException e) {
            e.printStackTrace();
        }
        // 创建Session
        ConvivaSessionManager.createConvivaSession(convivaMetaData);
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.RGBA_8888);
    }

    private boolean isSurfaceDestroyed = false;

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isSurfaceDestroyed = false;
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mPlayer.setDisplay(holder);
        mPlayer.setOnPreparedListener(this);
        mPlayer.setOnCompletionListener(this);
        mPlayer.setOnInfoListener(this);
        // 3：player与session绑定，在mPlayer和mStateManager不为null的时候创建
        //正常讲这个Interface文件由我们提供，也可以开发者自己写
        mPlayerInterface = new CVMediaPlayerInterface(mStateManager, mPlayer);
        playVideo();

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isSurfaceDestroyed = true;
    }

    private void playVideo() {
        try {
            if (mPlayer != null) {
                mPlayer.setDataSource(contentUri.toString());
                mPlayer.prepareAsync();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onStop() {
        if (!mIsBackPressed) {
            if (mPlayer != null)
            mSeekToMs = mPlayer.getCurrentPosition();
        }
        super.onStop();
    }
    @Override
    protected void onPause() {
        //这里我们认为按了back键就是退出这个视频，销毁session
        if (mIsBackPressed) {
            //5:销毁session，表示对一段视频的检测结束
            releasePlayer();
            //6;释放mClient，退出app销毁即可
            ConvivaSessionManager.deinitClient();
        }
        super.onPause();
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            releaseAnalytics();
        }
    }

    private void releaseAnalytics() {
        if (mPlayer != null) {
            if (mPlayerInterface != null) {
                mPlayerInterface.cleanup();
                mPlayerInterface = null;
            }
            ConvivaSessionManager.releasePlayerStateManager();
            ConvivaSessionManager.cleanupConvivaSession();
    }
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {

        if(!isSurfaceDestroyed){
            mMediaController = new MediaController(this);
            mMediaController.setMediaPlayer(this);
            mMediaController.setAnchorView(mSurfaceView);
            mMediaController.show(3000);
        }
        if (mSeekToMs > 0) {
            mPlayer.seekTo(mSeekToMs);
            mSeekToMs = -1;
        }
        mp.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {

    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mIsBackPressed = true;
        }
        return super.onKeyDown(keyCode, event);
    }
    // 4,click method,广告，自定义错误，自定义事件，比特率等
    //更新Bitrate
    public void simulateBitrate(View v) {
        if ((index < 0) || (index > bitrateKbps.length - 1)) {
            index = 0; // resetting the counter
        }
        try {
            Toast.makeText(this,"Update Bittare:"+bitrateKbps[index],Toast.LENGTH_LONG).show();
            mStateManager.setBitrateKbps(bitrateKbps[index]);
        } catch (ConvivaException e) {
            e.printStackTrace();
        }
        index++;
    }
    //
    public void simulateError(View v) {
        String ERROR_MSG = "Simulating Error event";
        try {
            Toast.makeText(this,"Report Error:"+ERROR_MSG,Toast.LENGTH_LONG).show();
            mStateManager.sendError(ERROR_MSG, Client.ErrorSeverity.FATAL);
        } catch (ConvivaException e) {
            e.printStackTrace();
        }
    }

    public void podStart(View v) {
        Toast.makeText(this,"POD_START",Toast.LENGTH_LONG).show();
        ConvivaSessionManager.podEvent(ConvivaSessionManager.POD_EVENTS.POD_START);//自定义事件
        ConvivaSessionManager.adStart();
    }

    public void podEnd(View v) {
        Toast.makeText(this,"POD_END",Toast.LENGTH_LONG).show();
        ConvivaSessionManager.adEnd();
        ConvivaSessionManager.podEvent(ConvivaSessionManager.POD_EVENTS.POD_END);//自定义事件

    }

    @Override
    public void start() {
        mPlayer.start();
    }

    @Override
    public void pause() {
        mPlayer.pause();
    }

    @Override
    public int getDuration() {
        return mPlayer.getDuration();
    }

    @Override
    public int getCurrentPosition() {
        return mPlayer.getCurrentPosition();
    }

    @Override
    public void seekTo(int pos) {
        Log.e("tag","seekto"+pos);
        mPlayer.seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return mPlayer.getAudioSessionId();
    }



    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(mMediaController != null )
            mMediaController.show(3000);
        return super.onTouchEvent(event);
    }
}
