package com.ucool.livecricketwidget;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install the SplashScreen
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        // Navigate to the main activity
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
