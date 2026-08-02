package com.la87hn.la87laprimera.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media3.common.util.UnstableApi;

import com.la87hn.la87laprimera.R;
import com.la87hn.la87laprimera.activities.MainActivity;

@UnstableApi
public class MediaNotificationManager {

    public static final int NOTIFICATION_ID = 555;
    public static final String NOTIFICATION_CHANNEL_ID = "myRadio";
    private final RadioService service;
    private String nowPlaying, songName;
    private Bitmap notifyIcon;
    private String playbackStatus;
    private final Resources resources;

    PendingIntent action;
    NotificationCompat.Builder builder;
    NotificationManager notificationManager;

    public MediaNotificationManager(RadioService service) {
        this.service = service;
        this.resources = service.getResources();
    }

    public void startNotify(String playbackStatus) {
        this.playbackStatus = playbackStatus;
        startNotify();
    }

    public void changeIcon(Bitmap notifyIcon) {
        this.notifyIcon = notifyIcon;
        startNotify();
    }

    public void changeRadio(String songName, String nowPlaying) {
        this.songName = songName;
        this.nowPlaying = nowPlaying;
    }

    @SuppressLint("NotificationTrampoline")
    private void startNotify() {
        if (playbackStatus == null) return;

        notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    service.getString(R.string.audio_notification),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.enableVibration(false);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationManagerCompat.from(service).cancel(NOTIFICATION_ID);
        builder = new NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID);

        notifyIcon = BitmapFactory.decodeResource(resources, R.drawable.oficial);
        int icon = R.drawable.ic_button_pause;
        Intent playbackAction = new Intent(service, RadioService.class);
        playbackAction.setAction(RadioService.ACTION_PAUSE);
        action = PendingIntent.getService(
                this.service, 1, playbackAction, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        if (playbackStatus.equals(PlaybackStatus.PAUSED)) {
            icon = R.drawable.ic_button_play;
            playbackAction.setAction(RadioService.ACTION_PLAY);
            action = PendingIntent.getService(
                    this.service, 2, playbackAction, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        Intent stopIntent = new Intent(service, RadioService.class);
        stopIntent.setAction(RadioService.ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(
                service, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent resumeIntent = new Intent(service.getApplicationContext(), MainActivity.class);
        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(
                service.getApplicationContext(), 0, resumeIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        builder.setContentTitle(nowPlaying)
                .setContentText(songName)
                .setLargeIcon(notifyIcon)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_radio_notif)
                .addAction(icon, "Pause", action)
                .addAction(R.drawable.ic_stop, "Stop", stopAction)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVibrate(new long[]{0L})
                .setWhen(System.currentTimeMillis())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(service.getMediaSession().getSessionToken())
                        .setShowActionsInCompactView(0, 1)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(stopAction));

        Notification notification = builder.build();
        service.startForeground(NOTIFICATION_ID, notification);
    }

    public void cancelNotify() {
        service.stopForeground(true);
    }
}
