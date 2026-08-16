package com.transparencia.radiotransparencia.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.util.ArrayList;

public class Tools {

    private static ArrayList<EventListener> listeners;
    private Context context;


    public Tools(Context context) {
        this.context = context;
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;

        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
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

