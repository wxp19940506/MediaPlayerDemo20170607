package com.example.xiaopengwang.mediaplayerdemo20170607;

import android.app.Application;

import com.example.xiaopengwang.mediaplayerdemo20170607.helper.ConvivaSessionManager;

/**
 * Created by XiaopengWang on 2017/6/22.
 * Email:xiaopeng.wang@qaii.ac.cn
 * QQ:839853185
 * WinXin;wxp19940505
 */

public class BaseApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        String mGatewayURL = "https://testonly.conviva.com";
        ConvivaSessionManager.initClient(this, mGatewayURL);

    }
}
