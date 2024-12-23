package com.ucool.livecricketwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.ucool.livecricketwidget.R;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CricketWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d("MY_TAG", "CricketWidgetProvider onUpdate() called");


        // Set initial RemoteViews with "Loading..." message and set up refresh button
        for(int appWidgetId : appWidgetIds){
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            // Display "Loading..." message

            // Set up refresh button PendingIntent
            Intent refreshIntent = new Intent(context, CricketWidgetProvider.class);
            refreshIntent.setAction("com.ucool.livecricketwidget.ACTION_REFRESH_MAIN");
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.buttonRefresh, refreshPendingIntent);

            // Update the widget with initial RemoteViews
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d("MY_TAG", "Initial RemoteViews set for widget ID: " + appWidgetId);
        }



        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(UpdateWidgetWorker.class)
                .build();
        //WorkManager.getInstance(context).enqueue(workRequest);
        WorkManager.getInstance(context).enqueueUniqueWork(
                "UpdateCricketWidget",
                ExistingWorkPolicy.REPLACE,  // Replace any existing work with the same name
                workRequest
        );


        SharedPreferences prefs = context.getSharedPreferences("uCoolWidgetPrefs", Context.MODE_PRIVATE);
        int updateInterval = prefs.getInt("update_interval", 2);
        Log.d("MY_TAG", "CricketWidgetProvider update_interval : "+updateInterval);

        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(UpdateWidgetWorker.class, updateInterval, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "UpdateWidgetWork",
                ExistingPeriodicWorkPolicy.KEEP,// Keep the existing periodic work if it's already enqueued
                periodicWorkRequest
        );
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.d("MY_TAG", "CricketWidgetProvider onEnabled() called");
        SharedPreferences prefs = context.getSharedPreferences("uCoolWidgetPrefs", Context.MODE_PRIVATE);
        if (!prefs.contains("selected_teams")) {
            SharedPreferences.Editor editor = prefs.edit();
            Set<String> defaultTeams = new HashSet<>();
            defaultTeams.add("AUS");
            defaultTeams.add("IND");
            editor.putStringSet("selected_teams", defaultTeams);
            editor.putInt("update_interval", 5); // Default 5 minutes
            editor.apply();
        }
        Log.d("MY_TAG", "onEnabled: Default preferences initialized");
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.d("MY_TAG", "CricketWidgetProvider onDisabled() called");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("com.ucool.livecricketwidget.ACTION_NEXT".equals(intent.getAction())) {
            int currentPage = getCurrentPage(context);
            setCurrentPage(context, currentPage + 1);
        } else if ("com.ucool.livecricketwidget.ACTION_PREVIOUS".equals(intent.getAction())) {
            int currentPage = getCurrentPage(context);
            setCurrentPage(context, Math.max(currentPage - 1, 0));
        }
        // Handle Refresh action
        if("com.ucool.livecricketwidget.ACTION_REFRESH".equals(intent.getAction())){
            Log.d("MY_TAG", "Refresh button clicked action received");
            // Trigger an immediate API refresh by enqueuing a OneTimeWorkRequest
            WorkManager workManager = WorkManager.getInstance(context);
            OneTimeWorkRequest refreshRequest = new OneTimeWorkRequest.Builder(UpdateWidgetWorker.class).build();
            workManager.enqueue(refreshRequest);
            setCurrentPage(context, 0);
        }


        // Trigger widget update
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, CricketWidgetProvider.class));
        onUpdate(context, appWidgetManager, appWidgetIds);
    }

    private int getCurrentPage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("uCoolWidgetPrefs", Context.MODE_PRIVATE);
        return prefs.getInt("current_page", 0);
    }

    private void setCurrentPage(Context context, int page) {
        SharedPreferences prefs = context.getSharedPreferences("uCoolWidgetPrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("current_page", page).apply();
    }
}
