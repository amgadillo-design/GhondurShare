package com.amgadillo.matchrates;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MatchRatesWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.amgadillo.matchrates.REFRESH";
    private static final int[] MATCH_IDS = {R.id.match1, R.id.match2, R.id.match3, R.id.match4};
    private static final int[] RATE_IDS = {R.id.rate1, R.id.rate2, R.id.rate3, R.id.rate4};

    public static void requestRefresh(Context context) {
        Intent i = new Intent(context, MatchRatesWidget.class);
        i.setAction(ACTION_REFRESH);
        context.sendBroadcast(i);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action) || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            final PendingResult pending = goAsync();
            final Context appContext = context.getApplicationContext();
            new Thread(() -> {
                try {
                    updateAll(appContext);
                } finally {
                    pending.finish();
                }
            }, "match-rates-refresh").start();
            return;
        }
        super.onReceive(context, intent);
    }

    private static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, MatchRatesWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) return;

        for (int id : ids) manager.updateAppWidget(id, loadingViews(context));

        SharedPreferences prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        List<String> matches;
        List<String> rates;
        String error = null;

        try {
            matches = loadMatches(prefs.getString("teams", ""));
        } catch (Exception e) {
            matches = new ArrayList<>();
            error = "تعذر تحديث المباريات";
        }

        try {
            rates = loadRates(prefs.getString("pairs", "USD/SAR\nUSD/EGP\nSAR/EGP\nEUR/SAR"));
        } catch (Exception e) {
            rates = new ArrayList<>();
            error = error == null ? "تعذر تحديث العملات" : error + " والعملات";
        }

        for (int id : ids) {
            RemoteViews rv = baseViews(context);
            fillLines(rv, MATCH_IDS, matches, "لا توجد مباريات للفرق المحددة اليوم");
            fillLines(rv, RATE_IDS, rates, "لا توجد أزواج عملات صالحة");
            rv.setTextViewText(R.id.footer, error == null ? "آخر تحديث: " + nowTime() : error + " • اضغط ↻");
            manager.updateAppWidget(id, rv);
        }
    }

    private static RemoteViews baseViews(Context context) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_match_rates);
        Intent refreshIntent = new Intent(context, MatchRatesWidget.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(context, 11, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.refresh, pi);

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(context, 12, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.title, openPi);
        return rv;
    }

    private static RemoteViews loadingViews(Context context) {
        RemoteViews rv = baseViews(context);
        for (int id : MATCH_IDS) rv.setViewVisibility(id, View.GONE);
        for (int id : RATE_IDS) rv.setViewVisibility(id, View.GONE);
        rv.setViewVisibility(R.id.match1, View.VISIBLE);
        rv.setTextViewText(R.id.match1, "جارٍ تحديث المباريات...");
        rv.setViewVisibility(R.id.rate1, View.VISIBLE);
        rv.setTextViewText(R.id.rate1, "جارٍ تحديث العملات...");
        rv.setTextViewText(R.id.footer, "");
        return rv;
    }

    private static void fillLines(RemoteViews rv, int[] ids, List<String> lines, String emptyMessage) {
        for (int id : ids) rv.setViewVisibility(id, View.GONE);
        if (lines.isEmpty()) {
            rv.setViewVisibility(ids[0], View.VISIBLE);
            rv.setTextViewText(ids[0], emptyMessage);
            return;
        }
        int count = Math.min(ids.length, lines.size());
        for (int i = 0; i < count; i++) {
            rv.setViewVisibility(ids[i], View.VISIBLE);
            rv.setTextViewText(ids[i], lines.get(i));
        }
    }

    private static List<String> loadMatches(String teamsRaw) throws Exception {
        List<String> out = new ArrayList<>();
        List<String> filters = new ArrayList<>();
        for (String s : teamsRaw.split("[,;\\n]+")) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) filters.add(t);
        }
        if (filters.isEmpty()) {
            out.add("افتح التطبيق وحدد الفرق التي تتابعها");
            return out;
        }

        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        day.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
        String date = day.format(new Date());
        String body = get("https://api.sofascore.com/api/v1/sport/football/scheduled-events/" + date);
        JSONArray events = new JSONObject(body).optJSONArray("events");
        if (events == null) return out;

        for (int i = 0; i < events.length() && out.size() < 4; i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            JSONObject home = e.optJSONObject("homeTeam");
            JSONObject away = e.optJSONObject("awayTeam");
            String homeName = home == null ? "" : home.optString("name", "");
            String awayName = away == null ? "" : away.optString("name", "");
            String haystack = (homeName + " " + awayName + " " +
                    (home == null ? "" : home.optString("slug", "")) + " " +
                    (away == null ? "" : away.optString("slug", ""))).toLowerCase(Locale.ROOT);

            boolean wanted = false;
            for (String filter : filters) {
                if (haystack.contains(filter)) {
                    wanted = true;
                    break;
                }
            }
            if (!wanted) continue;

            JSONObject status = e.optJSONObject("status");
            String type = status == null ? "" : status.optString("type", "");
            long startTs = e.optLong("startTimestamp", 0L) * 1000L;
            String suffix;

            JSONObject hs = e.optJSONObject("homeScore");
            JSONObject as = e.optJSONObject("awayScore");
            boolean hasScore = hs != null && as != null && hs.has("current") && as.has("current");
            if (hasScore && !"notstarted".equalsIgnoreCase(type)) {
                suffix = hs.optInt("current", 0) + " - " + as.optInt("current", 0);
                if ("finished".equalsIgnoreCase(type)) suffix += " • انتهت";
                else suffix += " • مباشر";
            } else {
                suffix = formatTime(startTs);
            }
            out.add(homeName + "  " + suffix + "  " + awayName);
        }
        return out;
    }

    private static List<String> loadRates(String pairsRaw) throws Exception {
        List<String> out = new ArrayList<>();
        String[] pairs = pairsRaw.split("[,;\\n]+");
        for (String p : pairs) {
            if (out.size() >= 4) break;
            String cleaned = p.trim().toUpperCase(Locale.ROOT).replace("-", "/").replace(" ", "");
            String[] parts = cleaned.split("/");
            if (parts.length != 2 || parts[0].length() != 3 || parts[1].length() != 3) continue;
            try {
                String body = get("https://api.frankfurter.dev/v2/rate/" + parts[0].toLowerCase(Locale.ROOT) + "/" + parts[1].toLowerCase(Locale.ROOT));
                JSONObject obj = new JSONObject(body);
                double rate = obj.optDouble("rate", Double.NaN);
                if (!Double.isNaN(rate)) {
                    out.add(parts[0] + "/" + parts[1] + "   " + formatRate(rate));
                }
            } catch (Exception ignored) {
                out.add(parts[0] + "/" + parts[1] + "   --");
            }
        }
        return out;
    }

    private static String get(String urlString) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "MatchRatesWidget/0.1 Android");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Accept-Language", "ar,en;q=0.8");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String formatRate(double rate) {
        if (rate >= 100) return String.format(Locale.US, "%.2f", rate);
        if (rate >= 10) return String.format(Locale.US, "%.3f", rate);
        if (rate >= 1) return String.format(Locale.US, "%.4f", rate);
        return String.format(Locale.US, "%.5f", rate);
    }

    private static String formatTime(long millis) {
        if (millis <= 0) return "--:--";
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
        return f.format(new Date(millis));
    }

    private static String nowTime() {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
        return f.format(new Date());
    }
}
