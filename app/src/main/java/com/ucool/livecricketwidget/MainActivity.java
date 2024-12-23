package com.ucool.livecricketwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends ComponentActivity {

    private static final String PREFS_NAME = "uCoolWidgetPrefs";
    private static final String KEY_TOTAL_PAGES = "total_pages";
    private static final String KEY_SELECTED_TEAMS = "selected_teams";
    private static final String KEY_SHOW_RESULT = "show_result";
    private static final String KEY_SELECTED_DAYS = "selected_days";
    private static final String KEY_UPDATE_INTERVAL = "update_interval";

    private static final String KEY_CURRENT_PAGE = "current_page";

    private Spinner spinnerTotalPages, spinnerShowResult, spinnerUpdateInterval;
    private GridLayout checkboxContainerTeams, checkboxContainerDays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        // Initialize UI components
        spinnerTotalPages = findViewById(R.id.spinner_total_pages);
        spinnerShowResult = findViewById(R.id.spinner_show_result);
        spinnerUpdateInterval = findViewById(R.id.spinner_update_interval);
        checkboxContainerTeams = findViewById(R.id.checkbox_container_teams);
        checkboxContainerDays = findViewById(R.id.checkbox_container_days);

        // Load or initialize default settings
        loadDefaultSettings();
        initializeUIWithDefaults();
        // Set up Save button to save settings
        findViewById(R.id.button_save_settings).setOnClickListener(v -> saveSettings());
        // Set up Close button to finish the activity
        findViewById(R.id.button_close_settings).setOnClickListener(v -> finish());
    }

    private void loadDefaultSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Set default settings if they are not already set
        if (!prefs.contains(KEY_TOTAL_PAGES)) {
            editor.putInt(KEY_TOTAL_PAGES, 4); // Default: 4 matches on summary page
        }
        if (!prefs.contains(KEY_SELECTED_TEAMS)) {
            Set<String> defaultTeams = new HashSet<>();
            defaultTeams.add("AUS");
            defaultTeams.add("AFG");
            defaultTeams.add("IND");
            defaultTeams.add("PAK");
            defaultTeams.add("BAN");
            editor.putStringSet(KEY_SELECTED_TEAMS, defaultTeams); // Default teams
        }
        if (!prefs.contains(KEY_SHOW_RESULT)) {
            editor.putBoolean(KEY_SHOW_RESULT, true); // Default: show result on summary page
        }
        if (!prefs.contains(KEY_SELECTED_DAYS)) {
            Set<String> defaultDays = new HashSet<>();
            defaultDays.add("Today");
            defaultDays.add("Tomorrow");
            editor.putStringSet(KEY_SELECTED_DAYS, defaultDays); // Default days
        }
        if (!prefs.contains(KEY_UPDATE_INTERVAL)) {
            editor.putInt(KEY_UPDATE_INTERVAL, 5); // Default: 5 minutes interval
        }
        editor.apply();
    }

    private void initializeUIWithDefaults() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Set total pages spinner
        spinnerTotalPages.setSelection(prefs.getInt(KEY_TOTAL_PAGES, 4) - 1);

        // Set team checkboxes based on saved selections
        Set<String> selectedTeams = prefs.getStringSet(KEY_SELECTED_TEAMS, new HashSet<>());
        String[] allTeams = getResources().getStringArray(R.array.team_codes);
        for (String team : allTeams) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(team);
            checkBox.setChecked(selectedTeams.contains(team));
            checkboxContainerTeams.addView(checkBox);
        }

        // Set show result spinner
        String showResultOption = prefs.getBoolean(KEY_SHOW_RESULT, true) ? "Yes" : "No";
        ArrayAdapter<CharSequence> showResultAdapter = ArrayAdapter.createFromResource(this, R.array.yes_no_options, android.R.layout.simple_spinner_item);
        showResultAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerShowResult.setAdapter(showResultAdapter);
        spinnerShowResult.setSelection(showResultOption.equals("Yes") ? 0 : 1);

        // Set day checkboxes based on saved selections
        Set<String> selectedDays = prefs.getStringSet(KEY_SELECTED_DAYS, new HashSet<>());
        String[] allDays = getResources().getStringArray(R.array.day_options);
        for (String day : allDays) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(day);
            checkBox.setChecked(selectedDays.contains(day));
            checkboxContainerDays.addView(checkBox);
        }

        // Set update interval spinner
        int updateIntervalIndex = getIntervalIndex(prefs.getInt(KEY_UPDATE_INTERVAL, 5));
        spinnerUpdateInterval.setSelection(updateIntervalIndex);
    }

    private int getIntervalIndex(int minutes) {
        String[] intervals = getResources().getStringArray(R.array.update_interval_options);
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i].equals(minutes + " minutes")) {
                return i;
            }
        }
        return 4; // Default to 5 minutes if not found
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        int totalPages = spinnerTotalPages.getSelectedItemPosition() + 1;
        editor.putInt(KEY_TOTAL_PAGES, totalPages);

        Set<String> selectedTeams = new HashSet<>();
        for (int i = 0; i < checkboxContainerTeams.getChildCount(); i++) {
            CheckBox checkBox = (CheckBox) checkboxContainerTeams.getChildAt(i);
            if (checkBox.isChecked()) selectedTeams.add(checkBox.getText().toString());
        }
        editor.putStringSet(KEY_SELECTED_TEAMS, selectedTeams);

        boolean showResult = spinnerShowResult.getSelectedItem().toString().equals("Yes");
        editor.putBoolean(KEY_SHOW_RESULT, showResult);

        Set<String> selectedDays = new HashSet<>();
        for (int i = 0; i < checkboxContainerDays.getChildCount(); i++) {
            CheckBox checkBox = (CheckBox) checkboxContainerDays.getChildAt(i);
            if (checkBox.isChecked()) selectedDays.add(checkBox.getText().toString());
        }
        editor.putStringSet(KEY_SELECTED_DAYS, selectedDays);

        String selectedIntervalText = spinnerUpdateInterval.getSelectedItem().toString().split(" ")[0];
        int updateInterval = Integer.parseInt(selectedIntervalText);
        editor.putInt(KEY_UPDATE_INTERVAL, updateInterval);

        editor.apply();
        refreshWidget();
        // Show custom Toast
        showCustomToast("Settings saved!");
        //Log.d("MY_TAG", "MainActivity showResult : "+showResult);
    }

    private void showCustomToast(String message) {
        // Inflate custom layout
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast_custom, null);

        // Set message text
        TextView text = layout.findViewById(R.id.toast_message);
        text.setText(message);

        // Create and show the Toast
        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }

    // Method to send a broadcast to refresh the widget
    private void refreshWidget() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_CURRENT_PAGE, 0).apply();

        Intent intent = new Intent(this, CricketWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        // Get all widget IDs and include them in the intent
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName widgetComponent = new ComponentName(this, CricketWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);

        // Send broadcast to refresh the widget
        sendBroadcast(intent);
    }

}
