package com.ucool.livecricketwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateWidgetWorker extends Worker {


    private String generateApiUrl(Set<String> selectedDays) {
        String startDate = getFormattedDateForDay("Today");
        String endDate = startDate; // Default end date is today

        if (selectedDays.contains("Tomorrow")) {
            endDate = getFormattedDateForDay("Tomorrow");
        }
        if (selectedDays.contains("Day After Tomorrow")) {
            endDate = getFormattedDateForDay("Day After Tomorrow");
        }
        if (selectedDays.contains("Yesterday")) {
            startDate = getFormattedDateForDay("Yesterday");
        }

        // Get the timezone offset in "+hhmm" or "-hhmm" format
        String timezoneOffset = getCurrentTimezoneOffset();

        // Generate the complete URL with dynamic date range and timezone
        return "https://assets-wwos.sportz.io/sifeeds/multisport/?methodtype=3&client=37f6777763&sport=1&league=0"
                + "&timezone=" + timezoneOffset
                + "&language=en"
                + "&daterange=" + startDate + "-" + endDate;
    }

    private String getFormattedDateForDay(String day) {
        Calendar calendar = Calendar.getInstance();

        switch (day) {
            case "Yesterday":
                calendar.add(Calendar.DAY_OF_YEAR, -1);
                break;
            case "Today":
                // No change needed, as calendar is already set to today
                break;
            case "Tomorrow":
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                break;
            case "Day After Tomorrow":
                calendar.add(Calendar.DAY_OF_YEAR, 2);
                break;
        }

        Date date = calendar.getTime();
        return new SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(date);
    }

    private String getCurrentTimezoneOffset() {
        TimeZone timeZone = TimeZone.getDefault();
        int offsetMillis = timeZone.getRawOffset();
        int hours = Math.abs(offsetMillis / (1000 * 60 * 60));
        int minutes = Math.abs((offsetMillis / (1000 * 60)) % 60);
        String sign = offsetMillis >= 0 ? "+" : "-";
        return String.format(Locale.getDefault(), "%s%02d%02d", sign, hours, minutes);
    }
    private static final String TAG = "MY_TAG";
    //private static final String API_URL = "https://assets-wwos.sportz.io/sifeeds/multisport/?methodtype=3&client=37f6777763&sport=1&league=0&timezone=0530&language=en&daterange=11112024-12112024";
    //private static final String API_URL = "https://jsonplaceholder.typicode.com/todos/1";

    public UpdateWidgetWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() { // the main function of the Worker whihc runs for worker when the worker runs
        Log.d("MY_TAG", "UpdateWidgetWorker doWork() called");

        int ping = getGooglePing();
        Log.d(TAG, "ping to google : " + ping);

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("uCoolWidgetPrefs", Context.MODE_PRIVATE);
        Set<String> selectedDays = prefs.getStringSet("selected_days", new HashSet<>());
        String API_URL = generateApiUrl(selectedDays);
        Log.d("MY_TAG", "UpdateWidgetWorker Generated API URL: " + API_URL);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(getApplicationContext(), CricketWidgetProvider.class));

        boolean showResult = prefs.getBoolean("show_result", true);
        Log.d("MY_TAG", "UpdateWidgetWorker showResult : "+showResult);

        int maxMatchesPerFirstPage = prefs.getInt("total_pages", 4);
        Log.d("MY_TAG", "UpdateWidgetWorker maxMatchesPerFirstPage : "+maxMatchesPerFirstPage);

        Set<String> selectedTeams = prefs.getStringSet("selected_teams", new HashSet<>());
        Log.d("MY_TAG", "UpdateWidgetWorker Selected Teams: " + selectedTeams);

        String jsonResponse = fetchDataFromApi(API_URL);
        if (jsonResponse == null) {
            return Result.failure();
        }

        try {
            int maxMatchesPerPage = 1;
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray matchesArray = jsonObject.getJSONArray("matches");
            JSONArray filteredMatchesArray = new JSONArray();

            // Filter matches to only include selected teams
            for (int i = 0; i < matchesArray.length(); i++) {
                JSONObject match = matchesArray.getJSONObject(i);
                JSONArray participants = match.getJSONArray("participants");
                boolean isMatchSelected = false;

                // Check if any participant's short_name matches the selected teams
                for (int j = 0; j < participants.length(); j++) {
                    String teamShortName = participants.getJSONObject(j).optString("short_name_eng", "");
                    if (selectedTeams.contains(teamShortName)) {
                        isMatchSelected = true;
                        break;
                    }
                }

                if (isMatchSelected) {
                    filteredMatchesArray.put(match);
                }
            }
            Log.d(TAG, "UpdateWidgetWorker: Filtered Array Data: " + filteredMatchesArray);
            Log.d("MY_TAG", "Filtered Matches Count: " + filteredMatchesArray.length());

            // Now use `filteredMatchesArray` for displaying in the widget
            int totalMatches = filteredMatchesArray.length();
            int currentPage = getCurrentPage();

            for (int appWidgetId : appWidgetIds) {
                RemoteViews views;

                if (currentPage == 0) {
                    // Display summary on the first page
                    views = new RemoteViews(getApplicationContext().getPackageName(), R.layout.summary_page);
                    views.removeAllViews(R.id.widgetMatchContainerSummary);

                    views.setViewVisibility(R.id.widgetMatchContainerSummary, View.VISIBLE);
                    views.setViewVisibility(R.id.widgetNoMatchesMessage, View.GONE);

                    if (totalMatches > 0) {
                        // Display matches as before
                        for (int i = 0; i < maxMatchesPerFirstPage && i < filteredMatchesArray.length(); i++) {
                            JSONObject match = filteredMatchesArray.getJSONObject(i);
                            Log.d(TAG, "Displaying match " + i + " of " + filteredMatchesArray.length());

                            String team1 = match.getJSONArray("participants").getJSONObject(0).optString("short_name_eng", "Team1");
                            String team2 = match.getJSONArray("participants").getJSONObject(1).optString("short_name_eng", "Team2");
                            String matchTime;

                            int eventStatusId = match.optInt("event_status_id", -1);
                            String eventStatus = match.optString("event_status", "Status");
                            String eventSubStatus = match.optString("event_sub_status", "Match Time");
                            String startDateStr = match.optString("start_date", "");

                            // Determine matchTime based on event_status_id and showResult setting
                            if (eventStatusId == 114 && !showResult) { // Match ended and showResult is false
                                matchTime = eventStatus;
                            } else if (eventStatusId == 115) { // Match not started
                                matchTime = formatMatchStartTime(startDateStr);
                            } else {
                                matchTime = eventSubStatus;
                            }

                            // Create a simple RemoteView for each match summary
                            RemoteViews matchSummaryView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.match_summary_item);
                            matchSummaryView.setTextViewText(R.id.widgetMatchSummary, team1 + " vs " + team2 + " - " + matchTime);

                            views.addView(R.id.widgetMatchContainerSummary, matchSummaryView);
                        }
                    } else{ // No matches found, display the message
                        String noMatchesMessage = constructNoMatchesMessage(selectedDays);
                        views.setTextViewText(R.id.widgetNoMatchesMessage, noMatchesMessage);
                        views.setViewVisibility(R.id.widgetMatchContainerSummary, View.GONE);
                        views.setViewVisibility(R.id.widgetNoMatchesMessage, View.VISIBLE);
                    }


                } else {
                    // Display detailed matches on subsequent pages
                    views = new RemoteViews(getApplicationContext().getPackageName(), R.layout.detailed_page);
                    views.removeAllViews(R.id.widgetMatchContainerDetailed);

                    int startIndex = (currentPage - 1) * maxMatchesPerPage;
                    int endIndex = Math.min(startIndex + maxMatchesPerPage, totalMatches);

                    for (int i = startIndex; i < endIndex && i < totalMatches; i++) {
                        JSONObject match = filteredMatchesArray.getJSONObject(i);
                        String matchTitle = match.optString("event_name", "Match Title");
                        String team1 = match.getJSONArray("participants").getJSONObject(0).optString("short_name_eng", "Team1");
                        String team2 = match.getJSONArray("participants").getJSONObject(1).optString("short_name_eng", "Team2");
                        String matchTime = match.optString("short_event_status", "Match Time");
                        int eventStatusId = match.optInt("event_status_id", -1);
                        String startDateStr = match.optString("start_date", "");
                        if (eventStatusId == 115) { // if the match is not started then show its date with time
                            matchTime = formatMatchStartTime(startDateStr);
                        }
                        String venue = match.optString("venue_name", "Venue");
                        String status = match.optString("event_status", "Status");

                        // Create a RemoteView for each detailed match
                        RemoteViews matchDetailedView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.match_item);
                        matchDetailedView.setTextViewText(R.id.widgetMatchTitle, matchTitle);
                        //matchDetailedView.setTextViewText(R.id.widgetTeams, team1 + " vs " + team2);
                        matchDetailedView.setTextViewText(R.id.team1_name, team1);
                        matchDetailedView.setTextViewText(R.id.team2_name, team2);
                        int team1FlagResId = getFlagResourceForTeam(team1);
                        int team2FlagResId = getFlagResourceForTeam(team2);
                        matchDetailedView.setImageViewResource(R.id.team1Flag, team1FlagResId);
                        matchDetailedView.setImageViewResource(R.id.team2Flag, team2FlagResId);
                        matchDetailedView.setTextViewText(R.id.widgetMatchTime, matchTime);
                        matchDetailedView.setTextViewText(R.id.widgetVenue, venue);
                        matchDetailedView.setTextViewText(R.id.widgetStatus, status);

                        views.addView(R.id.widgetMatchContainerDetailed, matchDetailedView);
                    }
                }

                // Set up navigation buttons
                // Set up navigation buttons conditionally
                Log.d(TAG, "*********** " + currentPage + ", "+totalMatches);
                if (currentPage < totalMatches) { // Show Next button only if not on the last page
                    Intent nextIntent = new Intent(getApplicationContext(), CricketWidgetProvider.class);
                    nextIntent.setAction("com.ucool.livecricketwidget.ACTION_NEXT");
                    PendingIntent nextPendingIntent = PendingIntent.getBroadcast(
                            getApplicationContext(),
                            0,
                            nextIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    views.setOnClickPendingIntent(R.id.buttonNext, nextPendingIntent);
                    views.setViewVisibility(R.id.buttonNext, View.VISIBLE);
                    views.setViewVisibility(R.id.buttonPrevious, View.VISIBLE);
                } else {
                    // Hide the Next button on the last page
                    views.setViewVisibility(R.id.buttonNext, View.GONE);
                }

                if (currentPage > 0) {
                    Intent previousIntent = new Intent(getApplicationContext(), CricketWidgetProvider.class);
                    previousIntent.setAction("com.ucool.livecricketwidget.ACTION_PREVIOUS");
                    PendingIntent previousPendingIntent = PendingIntent.getBroadcast(
                            getApplicationContext(),
                            0,
                            previousIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    views.setOnClickPendingIntent(R.id.buttonPrevious, previousPendingIntent);
                }
                // Set up refresh button
                Intent refreshIntent = new Intent(getApplicationContext(), CricketWidgetProvider.class);
                refreshIntent.setAction("com.ucool.livecricketwidget.ACTION_REFRESH");
                PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                        getApplicationContext(),
                        0,
                        refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                views.setOnClickPendingIntent(R.id.buttonRefresh, refreshPendingIntent);


                appWidgetManager.updateAppWidget(appWidgetId, views);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return Result.failure();
        }

        return Result.success();

    }




    private long getLastUpdateTime() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        return prefs.getLong("last_update_time", 0);
    }

    private void setLastUpdateTime(long time) {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("widget_prefs", Context.MODE_PRIVATE);
        prefs.edit().putLong("last_update_time", time).apply();
    }


    private String fetchDataFromApi(String urlString) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "UpdateWidgetWorker: HTTP error code: " + response.code());
                return null;
            }
            String responseData = response.body().string();
            Log.d(TAG, "UpdateWidgetWorker: Fetched Data: " + responseData);
            return responseData;
        } catch (IOException e) {
            Log.e(TAG, "UpdateWidgetWorker: Error fetching data: " + e.getMessage());
            return null;
        }
    }






    public static int getGooglePing() {
        String googleDns = "8.8.8.8";  // Google's DNS server
        int port = 53;                 // DNS port
        int timeout = 1500;            // Timeout in milliseconds

        try (Socket socket = new Socket()) {
            long startTime = System.currentTimeMillis();
            socket.connect(new InetSocketAddress(googleDns, port), timeout);
            long endTime = System.currentTimeMillis();
            return (int) (endTime - startTime);  // Return ping time in milliseconds
        } catch (Exception e) {
            e.printStackTrace();
            return -1;  // Indicate failure
        }
    }

    private boolean isHostResolvable(String hostname) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            for (InetAddress address : addresses) {
                Log.d(TAG, "UpdateWidgetWorker: Resolved IP for " + hostname + ": " + address.getHostAddress());
            }
            return true;
        } catch (UnknownHostException e) {
            Log.e(TAG, "UpdateWidgetWorker: Unable to resolve host \"" + hostname + "\": " + e.getMessage());
            return false;
        }
    }

    private String constructNoMatchesMessage(Set<String> selectedDays) {
        Map<String, String> dayMap = new HashMap<>();
        dayMap.put("Yesterday", "yesterday");
        dayMap.put("Today", "today");
        dayMap.put("Tomorrow", "tomorrow");
        dayMap.put("Day After Tomorrow", "day after tomorrow");

        // Collect the selected days in the order they appear in the list
        List<String> orderedDays = new ArrayList<>();
        if (selectedDays.contains("Yesterday")) {
            orderedDays.add(dayMap.get("Yesterday"));
        }
        if (selectedDays.contains("Today")) {
            orderedDays.add(dayMap.get("Today"));
        }
        if (selectedDays.contains("Tomorrow")) {
            orderedDays.add(dayMap.get("Tomorrow"));
        }
        if (selectedDays.contains("Day After Tomorrow")) {
            orderedDays.add(dayMap.get("Day After Tomorrow"));
        }

        if (orderedDays.isEmpty()) {
            return "No matches found for the selected days.";
        }

        // Build the message with proper commas and 'and'
        StringBuilder messageBuilder = new StringBuilder("No matches found for ");

        for (int i = 0; i < orderedDays.size(); i++) {
            messageBuilder.append(orderedDays.get(i));
            if (i < orderedDays.size() - 2) {
                messageBuilder.append(", ");
            } else if (i == orderedDays.size() - 2) {
                messageBuilder.append(" and ");
            }
        }
        messageBuilder.append(".");

        return messageBuilder.toString();
    }

    private int getCurrentPage() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("uCoolWidgetPrefs", Context.MODE_PRIVATE);
        return prefs.getInt("current_page", 0); // Default to page 0 (the summary page)
    }

    private void setCurrentPage(int page) {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("uCoolWidgetPrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("current_page", page).apply();
    }

    private String formatMatchStartTime(String startDateStr) {
        try {
            // Parse the start_date, e.g., "2024-11-13T13:00+04:00"
            LocalDateTime startDate = LocalDateTime.parse(startDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            // Determine if the date is today, tomorrow, or another date
            LocalDate today = LocalDate.now();
            LocalDate matchDate = startDate.toLocalDate();
            String dateLabel;

            if (matchDate.equals(today)) {
                dateLabel = "today";
            } else if (matchDate.equals(today.plusDays(1))) {
                dateLabel = "tomorrow";
            } else {
                dateLabel = matchDate.toString(); // e.g., "2024-11-13"
            }

            // Format time in hh:mm
            String timeLabel = startDate.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            return "Match starts at " + dateLabel + " " + timeLabel;

        } catch (DateTimeParseException e) {
            e.printStackTrace();
            return "Match time unavailable";
        }
    }

    private int getFlagResourceForTeam(String teamCode) {
        // Ensure the team code is in lowercase to match drawable naming
        String drawableName = teamCode.toLowerCase();

        // Get the resource ID for the drawable
        int resId = getApplicationContext().getResources().getIdentifier(
                drawableName,
                "drawable",
                getApplicationContext().getPackageName()
        );

        // Return the resource ID if found, otherwise a default flag
        return resId != 0 ? resId : R.drawable.baseline_flag_24;
    }


}
