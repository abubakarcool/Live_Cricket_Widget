package com.ucool.livecricketwidget;

import android.app.Application;
import androidx.work.Configuration;
import androidx.work.WorkManager;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize WorkManager manually
        WorkManager.initialize(
                this,
                new Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.DEBUG)
                        .build()
        );
    }
}
