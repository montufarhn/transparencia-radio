package com.la87hn.la87laprimera.activities;

import static com.la87hn.la87laprimera.Config.ENABLE_AUTOPLAY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;

import com.la87hn.la87laprimera.Config;
import com.la87hn.la87laprimera.R;
import com.la87hn.la87laprimera.services.PlaybackStatus;
import com.la87hn.la87laprimera.services.RadioManager;
import com.la87hn.la87laprimera.services.RadioService;
import com.la87hn.la87laprimera.utils.Constant;
import com.la87hn.la87laprimera.utils.Tools;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.next.androidintentlibrary.BrowserIntents;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Objects;

import eu.gsottbauer.equalizerview.EqualizerView;

@UnstableApi
public class MainActivity extends AppCompatActivity implements Tools.EventListener, View.OnClickListener {

    private ProgressBar loadingProgressBar;
    @SuppressLint("StaticFieldLeak")
    static ImageView bg_img;
    private MaterialButton playButton;
    @SuppressLint("StaticFieldLeak")
    private static TextView metadataTextView;
    private EqualizerView equalizerView;
    RadioManager radioManager;
    String jsonUrl;
    @SuppressLint("StaticFieldLeak")
    static Context context;

    // Variables locales para las URLs de redes sociales
    private String radioURL;
    private String numeroWhatsapp;
    private String urlFacebook;
    private String urlInstagram;
    private String urlTikTok;
    private String urlTwitter;
    private String urlYouTube;
    private String urlWebsite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        jsonUrl = Config.JSON_URL;

        initViews();
        fetchSocialItems();  // Cambiar a fetchSocialItems para obtener datos desde el JSON

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        radioManager = RadioManager.with();
        checkAutoplay();

        if (isPlaying()) {
            onAudioSessionId(RadioManager.getService().getAudioSessionId());
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitDialog();
            }
        });

        ImageView imageView = findViewById(R.id.player_anim);
        Glide.with(this).load(R.drawable.player_anim).into(imageView);

        findViewById(R.id.rotate_anim).startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate));
        ImageView btnPlayer = findViewById(R.id.bars_anim);
        Glide.with(this).load(R.drawable.bars_anim).into(btnPlayer);
    }

    private void stopDestroyService() {
        if (radioManager != null) {
            radioManager.unbind(this);
        }
    }

    public void minimizeApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onEvent(String status) {
        if (status != null) {
            switch (status) {
                case PlaybackStatus.LOADING:
                    setBuffering(true);
                    break;
                case PlaybackStatus.ERROR:
                    Toast.makeText(this, R.string.error_retry, Toast.LENGTH_SHORT).show();
                    break;
            }

            if (!status.equals(PlaybackStatus.LOADING)) {
                setBuffering(false);
                updateButtons(this);
            }
        }
    }

    @Override
    public void onAudioSessionId(Integer i) {
    }

    private void initViews() {
        equalizerView = findViewById(R.id.equalizer_view);
        equalizerView.setAnimationDuration(3000);

        loadingProgressBar = findViewById(R.id.progress_bar);
        bg_img = findViewById(R.id.background_image);

        playButton = findViewById(R.id.fab_play);
        playButton.setOnClickListener(this);

        MaterialButton shareButton = findViewById(R.id.btn_share);
        shareButton.setOnClickListener(this);

        MaterialButton offButton = findViewById(R.id.btn_off);
        offButton.setOnClickListener(this);

        TextView nowPlayingTextView = findViewById(R.id.now_playing_text);
        nowPlayingTextView.setText(Config.NOW_PLAYING_TEXT);

        metadataTextView = findViewById(R.id.metadata_text);
        metadataTextView.setSelected(true);

        if (!Tools.isNetworkAvailable(this)) {
            nowPlayingTextView.setText(getString(R.string.app_name));
            metadataTextView.setText(getString(R.string.internet_not_connected));
        }
    }

    // Método para obtener los ítems sociales desde el JSON
    private void fetchSocialItems() {
        new Thread(() -> {
            try {
                URL url = new URL(jsonUrl);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    reader.close();
                    parseSocialItems(new JSONObject(stringBuilder.toString()));
                } finally {
                    urlConnection.disconnect();
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error fetching social items", e);
            }
        }).start();
    }

    // Método para analizar el JSON y asignar los datos a las variables locales
    private void parseSocialItems(JSONObject json) {
        try {
            JSONArray jsonArray = json.getJSONArray("godo.pe");
            if (jsonArray.length() > 0) {
                JSONObject radioInfo = jsonArray.getJSONObject(0);
                radioURL = radioInfo.optString("radio_url");
                String tvURL = radioInfo.optString("tv_url");
                numeroWhatsapp = radioInfo.optString("numero_whatsapp");
                urlFacebook = radioInfo.optString("url_facebook");
                urlInstagram = radioInfo.optString("url_instagram");
                urlTikTok = radioInfo.optString("url_tiktok");
                urlTwitter = radioInfo.optString("url_twitter");
                urlYouTube = radioInfo.optString("url_youtube");
                urlWebsite = radioInfo.optString("url_website");

                runOnUiThread(this::initSocialItems);
            }
        } catch (JSONException e) {
            Log.e("MainActivity", "Error parsing JSON", e);
        }
    }

    // Método para inicializar los ítems sociales
    public void initSocialItems() {
        LinearLayout socialLyt = findViewById(R.id.social_lyt);
        socialLyt.removeAllViews();

        if (Config.ENABLE_SOCIAL_ICONS) {
            socialLyt.setVisibility(View.VISIBLE);
        } else {
            socialLyt.setVisibility(View.GONE);
        }

        if (urlFacebook != null && !urlFacebook.isEmpty()) {
            addSocialItem(socialLyt, R.drawable.facebook, urlFacebook);
        }
        if (urlInstagram != null && !urlInstagram.isEmpty()) {
            addSocialItem(socialLyt, R.drawable.instagram, urlInstagram);
        }
        if (urlTikTok != null && !urlTikTok.isEmpty()) {
            addSocialItem(socialLyt, R.drawable.tiktok, urlTikTok);
        }
        if (urlTwitter != null && !urlTwitter.isEmpty()) {
            addSocialItem(socialLyt, R.drawable.twitter, urlTwitter);
        }
        if (urlYouTube != null && !urlYouTube.isEmpty()) {
            addSocialItem(socialLyt, R.drawable.youtube, urlYouTube);
        }
        if (urlWebsite != null && !urlWebsite.isEmpty()) {
            addSocialItem(socialLyt, R.drawable.website, urlWebsite);
        }
        if (numeroWhatsapp != null && !numeroWhatsapp.isEmpty()) {
            addWhatsAppItem(socialLyt, R.drawable.whatsapp, numeroWhatsapp);
        }
    }

    // Método para agregar un ítem social
    private void addSocialItem(LinearLayout socialLyt, int drawableResId, String url) {
        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
        imageView.setPadding(10, 5, 10, 5);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setImageResource(drawableResId);

        imageView.setOnClickListener(v -> loadWebsite(url));

        socialLyt.addView(imageView);
    }
    private void addWhatsAppItem(LinearLayout layout, int iconResId, String phoneNumber) {
        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
        imageView.setPadding(10, 5, 10, 5);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setImageResource(iconResId);
        imageView.setOnClickListener(view -> sendWhatsAppMessage(phoneNumber));
        layout.addView(imageView);
    }
    private void sendWhatsAppMessage(String phoneNumber) {
        String message = getString(R.string.default_whatsapp_message);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=" + URLEncoder.encode(message, "UTF-8")));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp no \u200B\u200Bestá instalado en tu dispositivo", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadWebsite(String link) {
        BrowserIntents.from(MainActivity.this).openLink(Uri.parse(link)).show();
    }

    public static void changeSongName(String songName) {
        Constant.songName = songName;
        metadataTextView.setText(songName);
    }

    public static void changeAlbumArt(String artworkUrl) {
        if (artworkUrl != null && !artworkUrl.isEmpty()) {

            updateBackgroundImage(artworkUrl);
        }
    }

    private static void updateBackgroundImage(String image) {
        Glide.with(context)
                .load(image)
                .placeholder(R.drawable.radio_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(bg_img);
    }

    public void setBuffering(boolean flag) {
        loadingProgressBar.setVisibility(flag ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private void checkAutoplay() {
        if (ENABLE_AUTOPLAY) {
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                if (RadioManager.getService() == null) {
                    Toast.makeText(MainActivity.this, getString(R.string.please_wait), Toast.LENGTH_SHORT).show();
                    checkAutoplay();
                } else {
                    playButton.callOnClick();
                    ENABLE_AUTOPLAY = false;
                }
            }, 1000);
        }
    }

    private void updateButtons(Activity activity) {
        if (isPlaying()) {
            if (RadioManager.getService() != null && radioURL != null && !radioURL.equals(RadioManager.getService().getStreamUrl())) {
                playButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_button_play));
            } else {
                setBuffering(true);
                playButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_button_pause));
            }
        } else {
            playButton.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_button_play));
        }

        if (isPlaying()) {
            equalizerView.animateBars();
            setBuffering(false);
        } else {
            equalizerView.stopBars();
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_play:
                if (!isPlaying()) {
                    if (radioURL != null) {
                        startStopPlaying();
                        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
                        int volume_level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        if (volume_level < 2) {
                            Toast.makeText(MainActivity.this, getString(R.string.volume_low), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, R.string.error_retry_later, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    startStopPlaying();
                }
                break;
            case R.id.btn_share:
                shareApp();
                break;
            case R.id.btn_off:
                closeApp();
                break;
        }
    }

    private void startStopPlaying() {
        radioManager.playOrPause(radioURL);
        updateButtons(this);
    }

    private boolean isPlaying() {
        return (null != radioManager && null != RadioManager.getService() && RadioManager.getService().isPlaying());
    }

    @Override
    public void onStart() {
        super.onStart();
        Tools.registerAsListener(this);
    }

    @Override
    public void onStop() {
        Tools.unregisterAsListener(this);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (!radioManager.isPlaying()) {
            radioManager.unbind(this);
        }
        stopDestroyService();
        RadioService.songName = "";
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateButtons(this);
        radioManager.bind(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void showExitDialog() {
        final Dialog exit_dialog = new Dialog(this);
        exit_dialog.setContentView(R.layout.custom_exit);

        ImageView closeBtn = exit_dialog.findViewById(R.id.close_btn);
        Button minimize_btn = exit_dialog.findViewById(R.id.minimize_btn);
        Button quit_btn = exit_dialog.findViewById(R.id.quit_btn);

        exit_dialog.show();
        exit_dialog.setCanceledOnTouchOutside(false);
        exit_dialog.setCancelable(false);
        Objects.requireNonNull(exit_dialog.getWindow()).getAttributes().width = FrameLayout.LayoutParams.MATCH_PARENT;
        exit_dialog.getWindow().getAttributes().height = FrameLayout.LayoutParams.MATCH_PARENT;
        exit_dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        closeBtn.setOnClickListener(v -> exit_dialog.dismiss());
        minimize_btn.setOnClickListener(v -> {
            exit_dialog.dismiss();
            minimizeApp();
        });
        quit_btn.setOnClickListener(v -> {
            exit_dialog.dismiss();
            finish();
        });
    }


    private void shareApp() {
        String appUrl = "https://play.google.com/store/apps/details?id=" + getPackageName(); // Reemplaza con la URL de tu app en Google Play
        String shareBody = getString(R.string.share_message) + "\n\n" + appUrl;

        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);

        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
    }

    private void closeApp() {
        finishAffinity();  // Cierra todas las actividades en la pila de tareas
        System.exit(0);    // Finaliza el proceso actual
    }
}