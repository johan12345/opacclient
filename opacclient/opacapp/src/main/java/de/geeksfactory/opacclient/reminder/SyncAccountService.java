/**
 * Copyright (C) 2013 by Raphael Michel under the MIT license:
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package de.geeksfactory.opacclient.reminder;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.geeksfactory.opacclient.BuildConfig;
import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.apis.OpacApi;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.AccountData;
import de.geeksfactory.opacclient.objects.Library;
import de.geeksfactory.opacclient.storage.AccountDataSource;

public class SyncAccountService extends WakefulIntentService {

    private static final String NAME = "SyncAccountService";
    private boolean failed = false;

    public SyncAccountService() {
        super(NAME);
    }

    @Override
    protected void doWakefulWork(Intent intent) {
        if (BuildConfig.DEBUG) Log.i(NAME, "SyncAccountService started");

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        if (!sp.getBoolean(SyncAccountAlarmListener.PREF_SYNC_SERVICE, false)) {
            if (BuildConfig.DEBUG) Log.i(NAME, "notifications are disabled");
            return;
        }

        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null) {
            if (!sp.getBoolean("notification_service_wifionly", false) ||
                    networkInfo.getType() == ConnectivityManager.TYPE_WIFI ||
                    networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                syncAccounts();
            } else {
                failed = true;
            }
        } else {
            failed = true;
        }

        if (BuildConfig.DEBUG) {
            Log.i(NAME, "SyncAccountService finished " +
                    (failed ? " with errors" : " " + "successfully"));
        }

        long previousPeriod = sp.getLong(SyncAccountAlarmListener.PREF_SYNC_INTERVAL, 0);
        long newPeriod = failed ? AlarmManager.INTERVAL_HOUR : AlarmManager.INTERVAL_HALF_DAY;
        if (previousPeriod != newPeriod) {
            sp.edit().putLong(SyncAccountAlarmListener.PREF_SYNC_INTERVAL, newPeriod).apply();
            WakefulIntentService.cancelAlarms(this);
            WakefulIntentService
                    .scheduleAlarms(SyncAccountAlarmListener.withOnePeriodBeforeStart(), this);
        }
    }

    private void syncAccounts() {
        OpacClient app = (OpacClient) getApplication();
        AccountDataSource data = new AccountDataSource(this);
        List<Account> accounts = data.getAccountsWithPassword();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        if (!sp.contains("update_151_clear_cache")) {
            data.invalidateCachedData();
            sp.edit().putBoolean("update_151_clear_cache", true).apply();
       }

        Map<Long, AccountData> map = new HashMap<>();
        for (Account account : accounts) {
            if (BuildConfig.DEBUG) {
                Log.i(NAME, "Loading data for Account " + account.toString());
            }

            AccountData res;
            try {
                Library library = app.getLibrary(account.getLibrary());
                if (!library.isAccountSupported()) continue;
                OpacApi api = app.getNewApi(library);
                res = api.account(account);
                if (res == null) {
                    failed = true;
                    continue;
                }
            } catch (JSONException | IOException | OpacApi.OpacErrorException e) {
                e.printStackTrace();
                failed = true;
                continue;
            }

            account.setPasswordKnownValid(true);
            data.update(account);
            map.put(account.getId(), res);
        }
        new ReminderHelper(app).generateAlarmsAndSaveData(map);
    }

}
