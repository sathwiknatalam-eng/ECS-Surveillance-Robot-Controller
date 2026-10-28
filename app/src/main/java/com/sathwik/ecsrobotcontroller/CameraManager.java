
package com.sathwik.ecsrobotcontroller;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.WebView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class CameraManager {

    private final WebView cameraWebView;
    private final TextView cameraText;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private volatile boolean running = false;
    private volatile String streamAddress =
            "http://192.168.137.251/stream";

    private Thread cameraThread;
    private volatile HttpURLConnection connection;

    public CameraManager(
            WebView cameraWebView,
            TextView cameraText
    ) {
        this.cameraWebView = cameraWebView;
        this.cameraText = cameraText;

        setupWebView();
    }

    private void setupWebView() {
        cameraWebView.getSettings().setJavaScriptEnabled(true);
        cameraWebView.getSettings().setLoadWithOverviewMode(true);
        cameraWebView.getSettings().setUseWideViewPort(true);
        cameraWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        cameraWebView.getSettings().setBuiltInZoomControls(false);
        cameraWebView.getSettings().setDisplayZoomControls(false);
        cameraWebView.setBackgroundColor(0xFF000000);
    }

    public synchronized void startCamera() {
        stopCamera();

        running = true;
        updateCameraText("CAMERA CONNECTING...");

        cameraThread = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    connectToStream();
                } catch (Exception e) {
                    if (running) {
                        updateCameraText(
                                "CAMERA ERROR: "
                                        + e.getClass().getSimpleName()
                                        + "\n"
                                        + String.valueOf(e.getMessage())
                        );

                        closeConnection();

                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }, "ESP32-Camera-Stream");

        cameraThread.start();
    }

    private void connectToStream() throws IOException {
        URL url = new URL(streamAddress);

        HttpURLConnection activeConnection =
                (HttpURLConnection) url.openConnection();

        connection = activeConnection;

        activeConnection.setRequestMethod("GET");
        activeConnection.setConnectTimeout(5000);
        activeConnection.setReadTimeout(30000);
        activeConnection.setUseCaches(false);
        activeConnection.setDoInput(true);
        activeConnection.setRequestProperty("Connection", "Keep-Alive");

        try {
            activeConnection.connect();

            int responseCode = activeConnection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP response: " + responseCode);
            }

            updateCameraText("CAMERA CONNECTED");

            try (BufferedInputStream input =
                         new BufferedInputStream(
                                 activeConnection.getInputStream())) {
                readMJPEGStream(input);
            }
        } finally {
            activeConnection.disconnect();

            if (connection == activeConnection) {
                connection = null;
            }
        }
    }

    private void readMJPEGStream(BufferedInputStream input)
            throws IOException {

        ByteArrayOutputStream frameBuffer =
                new ByteArrayOutputStream();

        boolean jpegStarted = false;
        int previousByte = -1;

        while (running && !Thread.currentThread().isInterrupted()) {
            int currentByte = input.read();

            if (currentByte == -1) {
                throw new IOException("Camera stream ended");
            }

            if (!jpegStarted) {
                if (previousByte == 0xFF && currentByte == 0xD8) {
                    jpegStarted = true;
                    frameBuffer.reset();
                    frameBuffer.write(0xFF);
                    frameBuffer.write(0xD8);
                }
            } else {
                frameBuffer.write(currentByte);

                if (previousByte == 0xFF && currentByte == 0xD9) {
                    displayFrame(frameBuffer.toByteArray());
                    jpegStarted = false;
                    frameBuffer.reset();
                }

                // Prevent an unexpectedly large buffer from growing forever.
                if (frameBuffer.size() > 2_000_000) {
                    frameBuffer.reset();
                    jpegStarted = false;
                }
            }

            previousByte = currentByte;
        }
    }

    private void displayFrame(byte[] jpegData) {
        if (!running) {
            return;
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(
                jpegData,
                0,
                jpegData.length
        );

        if (bitmap == null) {
            return;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output);
        bitmap.recycle();

        String base64 = Base64.encodeToString(
                output.toByteArray(),
                Base64.NO_WRAP
        );

        mainHandler.post(() -> {
            if (!running) {
                return;
            }

            String html =
                    "<html><head>"
                            + "<meta name='viewport' content='width=device-width,"
                            + " initial-scale=1.0, maximum-scale=1.0'>"
                            + "</head>"
                            + "<body style='margin:0;padding:0;background:black;"
                            + "display:flex;align-items:center;justify-content:center;"
                            + "height:100vh;overflow:hidden;'>"
                            + "<img src='data:image/jpeg;base64,"
                            + base64
                            + "' style='width:100%;height:100%;object-fit:contain;'/>"
                            + "</body></html>";

            cameraWebView.loadDataWithBaseURL(
                    null,
                    html,
                    "text/html",
                    "UTF-8",
                    null
            );
        });
    }

    public synchronized void stopCamera() {
        running = false;

        closeConnection();

        if (cameraThread != null) {
            cameraThread.interrupt();
            cameraThread = null;
        }

        mainHandler.post(() -> {
            cameraWebView.loadData(
                    "<html><body style='margin:0;background:black;"
                            + "display:flex;align-items:center;justify-content:center;"
                            + "height:100vh;color:white;font-family:sans-serif;'>"
                            + "CAMERA OFFLINE</body></html>",
                    "text/html",
                    "UTF-8"
            );

            cameraText.setText("CAMERA OFFLINE");
        });
    }

    private void closeConnection() {
        HttpURLConnection activeConnection = connection;

        if (activeConnection != null) {
            activeConnection.disconnect();
        }
    }

    private void updateCameraText(String text) {
        mainHandler.post(() -> cameraText.setText(text));
    }

    public void setRobotAddress(String robotAddress) {
        if (robotAddress == null || robotAddress.trim().isEmpty()) {
            return;
        }

        String address = robotAddress.trim();

        if (!address.startsWith("http://")
                && !address.startsWith("https://")) {
            address = "http://" + address;
        }

        try {
            URI uri = URI.create(address);

            if (uri.getHost() == null) {
                updateCameraText("INVALID ROBOT IP ADDRESS");
                return;
            }

            // The camera stream always uses port 80.
            streamAddress = uri.getScheme()
                    + "://"
                    + uri.getHost()
                    + "/stream";

        } catch (IllegalArgumentException e) {
            updateCameraText("INVALID ROBOT IP ADDRESS");
        }
    }
}
