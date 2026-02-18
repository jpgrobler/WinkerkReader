package za.co.jpsoft.winkerkreader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
//import android.support.v4.app.NotificationCompat;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
    //SharedPreferences settings = getSharedPreferences(PREFS_USER_INFO, 0);
     //   if (!DateTime.now().toString().substring(1,10).equals(settings.getString("SMS","")){

        long when = System.currentTimeMillis();

        int notificationId = 1;
        String channelId = "wkr-01";
        String channelName = "WinkerkReader";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    channelId, channelName, importance);
            notificationManager.createNotificationChannel(mChannel);
        }
        if (!intent.getAction().isEmpty()) {
            //Stuur verjaarsdag sms?
            if (intent.getAction().equals("VerjaarSMS")) {
                Intent notificationIntent = new Intent(context, VerjaarSMS2.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                        notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Date now = new Date();
                int id = Integer.parseInt(new SimpleDateFormat("ddHHmmss", Locale.US).format(now));

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.mipmap.ic_stat_family_roof)
                        .setContentTitle("WinkerkReader")
                        .setContentText("Stuur verjaarsdag SMS'e").setSound(alarmSound)
                        .setAutoCancel(true).setWhen(when)
                        .setColor(39372)
                        .setAutoCancel(true).setWhen(when)
                        .setContentIntent(pendingIntent);

                notificationManager.notify(id, mBuilder.build());
            }

            // Download database van DropBox
            if (intent.getAction().equals("DropBoxDownLoad")) {
                Intent notificationIntent = new Intent(context, Laaidatabasis.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                        notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Date now = new Date();
                int id = Integer.parseInt(new SimpleDateFormat("ddHHmmss", Locale.US).format(now));

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.mipmap.ic_stat_family_roof)
                        .setContentTitle("WinkerkReader")
                        .setContentText("Laai data van DropBox").setSound(alarmSound)
                        .setAutoCancel(true).setWhen(when)
                        .setColor(39372)
                        .setAutoCancel(true).setWhen(when)
                        .setContentIntent(pendingIntent);

                notificationManager.notify(id, mBuilder.build());
            }
        }
    }
}