package com.amgadillo.matchrates;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText pairsInput;

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.RIGHT);
        v.setPadding(0, dp(8), 0, dp(6));
        return v;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(16, 24, 39));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView title = label("مبارياتي والعملات", 24, Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);

        root.addView(label("⚽ الفرق الثابتة", 17, Color.rgb(159, 195, 255)));
        root.addView(label(
                "الزمالك • الأهلي المصري • بيراميدز • برشلونة • ريال مدريد • طرابزون سبور",
                15, Color.WHITE));

        root.addView(label("🏆 البطولات الثابتة", 17, Color.rgb(159, 195, 255)));
        root.addView(label(
                "أمم أفريقيا + التصفيات\n" +
                "أمم آسيا + التصفيات\n" +
                "أمم أوروبا + التصفيات\n" +
                "كأس العالم + تصفيات كل القارات\n" +
                "دوري أبطال أفريقيا وأوروبا\n" +
                "كأس الكونفدرالية الأفريقية + الدوري الأوروبي",
                15, Color.WHITE));

        root.addView(label(
                "أي مباراة تخص فريقًا من الفرق الستة تظهر حتى لو كانت خارج البطولات السابقة، وأي مباراة ضمن البطولات السابقة تظهر حتى لو لم يكن أحد الفرق الستة مشاركًا فيها.",
                13, Color.rgb(190, 202, 220)));

        root.addView(label("💱 أزواج العملات", 16, Color.rgb(159, 195, 255)));
        pairsInput = new EditText(this);
        pairsInput.setHint("USD/SAR\nUSD/EGP\nSAR/EGP\nEUR/SAR");
        pairsInput.setHintTextColor(Color.rgb(120, 140, 165));
        pairsInput.setTextColor(Color.WHITE);
        pairsInput.setMinLines(4);
        pairsInput.setGravity(Gravity.TOP | Gravity.LEFT);
        pairsInput.setText(prefs.getString("pairs", "USD/SAR\nUSD/EGP\nSAR/EGP\nEUR/SAR"));
        root.addView(pairsInput, new LinearLayout.LayoutParams(-1, -2));

        Button save = new Button(this);
        save.setText("حفظ العملات وتحديث");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.topMargin = dp(18);
        root.addView(save, buttonParams);

        Button pin = new Button(this);
        pin.setText("إضافة الـ Widget للشاشة الرئيسية");
        LinearLayout.LayoutParams pinParams = new LinearLayout.LayoutParams(-1, dp(52));
        pinParams.topMargin = dp(8);
        root.addView(pin, pinParams);

        TextView note = label(
                "التحديث التلقائي كل 30 دقيقة، ويمكن الضغط على ↻ للتحديث الفوري. المباريات من ESPN، والعملات من Frankfurter.",
                12, Color.rgb(128, 144, 168));
        root.addView(note);

        save.setOnClickListener(v -> {
            prefs.edit()
                    .putString("pairs", pairsInput.getText().toString().trim())
                    .remove("teams")
                    .apply();
            MatchRatesWidget.requestRefresh(this);
            Toast.makeText(this, "تم الحفظ وبدأ التحديث", Toast.LENGTH_SHORT).show();
        });

        pin.setOnClickListener(v -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            ComponentName provider = new ComponentName(this, MatchRatesWidget.class);
            if (manager.isRequestPinAppWidgetSupported()) {
                Intent callbackIntent = new Intent(this, MainActivity.class);
                PendingIntent success = PendingIntent.getActivity(this, 77, callbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                manager.requestPinAppWidget(provider, null, success);
            } else {
                Toast.makeText(this, "اضغط مطولًا على الشاشة الرئيسية ← Widgets ← مبارياتي والعملات", Toast.LENGTH_LONG).show();
            }
        });

        setContentView(scroll);
    }
}
