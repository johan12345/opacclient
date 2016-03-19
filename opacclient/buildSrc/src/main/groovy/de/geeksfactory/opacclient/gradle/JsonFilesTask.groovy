package de.geeksfactory.opacclient.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GFileUtils
import org.json.JSONArray
import org.json.JSONObject

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore

class JsonFilesTask extends DefaultTask {
    private static final String API_URL = "https://info.opacapp.net/androidconfig_app/?format=json"
    public static final String BIBS_DIR = "opacapp/src/main/assets/bibs"

    @TaskAction
    def downloadFiles() {
        // Let's Encrypt certificates are not (yet?) trusted by Java JDK :(
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType())
        keystore.load(new FileInputStream("ssl_certificate.jks"), "FVVMHEdautxVcb9h4WdeZdMmvUwUHzgh".toCharArray())
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509")
        tmf.init(keystore)
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null)
        SSLSocketFactory factory = context.getSocketFactory()

        HttpsURLConnection conn = (HttpsURLConnection) API_URL.toURL().openConnection()
        conn.setSSLSocketFactory(factory)
        String response = conn.getInputStream().getText()
        JSONArray data = new JSONArray(response)
        GFileUtils.cleanDirectory(new File(BIBS_DIR))
        for (int i = 0; i < data.length(); i++) {
            JSONObject library = data.get(i);
            String id = library.getString("_id")
            library.remove("_id")
            File file = new File(BIBS_DIR, id + ".json")
            file.write(library.toString(4))
        }
    }
}