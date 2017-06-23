// Copyright (c) 2014, Conviva Inc.  All rights reserved.
package com.example.xiaopengwang.mediaplayerdemo20170607.helper;

import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import com.conviva.api.Client;
import com.conviva.api.ContentMetadata;
import com.conviva.api.ConvivaException;
import com.conviva.api.SystemSettings;
import com.conviva.api.player.IClientMeasureInterface;
import com.conviva.api.player.IPlayerInterface;
import com.conviva.api.player.PlayerStateManager;
import com.conviva.api.player.PlayerStateManager.PlayerState;
import com.conviva.api.system.ICancelTimer;
import com.conviva.api.system.ITimerInterface;
import com.conviva.platforms.android.AndroidTimerInterface;

import java.lang.reflect.Field;

/**
 * CVMediaPlayerInterface is used to collect and monitor data from the video played on assigned MediaPlayer instance and reports the data to Conviva Android SDK.
 * 该文件主要是对player播放状态，错误，播放器size改变等做的操作，conviva中制订了五个标准状态来评估视频状态，Playing，Buffering，Pause，Stopped，Unknow。
 * 在本方案中，Buffering，Pause，Playing是通过timer对比position得到的，第一次的Buffering和Stopped是通过监听时间得到的。还有error和player size的值也是通过监听事件获得。
 */
public class CVMediaPlayerInterface implements MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnVideoSizeChangedListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnSeekCompleteListener, IPlayerInterface, IClientMeasureInterface {

    public PlayerStateManager mStateManager = null;
    public MediaPlayer _mPlayer = null;
    //列举出所有的Linister
    public MediaPlayer.OnErrorListener _onErrorListenerOrig = null;
    public MediaPlayer.OnPreparedListener _onPreparedListenerOrig = null;
    public MediaPlayer.OnCompletionListener _onCompListenerOrig = null;
    public MediaPlayer.OnInfoListener _onInfoListenerOrig = null;
    public MediaPlayer.OnSeekCompleteListener _onSeekListenerOrig = null;
    //记录上次position的位置，结合timer判断状态。
    public long _previousPosition = 0;
    //记录上次播放状态，结合timer判断状态。
    public PlayerState _previousState = PlayerState.UNKNOWN;

    // We want to ignore the first playing states that reported.
    public int _playingStatesCounter = 0;

    /**
     * Flag to check the player prepared and playback completed state. Set to
     * true one player is prepared and PHT values can be queried. Reset it to
     * false when playback is completed.
     */
    public boolean _mIsPlayerActive = false;
    public boolean _inListener = false; // True if executing listener (to prevent infinite recursion)

    /** ---------- Error code definition for MediaPlayer starts------------ */
    //去player的源码中找这些Error，并全部列举出来,例如MediaPlayer.MEDIA_ERROR_UNKNOWN，点进去就可以看到所有error

    private static final String ERR_UNKNOWN = "MEDIA_ERROR_UNKNOWN";
    private static final String ERR_SERVERDIED = "MEDIA_ERROR_SERVER_DIED";
    private static final String ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
    //定时器，因为没有监听可以获取Paused，Playing等状态，所以使用定时器对比上次采样position和本次position值判断状态
    //如果每种状态都有监听就不需要定时器了
    public ICancelTimer _mCancelTimer = null;

    /** ---------- Error code definition for MediaPlayer end------------ */
    //根据position，判断状态的定时任务
    private Runnable _pollStreamerTask = new Runnable() {
        @Override
        public void run() {
            GetPlayheadTimeMs();
        }
    };

    /**
     * Creates an instance of CVMediaPlayerInterface.
     * @param playerStateManager PlayerStateManager Instance
     * @param player MediaPlayer Instance
     */
    public CVMediaPlayerInterface(PlayerStateManager playerStateManager, MediaPlayer player) {
        if (playerStateManager == null) {
            Log("CVMediaPlayerInterface(): Null playerStateManager argument", SystemSettings.LogLevel.ERROR);
            return;
        }

        if (player == null) {
            Log("CVMediaPlayerInterface(): Null Player argument", SystemSettings.LogLevel.ERROR);
            return;
        }
        mStateManager = playerStateManager;
        mStateManager.setPlayerType("MediaPlayer");//player类型
        mStateManager.setPlayerVersion(Build.VERSION.RELEASE);//player版本
        mStateManager.setClientMeasureInterface(this);//三个回调，getPHT,getBufferLength,getSignalStrength
        _mPlayer = player;
        //定时器200ms执行一次任务
        ITimerInterface iTimerInterface = new AndroidTimerInterface();
        _mCancelTimer = iTimerInterface.createTimer(_pollStreamerTask, 200, "CVMediaPlayerInterface");
        //利用反射，for循环获取Linister属性并调用。模仿这种类型写就可以
        try {
            for (Field f : MediaPlayer.class.getDeclaredFields()) {
                Class<?> t = f.getType();
                String n = f.getName();
                if (MediaPlayer.OnErrorListener.class.equals(t) && n.startsWith("mOn")) {
                    f.setAccessible(true);
                    _onErrorListenerOrig = (MediaPlayer.OnErrorListener)f.get(_mPlayer);
                } else if (MediaPlayer.OnPreparedListener.class.equals(t)&& n.startsWith("mOn")) {
                    f.setAccessible(true);
                    _onPreparedListenerOrig = (MediaPlayer.OnPreparedListener) f.get(_mPlayer);
                } else if (MediaPlayer.OnCompletionListener.class.equals(t) && n.startsWith("mOn")) {
                    f.setAccessible(true);
                    _onCompListenerOrig = (MediaPlayer.OnCompletionListener) f.get(_mPlayer);
                } else if (MediaPlayer.OnInfoListener.class.equals(t) && n.startsWith("mOn")) {
                    f.setAccessible(true);
                    _onInfoListenerOrig = (MediaPlayer.OnInfoListener) f.get(_mPlayer);
                } else if (MediaPlayer.OnSeekCompleteListener.class.equals(t) && n.startsWith("mOn")) {
                    f.setAccessible(true);
                    _onSeekListenerOrig = (MediaPlayer.OnSeekCompleteListener) f.get(_mPlayer);
                }
            }
            //为player设置监听
            _mPlayer.setOnInfoListener(this);
            _mPlayer.setOnErrorListener(this);
            _mPlayer.setOnPreparedListener(this);
            _mPlayer.setOnCompletionListener(this);
            _mPlayer.setOnSeekCompleteListener(this);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void Log(String message, SystemSettings.LogLevel logLevel) {
        String TAG = "CVMediaPlayerInterface";
        switch (logLevel) {
            case DEBUG:
                Log.d(TAG, message);
                break;
            case INFO:
                Log.i(TAG, message);
                break;
            case WARNING:
                Log.w(TAG, message);
                break;
            case ERROR:
                Log.e(TAG, message);
                break;
            case NONE:
                break;
            default:
                break;
        }
    }
    //清除操作
    public void cleanup() {
        if(_mCancelTimer != null) {
            _mCancelTimer.cancel();
        }
        mStateManager = null;

        if(_mPlayer != null) {
            _mPlayer.setOnPreparedListener(_onPreparedListenerOrig);
            _mPlayer.setOnErrorListener(_onErrorListenerOrig);
            _mPlayer.setOnCompletionListener(_onCompListenerOrig);
            _mPlayer.setOnInfoListener(_onInfoListenerOrig);
            _mPlayer.setOnSeekCompleteListener(_onSeekListenerOrig);
            _mPlayer = null;
        }

        _onInfoListenerOrig = null;
        _onPreparedListenerOrig = null;
        _onErrorListenerOrig = null;
        _onCompListenerOrig = null;
        _onSeekListenerOrig = null;
    }
   //定时任务执行的方法
    public int GetPlayheadTimeMs() {
        int currPos = -1;
        try {

            //Check if player is not null and player is prepared before pht values are queried.
            if(_mPlayer != null && _mIsPlayerActive) {
                currPos = _mPlayer.getCurrentPosition();

                //isPlaying 在Playing或Buffering状态为true
                if(_mPlayer.isPlaying()) {

                    // i如果当前position和前一次position相同
                    if(currPos == _previousPosition  ) {
                        updateState(PlayerState.BUFFERING);
                    } else {
                        updateState(PlayerState.PLAYING);
                    }
                    _previousPosition = currPos;
                } else {

                    //If isPlaying is false, player is paused.
                    updateState(PlayerState.PAUSED);
                }
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        return currPos;
    }
    //更新状态
    public void updateState(PlayerState newState) {
            if (_previousState != newState && mStateManager != null) {

            if (newState == PlayerState.PLAYING) {
                _playingStatesCounter += 1;
                if (_playingStatesCounter == 1) {
                    return;
                } else {
                    _playingStatesCounter = 2; // just set to 2 is enough
                }
            } else {
                _playingStatesCounter = 0; // reset to 0 for every other states.
            }

            _previousState = newState;
            try {
                mStateManager.setPlayerState(newState);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    //更新分辨率
    public void updateResolution(int width, int height) {
        if(mStateManager != null) {
            try {
                mStateManager.setVideoWidth(width);
                mStateManager.setVideoHeight(height);
            } catch (ConvivaException e) {
                e.printStackTrace();
            }
        }
    }
    //errorLinister回调方法
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {

//        Log("OnError : Error occurred", SystemSettings.LogLevel.DEBUG);
        if (_inListener)
            return true;

        if(mStateManager != null) {
            Log("Proxy: onError (" + what + ", " + extra + ")", SystemSettings.LogLevel.DEBUG);
            String errorCode = null;
            if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                errorCode = ERR_UNKNOWN;
            } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                errorCode = ERR_SERVERDIED;
            } else if (what == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                errorCode = ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK;
            } else {
                errorCode = ERR_UNKNOWN;
            }

            try {
                mStateManager.sendError(errorCode, Client.ErrorSeverity.FATAL);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // clean up session if the error causes video start failure
        //可以观察到，每个回掉都会有这种写法，如果不理解，对照写就行，目的是不影响外面对监听事件的使用。
        if (_onErrorListenerOrig != null) {
            _inListener = true;
            try {
                return _onErrorListenerOrig.onError(mp, what, extra);
            } finally {
                _inListener = false;
            }
        }
        return true;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        Log("OnPrepared", SystemSettings.LogLevel.DEBUG);
        if (_inListener)
            return;

        updateState(PlayerState.BUFFERING);//执行该方法时，player处于Buffering状态
        updateResolution(_mPlayer.getVideoHeight(), _mPlayer.getVideoWidth());//传递此时player的宽高或者说是分辨率

        _mIsPlayerActive = true;


        if(mp != null){
            int duration = mp.getDuration();
            if (mStateManager != null && duration > 0) {
                try {
//                    mStateManager.setDuration(duration / 1000);废弃的api，下面是最新的
                    //获取视频时长传递给conviva
                    ContentMetadata metadata = new ContentMetadata();
                    metadata.duration = duration / 1000;
                    Log.e("time",metadata.duration+"");
                    mStateManager.updateContentMetadata(metadata);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        //可以观察到，每个回掉都会有这种写法，如果不理解，对照写就行，目的是不影响外面对监听事件的使用。

        if (_onPreparedListenerOrig != null) {
            _inListener = true;
            try {
                _onPreparedListenerOrig.onPrepared(mp);
            } finally {
                _inListener = false;
            }
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Log("onCompletion", SystemSettings.LogLevel.DEBUG);
        if (_inListener)
            return;
        _mIsPlayerActive = false;
        updateState(PlayerState.STOPPED);
        //可以观察到，每个回调都会有这种写法，目的是不影响外面对监听事件的使用。如果不理解，对照写就行。
        if (_onCompListenerOrig != null) {
            _inListener = true;
            try {
                _onCompListenerOrig.onCompletion(mp);
            } finally {
                _inListener = false;
            }
        }
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        updateResolution(width, height);
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        //可以观察到，每个回掉都会有这种写法，如果不理解，对照写就行，目的是不影响外面对监听事件的使用。

        if(_onInfoListenerOrig != null) {
            _onInfoListenerOrig.onInfo(mp, what, extra);
        }

        return false;
    }

    @Override
    public long getPHT() {
        if(_mPlayer != null && _mIsPlayerActive){
            return _mPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public int getBufferLength() {
        return 0;
    }

    @Override
    public double getSignalStrength() {
        return 0;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        if(mStateManager != null) {
            try {
                mStateManager.setPlayerSeekEnd();
            } catch (ConvivaException e) {
                Log("Exception occurred during Seek End", SystemSettings.LogLevel.ERROR);
            }
        }

        if(_onSeekListenerOrig != null) {
            _onSeekListenerOrig.onSeekComplete(mp);
        }
    }
}
