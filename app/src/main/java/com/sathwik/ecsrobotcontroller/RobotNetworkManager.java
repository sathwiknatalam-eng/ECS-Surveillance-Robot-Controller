
package com.sathwik.ecsrobotcontroller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RobotNetworkManager {

    public interface ConnectionListener {
        void onConnectionChanged(boolean connected);
    }

    private static final String TAG = "RobotNetwork";

    // Camera and IR use port 80.
    private static final int CAMERA_PORT = 80;

    // Status and motor commands use port 81.
    private static final int CONTROL_PORT = 81;

    private final ExecutorService commandExecutor;
    private final ScheduledExecutorService monitorExecutor;
    private final Handler mainHandler;

    private ScheduledFuture<?> connectionMonitor;

    // Default IP. The app's IP settings can replace this.
    private volatile String esp32Address =
            "http://192.168.137.251";

    private volatile ConnectionListener connectionListener;

    public RobotNetworkManager() {
        commandExecutor = Executors.newSingleThreadExecutor();
        monitorExecutor = Executors.newSingleThreadScheduledExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setConnectionListener(ConnectionListener listener) {
        connectionListener = listener;
    }

    /*
     * Accepts an IP address such as:
     * 192.168.137.251
     * http://192.168.137.251
     * http://192.168.137.251:80
     *
     * The app chooses port 80 or 81 separately.
     */
    private String getBaseAddress() {
        String address = esp32Address.trim();

        if (!address.startsWith("http://")
                && !address.startsWith("https://")) {
            address = "http://" + address;
        }

        while (address.endsWith("/")) {
            address = address.substring(0, address.length() - 1);
        }

        try {
            URI uri = URI.create(address);

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                throw new IllegalArgumentException(
                        "Enter a valid ESP32 IP address."
                );
            }

            // Return only scheme and host, removing any supplied port/path.
            return scheme + "://" + host;

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid ESP32 address: " + address, e);
            throw e;
        }
    }

    private String buildUrl(String path, int port) {
        return getBaseAddress() + ":" + port + path;
    }

    private int request(String path, int port) throws IOException {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(buildUrl(path, port));

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "close");

            return connection.getResponseCode();

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void notifyConnection(boolean connected) {
        mainHandler.post(() -> {
            ConnectionListener listener = connectionListener;

            if (listener != null) {
                listener.onConnectionChanged(connected);
            }
        });
    }

    /*
     * Check the control server on port 81.
     */
    public void checkConnection() {
        try {
            monitorExecutor.execute(() -> {
                boolean connected = false;

                try {
                    connected =
                            request("/status", CONTROL_PORT) == 200;

                } catch (Exception e) {
                    Log.e(TAG, "Connection check failed", e);
                }

                notifyConnection(connected);
            });

        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "Connection monitor is shutting down");
        }
    }

    public synchronized void startConnectionMonitoring() {
        if (connectionMonitor != null
                && !connectionMonitor.isCancelled()
                && !connectionMonitor.isDone()) {
            return;
        }

        connectionMonitor = monitorExecutor.scheduleAtFixedRate(
                this::checkConnection,
                0,
                3,
                TimeUnit.SECONDS
        );
    }

    /*
     * Commands are sent through a single-thread executor.
     * This preserves order: movement followed by STOP.
     */
    public void sendCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        String normalizedCommand =
                command.trim().toUpperCase(Locale.ROOT);

        final String path;
        final int port;

        switch (normalizedCommand) {
            case "IR_ON":
                path = "/ir/on";
                port = CAMERA_PORT;
                break;

            case "IR_OFF":
                path = "/ir/off";
                port = CAMERA_PORT;
                break;

            case "FORWARD":
            case "BACKWARD":
            case "LEFT":
            case "RIGHT":
            case "STOP":
                path = "/command?move=" + normalizedCommand;
                port = CONTROL_PORT;
                break;

            default:
                Log.w(TAG, "Unknown command: " + normalizedCommand);
                return;
        }

        try {
            commandExecutor.execute(() -> {
                try {
                    int responseCode = request(path, port);

                    boolean success =
                            responseCode >= 200 && responseCode < 300;

                    Log.i(
                            TAG,
                            normalizedCommand
                                    + " -> port " + port
                                    + ", HTTP " + responseCode
                    );

                    notifyConnection(success);

                } catch (Exception e) {
                    Log.e(
                            TAG,
                            "Command failed: " + normalizedCommand,
                            e
                    );

                    notifyConnection(false);
                }
            });

        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "Command ignored because the app is shutting down");
        }
    }

    public void setEsp32Address(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }

        esp32Address = address.trim();

        Log.i(TAG, "ESP32 address updated");
    }

    public synchronized void shutdown() {
        if (connectionMonitor != null) {
            connectionMonitor.cancel(true);
            connectionMonitor = null;
        }

        monitorExecutor.shutdownNow();
        commandExecutor.shutdownNow();
    }
}
