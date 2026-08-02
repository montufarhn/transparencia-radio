package com.la87hn.la87laprimera.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.util.ArrayList;

public class Tools {

    private static ArrayList<EventListener> listeners;
    private Context context;


    public Tools(Context context) {
        this.context = context;
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static void registerAsListener(EventListener listener) {
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(listener);
    }

    public static void unregisterAsListener(EventListener listener) {
        listeners.remove(listener);
    }

    public static void onEvent(String status) {
        if (listeners == null) return;
        for (EventListener listener : listeners) {
            listener.onEvent(status);
        }
    }

    public static void onAudioSessionId(Integer id) {
        if (listeners == null) return;
        for (EventListener listener : listeners) {
            listener.onAudioSessionId(id);
        }
    }

    public interface EventListener {
        void onEvent(String status);
        void onAudioSessionId(Integer i);
    }

}

