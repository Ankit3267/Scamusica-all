package com.musicplayer.scamusica.util;

import javax.crypto.CipherOutputStream;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ApiClient {

    private static final int TIMEOUT = 15000;
    private static final int BUFFER_SIZE = 8192;

    public interface ProgressCallback {
        /**
         * Called periodically while downloading.
         *
         * @param bytesRead     so far
         * @param contentLength total content length in bytes (may be -1 if unknown)
         */
        void onProgress(long bytesRead, long contentLength);
    }

    public static String get(String urlString, Map<String, String> headers) throws Exception {
        HttpsURLConnection connection = createConnection(urlString, "GET", headers);
        return getResponse(connection);
    }

    public static String post(String urlString, String jsonBody, Map<String, String> headers) throws Exception {
        HttpsURLConnection connection = createConnection(urlString, "POST", headers);
        writeBody(connection, jsonBody);
        return getResponse(connection);
    }

    public static String put(String urlString, String jsonBody, Map<String, String> headers) throws Exception {
        HttpsURLConnection connection = createConnection(urlString, "PUT", headers);
        writeBody(connection, jsonBody);
        return getResponse(connection);
    }

    public static String delete(String urlString, Map<String, String> headers) throws Exception {
        HttpsURLConnection connection = createConnection(urlString, "DELETE", headers);
        return getResponse(connection);
    }

    private static HttpsURLConnection createConnection(String urlString, String method, Map<String, String> headers) throws Exception {
        URL url = new URL(urlString);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        connection.setReadTimeout(TIMEOUT);
        connection.setConnectTimeout(TIMEOUT);
        connection.setRequestMethod(method);
        connection.setDoInput(true);

        if ("POST".equals(method) || "PUT".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
        }

        if (headers != null) {
            for (String key : headers.keySet()) {
                connection.setRequestProperty(key, headers.get(key));
            }
        }

        return connection;
    }

    private static void writeBody(HttpsURLConnection connection, String jsonBody) throws IOException {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static String getResponse(HttpsURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream is = (status < HttpsURLConnection.HTTP_BAD_REQUEST)
                ? connection.getInputStream()
                : connection.getErrorStream();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            response.append(line);
        }

        br.close();
        connection.disconnect();
        return response.toString();
    }

    /**
     * Download a binary stream (audio) from the given URL to the given output file.
     * If progressCallback != null, it will be called periodically with bytesRead and contentLength.
     * Returns true if download completed (HTTP < 400 and file written), false otherwise.
     */
    public static boolean downloadToFile(String urlString,
                                         Map<String, String> headers,
                                         File outputFile,
                                         ProgressCallback progressCallback) throws Exception {
        HttpsURLConnection connection = createConnection(urlString, "GET", headers);
        connection.setDoInput(true);
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);

        int status = connection.getResponseCode();
        if (status >= HttpsURLConnection.HTTP_BAD_REQUEST) {
            InputStream err = connection.getErrorStream();
            if (err != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(err))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    System.out.println("[ApiClient] downloadToFile error response: " + sb.toString());
                } catch (Exception ignored) {
                }
            }
            connection.disconnect();
            return false;
        }

        long contentLength = connection.getContentLengthLong();
        InputStream is = connection.getInputStream();

        try (BufferedInputStream in = new BufferedInputStream(is);
             FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bout = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalRead = 0L;

            while ((bytesRead = in.read(buffer, 0, BUFFER_SIZE)) != -1) {
                bout.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (progressCallback != null) {
                    try {
                        progressCallback.onProgress(totalRead, contentLength);
                    } catch (Exception ignored) {
                    }
                }
            }
            bout.flush();
        } finally {
            connection.disconnect();
        }

        return true;
    }

    public static boolean downloadEncrypted(String urlStr,
                                            Map<String, String> headers,
                                            File outFile,
                                            ProgressCallback callback) {

        HttpURLConnection connection = null;

        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return false;
            }

            long contentLength = connection.getContentLengthLong();

            try (InputStream in = connection.getInputStream();
                 FileOutputStream fos = new FileOutputStream(outFile);
                 CipherOutputStream cos = CryptoUtil.encrypt(fos)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long total = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    cos.write(buffer, 0, bytesRead);
                    cos.flush();
                    total += bytesRead;

                    if (callback != null) {
                        callback.onProgress(total, contentLength);
                    }
                }
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
