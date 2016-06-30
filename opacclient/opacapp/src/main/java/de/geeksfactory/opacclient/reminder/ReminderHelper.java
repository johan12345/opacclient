package de.geeksfactory.opacclient.reminder;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.geeksfactory.opacclient.BuildConfig;
import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.AccountData;
import de.geeksfactory.opacclient.objects.LentItem;
import de.geeksfactory.opacclient.storage.AccountDataSource;

public class ReminderHelper {
    private OpacClient app;
    private SharedPreferences sp;

    public ReminderHelper(OpacClient app) {
        this.app = app;
        sp = PreferenceManager.getDefaultSharedPreferences(app);
    }

    public void generateAlarmsAndSaveData(Account account, AccountData data) {
        Map<Long, AccountData> map = new HashMap<>();
        map.put(account.getId(), data);
        generateAlarmsAndSaveData(map);
    }

    /**
     * Save account data and alarms for expiring media to the DB and schedule them using {@link
     * android.app.AlarmManager}.
     */
    public void generateAlarmsAndSaveData(Map<Long, AccountData> map) {
        AccountDataSource data = new AccountDataSource(app);
        List<LentItem> items = new ArrayList<>();

        for (Account account : data.getAllAccounts()) {
            if (map.containsKey(account.getId())) {
                items.addAll(map.get(account.getId()).getLent());
            } else {
                items.addAll(data.getCachedAccountData(account).getLent());
            }
        }

        int warning = getWarningPeriod();
        boolean enabled = isNotificationEnabled();

        if (!enabled) {
            if (BuildConfig.DEBUG) {
                Log.d("OpacClient", "scheduling no alarms because notifications are disabled");
            }
            return;
        }

        GenerateAlarmsResult result = generateAlarms(warning, items, data);

        data.beginTransaction();
        try {
            // resets the notified field to false for all alarms with finished == false this will
            // re-show notifications that were not dismissed yet (for example after reboot)
            data.resetNotifiedOnAllAlarams();

            saveAlarms(result, data);
            for (Account account : data.getAllAccounts()) {
                if (map.containsKey(account.getId())) {
                    data.storeCachedAccountData(account, map.get(account.getId()));
                }
            }
            data.setTransactionSuccessful();
        } finally {
            data.endTransaction();
        }
        scheduleAlarms();
    }

    private void saveAlarms(GenerateAlarmsResult result, AccountDataSource data) {
        for (Alarm alarm : result.alarmsToAdd) data.addAlarm(alarm);
        for (Alarm alarm : result.alarmsToUpdate) data.updateAlarm(alarm);
        for (Alarm alarm : result.alarmsToRemove) data.removeAlarm(alarm);
    }

    public void regenerateAlarms() {
        regenerateAlarms(getWarningPeriod(), isNotificationEnabled());
    }

    private void regenerateAlarms(int warning, boolean enabled) {
        if (!enabled) {
            if (BuildConfig.DEBUG) {
                Log.d("OpacClient", "scheduling no alarms because notifications are disabled");
            }
            return;
        }

        AccountDataSource data = new AccountDataSource(app);
        List<LentItem> items = data.getAllLentItems();

        GenerateAlarmsResult result = generateAlarms(warning, items, data);

        data.beginTransaction();
        try {
            // resets the notified field to false for all alarms with finished == false this will
            // re-show notifications that were not dismissed yet (for example after reboot)
            data.resetNotifiedOnAllAlarams();

            saveAlarms(result, data);
            data.setTransactionSuccessful();
        } finally {
            data.endTransaction();
        }
        scheduleAlarms(true);
    }

    private GenerateAlarmsResult generateAlarms(int warning, List<LentItem> items,
            AccountDataSource data) {
        // Sort lent items by deadline
        Map<LocalDate, List<Long>> arrangedIds = new HashMap<>();
        for (LentItem item : items) {
            LocalDate deadline = item.getDeadline();
            if (deadline == null) {
                // Fail silently to not annoy users. We display a warning in account view in
                // this case.
                continue;
            }
            if (item.getDownloadData() != null && item.getDownloadData().startsWith("http")) {
                // Don't remind people of bringing back ebooks, because ... uhm...
                continue;
            }
            if (!arrangedIds.containsKey(deadline)) {
                arrangedIds.put(deadline, new ArrayList<Long>());
            }
            arrangedIds.get(deadline).add(item.getDbId());
        }

        GenerateAlarmsResult result = new GenerateAlarmsResult();

        for (Alarm alarm : data.getAllAlarms()) {
            // Remove alarms with no corresponding media
            if (!arrangedIds.containsKey(alarm.deadline)) {
                cancelNotification(alarm);
                result.alarmsToRemove.add(alarm);
            }
        }

        // Find and add/update corresponding alarms for current lent media
        for (Map.Entry<LocalDate, List<Long>> entry : arrangedIds.entrySet()) {
            LocalDate deadline = entry.getKey();
            long[] media = toArray(entry.getValue());
            Alarm alarm = data.getAlarmByDeadline(deadline);
            if (alarm != null) {
                if (!Arrays.equals(media, alarm.media)) {
                    alarm.media = media;
                    result.alarmsToUpdate.add(alarm);
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.i("OpacClient",
                            "scheduling alarm for " + media.length + " items with deadline on " +
                                    DateTimeFormat.shortDate().print(deadline) + " on " +
                                    DateTimeFormat.shortDate().print(deadline.minusDays(warning)));
                }
                alarm = new Alarm();
                alarm.deadline = deadline;
                alarm.media = media;
                alarm.notificationTime = deadline.minusDays(warning).toDateTimeAtStartOfDay();
                result.alarmsToAdd.add(alarm);
            }
        }
        return result;
    }

    /**
     * Update alarms when the warning period setting is changed
     * @param newWarning new warning period
     */
    public void updateAlarms(int newWarning) {
        // We could do this better, but for now, let's simply recreate all alarms. This can
        // result in some notifications being shown immediately.
        cancelAllNotifications();
        clearAlarms();
        regenerateAlarms(newWarning, isNotificationEnabled());
    }

    /**
     * Update alarms when notifications were enabled or disabled
     * @param enabled Whether notification were enabled or disabled
     */
    public void updateAlarms(boolean enabled) {
        cancelAllNotifications();
        clearAlarms();
        regenerateAlarms(getWarningPeriod(), enabled);
    }

    private void clearAlarms() {
        AccountDataSource data = new AccountDataSource(app);
        data.clearAlarms();
    }

    private void cancelNotification(Alarm alarm) {
        NotificationManager notificationManager = (NotificationManager) app
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel((int) alarm.id);
    }

    private void cancelAllNotifications() {
        NotificationManager notificationManager = (NotificationManager) app
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    /**
     * (re-)schedule alarms using {@link android.app.AlarmManager}
     */
    public void scheduleAlarms() {
        scheduleAlarms(false);
    }

    private void scheduleAlarms(boolean enabled) {
        if (!isNotificationEnabled() && !enabled) return;

        AccountDataSource data = new AccountDataSource(app);
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);

        List<Alarm> alarms = data.getAllAlarms();

        for (Alarm alarm : alarms) {
            if (!alarm.notified) {
                Intent i = new Intent(app, ReminderBroadcastReceiver.class);
                i.setAction(ReminderBroadcastReceiver.ACTION_SHOW_NOTIFICATION);
                i.putExtra(ReminderBroadcastReceiver.EXTRA_ALARM_ID, alarm.id);
                PendingIntent pi = PendingIntent
                        .getBroadcast(app, (int) alarm.id, i, PendingIntent.FLAG_UPDATE_CURRENT);
                // If the alarm's timestamp is in the past, AlarmManager will trigger it
                // immediately.
                setExact(alarmManager, AlarmManager.RTC_WAKEUP, alarm.notificationTime.getMillis(),
                        pi);
            }
        }
    }

    private long[] toArray(List<Long> list) {
        long[] array = new long[list.size()];
        for (int i = 0; i < list.size(); i++) array[i] = list.get(i);
        return array;
    }

    @SuppressWarnings("SameParameterValue")
    private void setExact(AlarmManager am, int type, long triggerAtMillis,
            PendingIntent operation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(type, triggerAtMillis, operation);
        } else {
            am.set(type, triggerAtMillis, operation);
        }
    }

    public void resetNotified() {
        AccountDataSource data = new AccountDataSource(app);
        data.resetNotifiedOnAllAlarams();
    }

    private static class GenerateAlarmsResult {
        public List<Alarm> alarmsToRemove;
        public List<Alarm> alarmsToUpdate;
        public List<Alarm> alarmsToAdd;

        public GenerateAlarmsResult() {
            alarmsToRemove = new ArrayList<>();
            alarmsToUpdate = new ArrayList<>();
            alarmsToAdd = new ArrayList<>();
        }
    }

    private int getWarningPeriod() {
        int warning = Integer.parseInt(sp.getString("notification_warning", "3"));
        if (warning > 10) {
            // updated from the old app version -> change value to get days instead of milliseconds
            warning = warning / (24 * 60 * 60 * 1000);
            sp.edit().putString("notification_warning", String.valueOf(warning)).apply();
        }
        return warning;
    }

    private boolean isNotificationEnabled() {
        return sp.getBoolean("notification_service", false);
    }
}
