package com.cle.ambientnotifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;


/**
 * Created by christian on 1/1/15.
 */
public class NotifListenerService extends NotificationListenerService {
    private String TAG = this.getClass().getSimpleName();

    private NotifListenerServiceReceiver notifListenerServiceReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        notifListenerServiceReceiver = new NotifListenerServiceReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.cle.ambientnotifications.NOTIFICATION_LISTENER_SERVICE");
        registerReceiver(notifListenerServiceReceiver, filter);

        Log.d(TAG, "NotifListenerService is running");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(notifListenerServiceReceiver);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notif) {
        Log.i(TAG, "**********  onNotificationPosted");
        Log.i(TAG,"ID :" + notif.getId() + "t" + notif.getNotification().tickerText +
                "t" + notif.getPackageName());
        Intent i = new Intent("com.cle.ambientnotifications.NOTIFICATION_LISTENER");
        i.putExtra("notification_event", "NotificationPosted");
        i.putExtra("notification_package", notif.getPackageName());
        sendBroadcast(i);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notif) {
        Log.i(TAG, "**********  onNotificationRemoved");
        Log.i(TAG,"ID :" + notif.getId() + "t" + notif.getNotification().tickerText +
                "t" + notif.getPackageName());
        Intent i = new Intent("com.cle.ambientnotifications.NOTIFICATION_LISTENER");
        i.putExtra("notification_event", "NotificationRemoved");
        i.putExtra("notification_package", notif.getPackageName());
        sendBroadcast(i);
    };

    class NotifListenerServiceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getStringExtra("command").equals("clearall")) {
                NotifListenerService.this.cancelAllNotifications();
            } else if (intent.getStringExtra("command").equals("list")) {
                Log.d(TAG, "command received");

                Intent i = new Intent("com.cle.ambientnotifications.NOTIFICATION_LISTENER");
                i.putExtra("notification_event", "=========");
                sendBroadcast(i);

                int counter = 1;
                for (StatusBarNotification notif : NotifListenerService.this.getActiveNotifications()) {
                    Intent notifIntent =
                            new Intent("com.cle.ambientnotifications.NOTIFICATION_LISTENER");
                    notifIntent.putExtra("notification_event", counter + " " +
                            notif.getPackageName() + "\n");
                    sendBroadcast(notifIntent);
                    counter += 1;
                }

                Intent listIntent =
                        new Intent("com.cle.ambientnotifications.NOTIFICATION_LISTENER");
                listIntent.putExtra("notification_event", "==== Notification List ====");
                sendBroadcast(listIntent);
            }
        }
    }
}
