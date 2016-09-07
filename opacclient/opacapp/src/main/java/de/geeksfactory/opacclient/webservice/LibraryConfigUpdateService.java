/**
 * Copyright (C) 2016 by Johan von Forstner under the MIT license:
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
package de.geeksfactory.opacclient.webservice;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.objects.Library;
import de.geeksfactory.opacclient.storage.PreferenceDataSource;
import de.geeksfactory.opacclient.utils.ErrorReporter;
import retrofit2.Response;

public class LibraryConfigUpdateService extends IntentService {
    private static final String NAME = "LibraryConfigUpdateService";
    public static final String ACTION_SUCCESS = NAME + "_success";
    public static final String ACTION_FAILURE = NAME + "_failure";
    public static final String EXTRA_UPDATE_COUNT = "update_count";
    public static final String LIBRARIES_DIR = "libraries";

    public LibraryConfigUpdateService() {
        super(NAME);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        WebService service = WebServiceManager.getInstance();
        PreferenceDataSource prefs = new PreferenceDataSource(this);
        File filesDir = new File(getFilesDir(), LIBRARIES_DIR);
        filesDir.mkdirs();
        try {
            int count = updateConfig(service, prefs, new FileOutput(filesDir));
            Intent broadcast = new Intent(ACTION_SUCCESS).putExtra(EXTRA_UPDATE_COUNT, count);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
            ((OpacClient) getApplication()).resetCache();
        } catch (IOException | JSONException e) {
            ErrorReporter.handleException(e);
            Intent broadcast = new Intent(ACTION_FAILURE);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
    }

    static int updateConfig(WebService service, PreferenceDataSource prefs, FileOutput output)
            throws IOException, JSONException {
        Response<List<Library>>
                response = service.getLibraryConfigs(prefs.getLastLibraryConfigUpdate()).execute();
        List<Library> updatedLibraries = response.body();

        for (Library lib : updatedLibraries) {
            String filename = lib.getIdent() + ".json";
            JSONObject json = lib.toJSON();
            output.writeFile(filename, json.toString());
        }

        DateTime lastUpdate = new DateTime(response.headers().get("X-Page-Generated"));
        prefs.setLastLibraryConfigUpdate(lastUpdate);

        return updatedLibraries.size();
    }

    static class FileOutput {
        private final File dir;

        public FileOutput(File dir) {
            this.dir = dir;
        }

        public void writeFile(String filename, String data) throws IOException {
            File file = new File(dir, filename);
            FileWriter writer = null;
            try {
                writer = new FileWriter(file);
                writer.write(data);
            } finally {
                if (writer != null) writer.close();
            }
        }
    }
}
