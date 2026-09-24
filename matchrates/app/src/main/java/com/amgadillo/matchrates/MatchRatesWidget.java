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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MatchRatesWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.amgadillo.matchrates.REFRESH";

    private static final int[] MATCH_IDS = {
            R.id.match1, R.id.match2, R.id.match3, R.id.match4,
            R.id.match5, R.id.match6, R.id.match7, R.id.match8
    };
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
            matches = loadMatches();
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
            fillMatchLines(rv, matches);
            fillLines(rv, RATE_IDS, rates, "لا توجد أزواج عملات صالحة");
            String footer = error == null
                    ? "مباريات اليوم: " + matches.size() + " • آخر تحديث: " + nowTime()
                    : error + " • اضغط ↻";
            rv.setTextViewText(R.id.footer, footer);
            manager.updateAppWidget(id, rv);
        }
    }

    private static RemoteViews baseViews(Context context) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_match_rates);

        Intent refreshIntent = new Intent(context, MatchRatesWidget.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, 11, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.refresh, refreshPi);

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
        rv.setTextViewText(R.id.match1, "جارٍ تحديث كل مباريات اليوم...");
        rv.setViewVisibility(R.id.rate1, View.VISIBLE);
        rv.setTextViewText(R.id.rate1, "جارٍ تحديث العملات...");
        rv.setTextViewText(R.id.footer, "");
        return rv;
    }

    private static void fillMatchLines(RemoteViews rv, List<String> matches) {
        for (int id : MATCH_IDS) rv.setViewVisibility(id, View.GONE);

        if (matches.isEmpty()) {
            rv.setViewVisibility(MATCH_IDS[0], View.VISIBLE);
            rv.setTextViewText(MATCH_IDS[0], "لا توجد مباريات اليوم");
            return;
        }

        int visible = Math.min(MATCH_IDS.length, matches.size());
        int regularLines = visible;

        if (matches.size() > MATCH_IDS.length) {
            regularLines = MATCH_IDS.length - 1;
        }

        for (int i = 0; i < regularLines; i++) {
            rv.setViewVisibility(MATCH_IDS[i], View.VISIBLE);
            rv.setTextViewText(MATCH_IDS[i], matches.get(i));
        }

        if (matches.size() > MATCH_IDS.length) {
            int remaining = matches.size() - regularLines;
            int lastId = MATCH_IDS[MATCH_IDS.length - 1];
            rv.setViewVisibility(lastId, View.VISIBLE);
            rv.setTextViewText(lastId, "… +" + remaining + " مباراة أخرى");
        }
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

    private static List<String> loadMatches() throws Exception {
        List<MatchRow> rows = new ArrayList<>();

        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.US);
        day.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
        String date = day.format(new Date());

        String body = get("https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates=" + date);
        JSONArray events = new JSONObject(body).optJSONArray("events");
        if (events == null) return new ArrayList<>();

        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;

            JSONArray competitions = e.optJSONArray("competitions");
            if (competitions == null || competitions.length() == 0) continue;
            JSONObject comp = competitions.optJSONObject(0);
            if (comp == null) continue;

            JSONArray competitors = comp.optJSONArray("competitors");
            if (competitors == null || competitors.length() < 2) continue;

            JSONObject home = null;
            JSONObject away = null;
            for (int j = 0; j < competitors.length(); j++) {
                JSONObject x = competitors.optJSONObject(j);
                if (x == null) continue;
                String side = x.optString("homeAway", "");
                if ("home".equalsIgnoreCase(side)) home = x;
                else if ("away".equalsIgnoreCase(side)) away = x;
            }
            if (home == null || away == null) continue;

            JSONObject homeTeam = home.optJSONObject("team");
            JSONObject awayTeam = away.optJSONObject("team");
            String homeName = teamName(homeTeam);
            String awayName = teamName(awayTeam);
            if (homeName.isEmpty() || awayName.isEmpty()) continue;

            long startTs = parseEspnDate(e.optString("date", comp.optString("date", "")));

            JSONObject status = comp.optJSONObject("status");
            JSONObject statusType = status == null ? null : status.optJSONObject("type");
            String state = statusType == null ? "" : statusType.optString("state", "");
            String shortDetail = statusType == null ? "" : statusType.optString("shortDetail", "");

            String middle;
            if ("in".equalsIgnoreCase(state)) {
                middle = score(home) + " - " + score(away);
                if (!shortDetail.isEmpty()) middle += " • " + shortDetail;
                else middle += " • مباشر";
            } else if ("post".equalsIgnoreCase(state)) {
                middle = score(home) + " - " + score(away) + " • انتهت";
            } else {
                middle = formatTime(startTs);
            }

            rows.add(new MatchRow(startTs, homeName + "  " + middle + "  " + awayName));
        }

        rows.sort(Comparator.comparingLong(a -> a.startTs));

        List<String> out = new ArrayList<>();
        for (MatchRow row : rows) out.add(row.text);
        return out;
    }

    private static String teamName(JSONObject team) {
        if (team == null) return "";
        String name = team.optString("shortDisplayName", "");
        if (name.isEmpty()) name = team.optString("displayName", "");
        if (name.isEmpty()) name = team.optString("name", "");
        return name;
    }

    private static String score(JSONObject competitor) {
        if (competitor == null) return "0";
        String s = competitor.optString("score", "");
        return s.isEmpty() ? "0" : s;
    }

    private static long parseEspnDate(String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmX", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso);
            return d == null ? 0L : d.getTime();
        } catch (Exception ignored) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = f.parse(iso);
                return d == null ? 0L : d.getTime();
            } catch (Exception ignored2) {
                return 0L;
            }
        }
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
                String body = get("https://api.frankfurter.dev/v2/rate/"
                        + parts[0].toLowerCase(Locale.ROOT) + "/"
                        + parts[1].toLowerCase(Locale.ROOT));

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
        c.setRequestProperty("User-Agent", "MatchRatesWidget/0.3 Android");
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

    private static class MatchRow {
        final long startTs;
        final String text;

        MatchRow(long startTs, String text) {
            this.startTs = startTs;
            this.text = text;
        }
    }
}
