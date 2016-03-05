package com.op.android.xrppricewidget;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class RippleWidgetProvider extends AppWidgetProvider {

    public static String ACTION_WIDGET_RELOAD = "reload";
    private String priceText = null;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        context.startService(new Intent(context, UpdateService.class));
    }

    public static class UpdateService extends Service {

        public static final String RIPPLE_PRICE_URL = "http://data.ripple.com/v2/exchanges/XRP/USD+rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B?limit=1&descending=true";

        @Override
        public void onStart(Intent intent, int startId) {

            RemoteViews updateViews = buildUpdate(this);
            AppWidgetManager.getInstance(this).updateAppWidget(new ComponentName(this, RippleWidgetProvider.class), updateViews);

        }

        /**
         * Build a widget update
         */
        public RemoteViews buildUpdate(Context context) {

            //yes it's bad, but we download file with 5 bytes size
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            Date curDate = new Date();

            PendingIntent actionPendingIntent = getPendingIntent(context);

            views.setOnClickPendingIntent(R.id.widget_button, actionPendingIntent);
            try {
                tryToGetPrice(context, views);
            } catch (Exception e) {
                restoreSavedPrice(context, views, curDate);
            }

            String date = new SimpleDateFormat("EE HH:mm").format(curDate);
            views.setTextViewText(R.id.upd_date, date);
            return views;
        }

        private void tryToGetPrice(Context context, RemoteViews views) throws IOException, JSONException {
            String widgetText = String.format("$%.4f", readXRPPriceFromJson());
            views.setTextViewText(R.id.ripple_price, widgetText);
            SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(context).edit();
            edit.putString("SAVED_PRICE", widgetText).putLong("SAVED_DATE", Calendar.getInstance().getTimeInMillis()).commit();
        }

        private void restoreSavedPrice(Context context, RemoteViews views, Date curDate) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String priceText = preferences.getString("SAVED_PRICE", null);
            Long dateLong = preferences.getLong("SAVED_DATE", Calendar.getInstance().getTimeInMillis());
            curDate.setTime(dateLong);
            views.setTextViewText(R.id.ripple_price, priceText);
        }

        private PendingIntent getPendingIntent(Context context) {
            Intent active = new Intent(context, RippleWidgetProvider.class);
            active.setAction(ACTION_WIDGET_RELOAD);
            return PendingIntent.getBroadcast(context, 0, active, 0);
        }

        private double readXRPPriceFromJson() throws IOException, JSONException {
            String json = getJsonFromRest();
            JSONObject jObject = new JSONObject(json);
            JSONArray jsonArray = jObject.getJSONArray("exchanges");
            JSONObject so = (JSONObject) jsonArray.get(0);
            return so.getDouble("rate");
        }

        private String getJsonFromRest() throws IOException {
            URL url = new URL(RIPPLE_PRICE_URL);
            InputStream is = url.openStream();
            String json = "";
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        is, "iso-8859-1"), 8);
                StringBuilder sb = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                is.close();
                json = sb.toString();
            } catch (Exception e) {
                Log.e("Buffer Error", "Error converting result " + e.toString());
            }
            return json;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Receive event
        final String action = intent.getAction();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (priceText == null) {
            priceText = preferences.getString("SAVED_PRICE", null);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            if (priceText != null) {
                views.setTextViewText(R.id.ripple_price, priceText);
            }
        }
        //currentPreferences.edit().putString(Constants.LIST_ID, "0").commit();

        if (ACTION_WIDGET_RELOAD.equals(action)) {
            Toast.makeText(context, context.getResources().getString(R.string.loading), Toast.LENGTH_SHORT).show();
            context.startService(new Intent(context, UpdateService.class));
        }
        super.onReceive(context, intent);
    }
}