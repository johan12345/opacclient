package de.geeksfactory.opacclient.webservice;

import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;

import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.storage.JsonSearchFieldDataSource;
import de.geeksfactory.opacclient.storage.PreferenceDataSource;
import de.geeksfactory.opacclient.utils.ErrorReporter;

public class LibraryConfigUpdateJob extends Job {
    public static final String TAG = "LibraryConfigUpdateJob";

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        WebService service = WebServiceManager.getInstance();
        PreferenceDataSource prefs = new PreferenceDataSource(getContext());
        File filesDir =
                new File(getContext().getFilesDir(), LibraryConfigUpdateService.LIBRARIES_DIR);
        filesDir.mkdirs();
        try {
            int count = LibraryConfigUpdateService.updateConfig(service, prefs,
                    new LibraryConfigUpdateService.FileOutput(filesDir),
                    new JsonSearchFieldDataSource(getContext()));
            ((OpacClient) getContext().getApplicationContext()).resetCache();
            return Result.SUCCESS;
        } catch (IOException | JSONException e) {
            ErrorReporter.handleException(e);
            return Result.FAILURE;
        }
    }

    public static void scheduleJob() {
        new JobRequest.Builder(TAG)
                .setPeriodic(5000/*DateTimeConstants.MILLIS_PER_DAY*/)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
                .setRequirementsEnforced(false) // we prefer WiFi (Unmetered), but don't enforce it.
                .build()
                .schedule();
    }
}
