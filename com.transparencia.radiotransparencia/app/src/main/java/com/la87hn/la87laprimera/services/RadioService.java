package com.la87hn.la87laprimera.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import com.la87hn.la87laprimera.R;
import com.la87hn.la87laprimera.activities.MainActivity;
import com.la87hn.la87laprimera.utils.HttpsTrustManager;
import com.la87hn.la87laprimera.utils.Tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@UnstableApi public class RadioService extends Service implements Player.Listener, AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = ".ACTION_PLAY";
    public static final String ACTION_PAUSE = ".ACTION_PAUSE";
    public static final String ACTION_STOP = ".ACTION_STOP";
    private final IBinder iBinder = new LocalBinder();

    ExoPlayer exoPlayer;
    DataSource.Factory dataSourceFactory;
    DefaultBandwidthMeter bandwidthMeter;
    MediaSource newMediaSource;

    public static MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;
    private WifiManager.WifiLock wifiLock;
    private AudioManager audioManager;
    public static MediaNotificationManager notificationManager;

    private String status;
    private String nowPlaying;
    public static String songName = "";
    public static String artWorkUrl = "";
    private String streamUrl;

    public class LocalBinder extends Binder {
        public RadioService getService() {
            return RadioService.this;
        }
    }

    private final MediaSessionCompat.Callback mediasSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPause() {
            super.onPause();
            pause();
        }

        @Override
        public void onStop() {
            super.onStop();
            stop();
            notificationManager.cancelNotify();
        }

        @Override
        public void onPlay() {
            super.onPlay();
            resume();
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        nowPlaying = getResources().getString(R.string.notification_playing);
        songName = getResources().getString(R.string.app_name);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        notificationManager = new MediaNotificationManager(this);

        wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "mcScPAmpLock");

        mediaSession = new MediaSessionCompat(this, getClass().getSimpleName());
        transportControls = mediaSession.getController().getTransportControls();
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, songName)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, nowPlaying)
                .build());
        mediaSession.setCallback(mediasSessionCallback);
        registerReceiver(onCallIncome, new IntentFilter("android.intent.action.PHONE_STATE"));
        registerReceiver(onHeadPhoneDetect, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        bandwidthMeter = new DefaultBandwidthMeter.Builder(this).build();
        dataSourceFactory = buildDataSourceFactory(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action))
            return START_NOT_STICKY;

        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stop();
            return START_NOT_STICKY;
        }
        if (action.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (action.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (action.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }

        return START_NOT_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (status != null) {
            if (status.equals(PlaybackStatus.IDLE))
                stopSelf();
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(final Intent intent) {
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                exoPlayer.setVolume(0.8f);
                resume();
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                stop();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) pause();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (isPlaying())
                    exoPlayer.setVolume(0.1f);
                break;
        }
    }

    BroadcastReceiver onCallIncome = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            try {
                if (exoPlayer.getPlayWhenReady()) {
                    if (a.equals(TelephonyManager.EXTRA_STATE_OFFHOOK) || a.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                        exoPlayer.setPlayWhenReady(false);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();

            }
        }
    };

    BroadcastReceiver onHeadPhoneDetect = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (exoPlayer.getPlayWhenReady()) {
                    if (streamUrl != null) {
                        playOrPause(streamUrl);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                status = PlaybackStatus.LOADING;
                break;
            case Player.STATE_ENDED:
                status = PlaybackStatus.STOPPED;
                break;
            case Player.STATE_READY:
                if (exoPlayer.getPlayWhenReady()) {
                    status = PlaybackStatus.PLAYING;
                } else {
                    status = PlaybackStatus.PAUSED;
                }
                break;
            default:
                status = PlaybackStatus.IDLE;
                break;
        }

        if (!status.equals(PlaybackStatus.IDLE))
            notificationManager.startNotify(status);

        Tools.onEvent(status);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Tools.onEvent(PlaybackStatus.ERROR);
    }

    @Override
    public void onMetadata(@NonNull Metadata metadata) {
        new Handler().postDelayed(() -> getMetadata(metadata), 1000);
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    private String getUserAgent() {
        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version"));
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE;
        result.append(version.length() > 0 ? version : "1.0");

        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }

        String id = Build.ID;
        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }
        result.append(")");
        return result.toString();
    }

    public void newPlay(String streamUrl) {
        this.streamUrl = streamUrl;
        if (wifiLock != null && !wifiLock.isHeld()) {
            wifiLock.acquire();
        }
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.addListener(this);
        exoPlayer.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onAudioSessionIdChanged(EventTime eventTime, int audioSessionId) {
                Tools.onAudioSessionId(getAudioSessionId());
            }
        });

        HttpsTrustManager.allowAllSSL();

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));

        // Use appropriate MediaSource factory based on the stream type
        if (streamUrl.contains(".m3u8") || streamUrl.contains(".M3U8")) {
            newMediaSource = new androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(false) // Adjust as necessary
                    .setExtractorFactory(new DefaultHlsExtractorFactory(DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM, false))
                    .createMediaSource(mediaItem);
        } else {
            newMediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, new DefaultExtractorsFactory())
                    .createMediaSource(mediaItem);
        }
        exoPlayer.setMediaSource(newMediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }


    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return buildNewDataSourceFactory(useBandwidthMeter ? bandwidthMeter : null);
    }

    public DataSource.Factory buildNewDataSourceFactory(DefaultBandwidthMeter bandwidthMeter){
        return new DefaultDataSource.Factory(this, buildHttpDataSourceFactory(bandwidthMeter));
    }

    public HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter){
        return new DefaultHttpDataSource.Factory().setUserAgent(getUserAgent()).setTransferListener(bandwidthMeter);
    }

    public int getAudioSessionId() {
        return exoPlayer.getAudioSessionId();
    }

    public void resume() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
            status = PlaybackStatus.PLAYING;
            notificationManager.startNotify(status);
            Tools.onEvent(status);
        }
    }

    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            status = PlaybackStatus.PAUSED;
            notificationManager.startNotify(status);
            Tools.onEvent(status);
        }
    }

    public void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        exoPlayer = null;
        notificationManager.cancelNotify();
        audioManager.abandonAudioFocus(this);
        try {
            unregisterReceiver(onCallIncome);
            unregisterReceiver(onHeadPhoneDetect);
        } catch (Exception e) {
            e.printStackTrace();
        }
        wifiLockRelease();
    }

    public void playOrPause(String url) {
        if (url != null) {
            if (exoPlayer != null) {
                if (isPlaying()) {
                    pause();
                } else {
                    resume();
                }
            } else {
                newPlay(url);
            }

        }
    }

    public void stopAndPlay(String url) {
        if (url != null && !url.isEmpty()) {
            if (!isPlaying()) {
                newPlay(url);
            } else {
                stop();
                newPlay(url);
            }
        } else {
            if (isPlaying()) {
                stop();
                newPlay(url);
            } else {
                newPlay(url);
            }
        }
    }

    public String getStatus() {
        return status;
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public boolean isPlaying() {
        if (exoPlayer != null) {
            return this.status.equals(PlaybackStatus.PLAYING);
        } else {
            return false;
        }
    }

    private void wifiLockRelease() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
    }

    @Override
    public void onDestroy() {
        pause();
        try {
            notificationManager.cancelNotify();
            if (exoPlayer != null) {
                exoPlayer.release();
                exoPlayer.removeListener(this);
            }
            mediaSession.release();
            unregisterReceiver(onCallIncome);
            unregisterReceiver(onHeadPhoneDetect);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();

    }

    @UnstableApi @SuppressWarnings({"unchecked", "rawtypes"})
    private void getMetadata(Metadata metadata){
        if (!metadata.get(0).toString().equals("")) {
            String data = metadata.get(0).toString().replace("ICY: ", "");
            ArrayList<String> arrayList = new ArrayList(Arrays.asList(data.split(",")));
            String[] mediaMetadata = arrayList.get(0).split("=");

            String currentSong = mediaMetadata[1].replace("\"", "");

            if (currentSong.contains("null")) {
                currentSong = getString(R.string.unknown_song);
            } else if (currentSong.isEmpty()){
                currentSong = getString(R.string.unknown_song);
            }

            if (isPlaying()){
                nowPlaying = getString(R.string.now_playing);
                songName = currentSong;

                if (songName.isEmpty()){
                    songName = getString(R.string.unknown_song);
                }

                fetchArtworkFromItunes(songName);

                mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, songName)
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, nowPlaying)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artWorkUrl)
                        .build());
                notificationManager.changeRadio(songName, nowPlaying);
                notificationManager.startNotify(status);

                Handler handler = new Handler();
                handler.postDelayed(() -> {
                    MainActivity.changeSongName(songName);
                    MainActivity.changeAlbumArt(artWorkUrl);
                    new FetchBitmapTask().execute(artWorkUrl);
                }, 1000);
            }
        }
    }

    // Step 1: Method to build iTunes search URL
    private static String buildItunesSearchUrl(String songName) {
        try {
            String encodedSongName = URLEncoder.encode(songName, "UTF-8");
            return "https://itunes.apple.com/search?term=" + encodedSongName + "&entity=song&limit=1";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Step 2: Method to fetch artwork URL using OkHttp and update UI
    private void fetchArtworkFromItunes(String songName) {
        String url = buildItunesSearchUrl(songName);
        if (url == null) return;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                try {
                    JSONObject jsonResponse = new JSONObject(response.body().string());
                    JSONArray results = jsonResponse.getJSONArray("results");
                    if (results.length() > 0) {
                        JSONObject firstResult = results.getJSONObject(0);
                        artWorkUrl = firstResult.getString("artworkUrl100").replace("100x100", "500x500"); // Increase resolution
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private class FetchBitmapTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... urls) {
            String src = urls[0];
            Bitmap bitmap = null;
            try {
                URL url = new URL(src.replace(" ", "%20"));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                notificationManager.changeIcon(bitmap);
            }
        }
    }

}
