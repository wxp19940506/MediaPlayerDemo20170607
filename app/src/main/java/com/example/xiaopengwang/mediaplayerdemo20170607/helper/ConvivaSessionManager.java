/*! (C) 2015 Conviva, Inc. All rights reserved. Confidential and
proprietary. */


package com.example.xiaopengwang.mediaplayerdemo20170607.helper;

import android.content.Context;
import android.util.Log;

import com.conviva.api.*;
import com.conviva.api.player.*;
import com.conviva.api.system.SystemInterface;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class that manages Conviva Client, Session, PlayerStateManager and integration API calls.
 */
public class ConvivaSessionManager {
    private static boolean initialized = false;
    private static PlayerStateManager mPlayerStateManager = null;
    private static SystemInterface mAndroidSystemInterface;
    private static SystemFactory mAndroidSystemFactory;
    private static SystemSettings mSystemSettings;
    private static ClientSettings mClientSettings;
    private static Client mClient = null;

    public static int mSessionKey = -1;
    private static final String PLAYER = "ConvivaSessionManager";
    //替换您的Key值
    private static final String customerKey = "1a6d7f0de15335c201e8e9aacbc7a0952f5191d7";
    // The customerKey on Production for customer "c3.DryRun".

    public enum POD_EVENTS {
        POD_START,
        POD_END
    }

    /**
     * Should be called first
     */
    public static Client initClient(Context context, String gatewayUrl) {
        try {
            if (!initialized) {
                mAndroidSystemInterface = AndroidSystemInterfaceFactory.build(context);
                mSystemSettings = new SystemSettings();
                // Do not use DEBUG for production app
                mSystemSettings.logLevel = SystemSettings.LogLevel.DEBUG;
                mSystemSettings.allowUncaughtExceptions = false;
                mAndroidSystemFactory = new SystemFactory(mAndroidSystemInterface, mSystemSettings);
                mClientSettings = new ClientSettings(customerKey);
                if(gatewayUrl != null && !gatewayUrl.isEmpty()) {
                    mClientSettings.gatewayUrl = gatewayUrl;            // client should provide a proper gateway url
                }

                mClient = new Client(mClientSettings, mAndroidSystemFactory);
                initialized = true;
            }

        } catch (Exception ex) {
            Log.e(PLAYER, "Failed to initialize LivePass");
            ex.printStackTrace();
        }
        return mClient;
    }

    // return new playerStateManager
    public static PlayerStateManager getPlayerStateManager() {
        if (mPlayerStateManager == null) {
            mPlayerStateManager = new PlayerStateManager(mAndroidSystemFactory);
        }
        return mPlayerStateManager;
    }

    public static void releasePlayerStateManager() {
        try {
            if (mPlayerStateManager != null) {
                mPlayerStateManager.release();
                mPlayerStateManager = null;
            }
        } catch (Exception e) {
            Log.e(PLAYER, "Failed to release mPlayerStateManager");
        }
    }

    public static void deinitClient() {
        if (!initialized)
            return;

        if (mClient == null) {
            Log.w(PLAYER, "Unable to deinit since client has not been initialized");
            return;
        }

        if (mAndroidSystemFactory != null)
            mAndroidSystemFactory.release();
        try {
            releasePlayerStateManager();
            mClient.release();
        } catch (Exception e) {
            Log.e(PLAYER, "Failed to release client");
        }

        mAndroidSystemFactory = null;
        mClient = null;
        initialized = false;
    }

    /**
     * Called when player has been created and the media url is known.
     * Note that:
     * This function may be called multiple times by the same player and
     * for different sessions,
     * @param convivaMetaData
     */
    public static void createConvivaSession(ContentMetadata convivaMetaData) {
        if (!initialized || mClient == null) {
            Log.e(PLAYER, "Unable to create session since client not initialized");
            return;
        }

        try {
            if (mSessionKey != -1) {
                cleanupConvivaSession();
            }
        } catch (Exception e) {
            Log.e(PLAYER, "Unable to cleanup session: " + e.toString());
        }

        try {
//            //自定义Tag
//            Map<String, String> tags = new HashMap<String, String>();
//            tags.put("key", "value");
//            //视频的基本信息
//            ContentMetadata convivaMetaData = new ContentMetadata();
//            convivaMetaData.assetName = "mediaplayer test video";
//            convivaMetaData.custom = tags;
////            convivaMetaData.defaultBitrateKbps = -1;
//            mPlayerStateManager.setBitrateKbps(-1);
//            convivaMetaData.defaultResource = "AKAMAI";
//            convivaMetaData.viewerId = "Test Viewer";
//            convivaMetaData.applicationName = "ConvivaAndroidSDKVideoView";
//            convivaMetaData.streamUrl = mediaUrl;
//            convivaMetaData.streamType = ContentMetadata.StreamType.VOD;
//            convivaMetaData.duration = 0;
//            convivaMetaData.encodedFrameRate = -1;
            mSessionKey = mClient.createSession(convivaMetaData);
            mClient.attachPlayer(mSessionKey, mPlayerStateManager);

        } catch (Exception ex) {
            Log.e(PLAYER, "Failed to create session");
            ex.printStackTrace();
        }
    }

    /**
     * Called after video session has completed
     */
    public static void cleanupConvivaSession() {
        if (!initialized || mClient == null) {
            Log.w(PLAYER, "Unable to clean session since client not initialized");
            return;
        }

        if (mSessionKey != -1) {
            Log.d(PLAYER, "cleanup session: " + mSessionKey);
            try {
                mClient.cleanupSession(mSessionKey);

            } catch (Exception ex) {
                Log.e(PLAYER, "Failed to cleanup");
                ex.printStackTrace();
            }
            mSessionKey = -1;
        }
    }

    public static void reportError(String err, boolean fatal) {
        if (!initialized || mClient == null) {
            Log.e(PLAYER, "Unable to report error since client not initialized");
            return;
        }

        Client.ErrorSeverity severity = fatal ? Client.ErrorSeverity.FATAL : Client.ErrorSeverity.WARNING;
        try {
            mClient.reportError(mSessionKey, err, severity);
        } catch (Exception ex) {
            Log.e(PLAYER, "Failed to report error");
            ex.printStackTrace();
        }
    }

    public static void adStart() {
        if (!initialized || mClient == null) {
            Log.e(PLAYER, "Unable to start Ad since client not initialized");
            return;
        }

        if (mSessionKey == -1) {
            Log.e(PLAYER, "adStart() requires a session");
            return;
        }
        try {
            mClient.adStart(mSessionKey, Client.AdStream.SEPARATE,
                    Client.AdPlayer.SEPARATE,
                    Client.AdPosition.PREROLL);
        } catch (Exception ex) {
            Log.e(PLAYER, "Failed to start Ad");
            ex.printStackTrace();
        }
    }

    public static void adEnd() {
        if (!initialized || mClient == null) {
            Log.e(PLAYER, "Unable to stop Ad since client not initialized");
            return;
        }

        if (mSessionKey == -1) {
            Log.e(PLAYER, "adEnd() requires a session");
            return;
        }
        try {
            mClient.adEnd(mSessionKey);
        } catch (Exception ex) {
            Log.e(PLAYER, "Failed to end Ad");
            ex.printStackTrace();
        }
    }

    public static void podEvent(POD_EVENTS event) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("podDuration", "60");
        attributes.put("podPosition", "Pre-roll");
        attributes.put("podIndex", "1");

        try {
            switch (event) {
                case POD_START:
                    mClient.sendCustomEvent(mSessionKey, "Conviva.PodStart", attributes);
                    break;

                case POD_END:
                    mClient.sendCustomEvent(mSessionKey, "Conviva.PodEnd", attributes);
                    break;
                default:
                    break;
            }
        } catch (ConvivaException e) {
            e.printStackTrace();
        }

    }
}
