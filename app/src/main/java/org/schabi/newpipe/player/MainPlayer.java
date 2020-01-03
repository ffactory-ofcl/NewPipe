/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * BackgroundPlayer.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.helper.LockManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;


/**
 * One service for all players
 *
 * @author mauriciocolli
 */
public final class MainPlayer extends Service {
    private static final String TAG = "MainPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private VideoPlayerImpl playerImpl;
    private WindowManager windowManager;
    private LockManager lockManager;

    private final IBinder mBinder = new MainPlayer.LocalBinder();

    public enum PlayerType {
        VIDEO,
        AUDIO,
        POPUP
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    static final int NOTIFICATION_ID = 123789;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;
    private RemoteViews bigNotRemoteView;

    static final String ACTION_CLOSE = "org.schabi.newpipe.player.MainPlayer.CLOSE";
    static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.MainPlayer.PLAY_PAUSE";
    static final String ACTION_OPEN_CONTROLS = "org.schabi.newpipe.player.MainPlayer.OPEN_CONTROLS";
    static final String ACTION_REPEAT = "org.schabi.newpipe.player.MainPlayer.REPEAT";
    static final String ACTION_PLAY_NEXT = "org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_NEXT";
    static final String ACTION_PLAY_PREVIOUS = "org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PREVIOUS";
    static final String ACTION_FAST_REWIND = "org.schabi.newpipe.player.MainPlayer.ACTION_FAST_REWIND";
    static final String ACTION_FAST_FORWARD = "org.schabi.newpipe.player.MainPlayer.ACTION_FAST_FORWARD";

    private static final String SET_IMAGE_RESOURCE_METHOD = "setImageResource";

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate() called");
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        lockManager = new LockManager(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        ThemeHelper.setTheme(this);
        createView();
    }

    private void createView() {
        View layout = View.inflate(this, R.layout.activity_main_player, null);

        playerImpl = new VideoPlayerImpl(this);
        playerImpl.setup(layout);
        playerImpl.shouldUpdateOnProgress = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand() called with: intent = [" + intent +
                "], flags = [" + flags + "], startId = [" + startId + "]");
        playerImpl.handleIntent(intent);
        if (playerImpl.mediaSessionManager != null) {
            playerImpl.mediaSessionManager.handleMediaButtonIntent(intent);
        }
        return START_NOT_STICKY;
    }

    public void stop() {
        if (DEBUG)
            Log.d(TAG, "stop() called");

        if (playerImpl.getPlayer() != null) {
            playerImpl.wasPlaying = playerImpl.getPlayer().getPlayWhenReady();
            playerImpl.getPlayer().stop(false);
            playerImpl.setRecovery();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        onDestroy();
        // Unload from memory completely
        Runtime.getRuntime().halt(0);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        onClose();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Actions
    //////////////////////////////////////////////////////////////////////////*/
    private void onClose() {
        if (DEBUG) Log.d(TAG, "onClose() called");

        if (lockManager != null) {
            lockManager.releaseWifiAndCpu();
        }
        if (playerImpl != null) {
            removeViewFromParent();

            playerImpl.savePlaybackState();
            playerImpl.stopActivityBinding();
            playerImpl.destroy();
        }
        if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
        playerImpl = null;
        lockManager = null;

        stopForeground(true);
        stopSelf();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    boolean isLandscape() {
        // DisplayMetrics from activity context knows about MultiWindow feature while DisplayMetrics from app context doesn't
        final DisplayMetrics metrics = playerImpl.getParentActivity() != null ?
                playerImpl.getParentActivity().getResources().getDisplayMetrics()
                : getResources().getDisplayMetrics();
        return metrics.heightPixels < metrics.widthPixels;
    }

    public View getView() {
        if (playerImpl == null)
            return null;

        return playerImpl.getRootView();
    }

    public void removeViewFromParent() {
        if (getView().getParent() != null) {
            if (playerImpl.getParentActivity() != null) {
                // This means view was added to fragment
                ViewGroup parent = (ViewGroup) getView().getParent();
                parent.removeView(getView());
            } else
                // This means view was added by windowManager for popup player
                windowManager.removeViewImmediate(getView());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    void resetNotification() {
        notBuilder = createNotification();
    }

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification);
        bigNotRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification_expanded);

        setupNotification(notRemoteView);
        setupNotification(bigNotRemoteView);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCustomContentView(notRemoteView)
                .setCustomBigContentView(bigNotRemoteView);
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        return builder;
    }

    private void setupNotification(RemoteViews remoteViews) {
        // Don't show anything until player is playing
        if (playerImpl == null) return;

        remoteViews.setTextViewText(R.id.notificationSongName, playerImpl.getVideoTitle());
        remoteViews.setTextViewText(R.id.notificationArtist, playerImpl.getUploaderName());
        remoteViews.setImageViewBitmap(R.id.notificationCover, playerImpl.getThumbnail());

        remoteViews.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        // Starts VideoDetailFragment or opens BackgroundPlayerActivity.
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(this, NOTIFICATION_ID, getIntentForNotification(), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT));


        if (playerImpl.playQueue != null && playerImpl.playQueue.size() > 1) {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_previous);
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_next);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_NEXT), PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_rewind);
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_fastforward);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_FAST_REWIND), PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_FAST_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT));
        }

        setRepeatModeIcon(remoteViews, playerImpl.getRepeatMode());
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    synchronized void updateNotification(int drawableId) {
        //if (DEBUG) Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        if (notBuilder == null) return;
        if (drawableId != -1) {
            if (notRemoteView != null) notRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
            if (bigNotRemoteView != null) bigNotRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
        }
        notificationManager.notify(NOTIFICATION_ID, notBuilder.build());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void setRepeatModeIcon(final RemoteViews remoteViews, final int repeatMode) {
        if (remoteViews == null) return;

        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    private Intent getIntentForNotification() {
        Intent intent;
        if (playerImpl.audioPlayerSelected() || playerImpl.popupPlayerSelected()) {
            // Means we play in popup or audio only. Let's show BackgroundPlayerActivity
            intent = NavigationHelper.getBackgroundPlayerActivityIntent(getApplicationContext());
        } else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
            intent = NavigationHelper.getPlayerIntent(this, MainActivity.class, null, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        return intent;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/

    LockManager getLockManager() {
        return lockManager;
    }

    NotificationCompat.Builder getNotBuilder() {
        return notBuilder;
    }

    RemoteViews getBigNotRemoteView() {
        return bigNotRemoteView;
    }

    RemoteViews getNotRemoteView() {
        return notRemoteView;
    }


    public class LocalBinder extends Binder {

        public MainPlayer getService() {
            return MainPlayer.this;
        }

        public VideoPlayerImpl getPlayer() {
            return MainPlayer.this.playerImpl;
        }
    }
}