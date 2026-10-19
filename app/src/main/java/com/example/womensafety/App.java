package com.example.womensafety;

import android.app.Application;

import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Notifications;

/** App-wide setup that must happen before any screen or service runs. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.createChannels(this);
        Backend.init(this);
    }
}
