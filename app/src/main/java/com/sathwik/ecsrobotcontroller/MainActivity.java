package com.sathwik.ecsrobotcontroller;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final int HOME = 0;
    private static final int CONTROL = 1;
    private static final int WIFI = 2;
    private static final int RECORDING = 3;
    private static final int DISCONNECTED = 4;

    private final Handler recordingHandler =
            new Handler(Looper.getMainLooper());

    private RobotNetworkManager robotNetworkManager;
    private CameraManager cameraManager;
    private RobotCommand activeMovement;

    private View[] screens;

    private TextView statusText;
    private TextView commandText;
    private TextView headerStatus;
    private TextView homeConnectionText;

    private TextView recordingBadge;
    private TextView recordingStatusText;

    private Button irButton;
    private Button recordButton;
    private Button recordingActionButton;

    private boolean irLightOn;
    private boolean isRecording;

    private long recordingStartedAt;

    private int currentScreen = HOME;

    private SharedPreferences preferences;


    private final Runnable timerUpdate = new Runnable() {

        @Override
        public void run() {

            if (!isRecording) {
                return;
            }

            long seconds =
                    (System.currentTimeMillis()
                            - recordingStartedAt) / 1000;

            String time =
                    String.format(
                            "%02d:%02d",
                            seconds / 60,
                            seconds % 60
                    );

            recordingBadge.setText(
                    "● REC " + time
            );

            recordingStatusText.setText(
                    "● Recording " + time +
                            "\nDestination: " +
                            selectedStorage()
            );

            recordingHandler.postDelayed(
                    this,
                    1000
            );
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(
                R.layout.activity_main
        );

        preferences =
                getSharedPreferences(
                        "spybot_preferences",
                        MODE_PRIVATE
                );

        robotNetworkManager =
                new RobotNetworkManager();

        bindViews();

        styleButtons();

        setupNetwork();

        setupControls();

        showScreen(HOME);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {

                    Insets bars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    v.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return insets;
                }
        );

        robotNetworkManager
                .startConnectionMonitoring();
    }


    private void bindViews() {

        screens = new View[]{
                findViewById(R.id.homeScreen),
                findViewById(R.id.controlScreen),
                findViewById(R.id.wifiScreen),
                findViewById(R.id.recordingScreen),
                findViewById(R.id.disconnectedScreen)
        };


        statusText =
                findViewById(
                        R.id.statusText
                );

        commandText =
                findViewById(
                        R.id.commandText
                );

        headerStatus =
                findViewById(
                        R.id.headerStatus
                );

        homeConnectionText =
                findViewById(
                        R.id.homeConnectionText
                );

        recordingBadge =
                findViewById(
                        R.id.recordingBadge
                );

        recordingStatusText =
                findViewById(
                        R.id.recordingStatusText
                );

        irButton =
                findViewById(
                        R.id.irButton
                );

        recordButton =
                findViewById(
                        R.id.recordButton
                );

        recordingActionButton =
                findViewById(
                        R.id.recordingActionButton
                );


        /*
         * CAMERA
         */
        WebView cameraWebView =
                findViewById(
                        R.id.cameraWebView
                );

        cameraManager =
                new CameraManager(
                        cameraWebView,
                        findViewById(
                                R.id.cameraText
                        )
                );


        /*
         * ROBOT IP
         */
        EditText ipInput =
                findViewById(
                        R.id.robotIpInput
                );

        ipInput.setText(
                preferences.getString(
                        "robot_ip",
                        "http://192.168.137.251"
                )
        );


        /*
         * SAME ADDRESS FOR:
         *
         * Robot commands
         * Camera streaming
         */
        String robotAddress =
                ipInput
                        .getText()
                        .toString()
                        .trim();

        robotNetworkManager
                .setEsp32Address(
                        robotAddress
                );

        cameraManager
                .setRobotAddress(
                        robotAddress
                );
    }


    private void setupNetwork() {

        robotNetworkManager
                .setConnectionListener(
                        connected -> {

                            int color =
                                    getColor(
                                            connected
                                                    ? R.color.green
                                                    : R.color.red
                                    );

                            String label =
                                    connected
                                            ? "● CONNECTED"
                                            : "● OFFLINE";

                            headerStatus
                                    .setText(label);

                            headerStatus
                                    .setTextColor(color);

                            statusText
                                    .setText(
                                            connected
                                                    ? "Connection: CONNECTED • ESP32 reachable"
                                                    : "Connection: OFFLINE • Check Wi-Fi or robot IP"
                                    );

                            homeConnectionText
                                    .setText(
                                            connected
                                                    ? "● Robot connected and ready"
                                                    : "● Robot not connected"
                                    );

                            homeConnectionText
                                    .setTextColor(
                                            color
                                    );
                        }
                );
    }


    private void setupControls() {

        /*
         * OPEN CONTROL
         */
        findViewById(
                R.id.openControlButton
        ).setOnClickListener(
                v -> showScreen(CONTROL)
        );


        /*
         * ROBOT IP
         */
        findViewById(
                R.id.openRobotIpButton
        ).setOnClickListener(
                v -> showScreen(WIFI)
        );


        /*
         * BACK BUTTON
         */
        findViewById(
                R.id.backButton
        ).setOnClickListener(
                v -> showScreen(parentScreen())
        );


        /*
         * TRY AGAIN
         */
        findViewById(
                R.id.tryAgainButton
        ).setOnClickListener(
                v -> robotNetworkManager
                        .checkConnection()
        );


        /*
         * OPEN ROBOT IP FROM DISCONNECTED SCREEN
         */
        findViewById(
                R.id.disconnectedSettingsButton
        ).setOnClickListener(
                v -> showScreen(WIFI)
        );


        /*
         * SAVE & CONNECT
         */
        findViewById(
                R.id.connectButton
        ).setOnClickListener(
                v -> saveRobotIp()
        );


        /*
         * IR
         */
        irButton.setOnClickListener(
                v -> toggleIRLight()
        );


        /*
         * RECORDING
         *
         * Kept intact.
         */
        recordButton.setOnClickListener(
                v -> toggleRecording()
        );

        recordingActionButton.setOnClickListener(
                v -> toggleRecording()
        );


        /*
         * MOVEMENT
         */
        setupMovementButton(
                R.id.forwardButton,
                RobotCommand.FORWARD
        );

        setupMovementButton(
                R.id.backwardButton,
                RobotCommand.BACKWARD
        );

        setupMovementButton(
                R.id.leftButton,
                RobotCommand.LEFT
        );

        setupMovementButton(
                R.id.rightButton,
                RobotCommand.RIGHT
        );
    }


    private void showScreen(int screen) {

        currentScreen = screen;

        for (int i = 0;
             i < screens.length;
             i++) {

            screens[i].setVisibility(
                    i == screen
                            ? View.VISIBLE
                            : View.GONE
            );
        }


        /*
         * Back button is shown on
         * every screen except Home.
         */
        findViewById(
                R.id.backButton
        ).setVisibility(
                screen == HOME
                        ? View.GONE
                        : View.VISIBLE
        );


        TextView title =
                findViewById(
                        R.id.topTitle
                );

        TextView subtitle =
                findViewById(
                        R.id.topSubtitle
                );


        if (screen == HOME) {

            title.setText("NIGHT VISION ROBOT");
            subtitle.setText(
                    "SURVEILLANCE CONTROL"
            );

        } else if (screen == CONTROL) {

            title.setText(
                    "LIVE CONTROL"
            );

            subtitle.setText(
                    "HOLD TO MOVE • RELEASE TO STOP"
            );

        } else if (screen == WIFI) {

            title.setText(
                    "ROBOT IP"
            );

            subtitle.setText(
                    "ESP32-CAM CONNECTION"
            );

        } else if (screen == RECORDING) {

            title.setText(
                    "RECORDING"
            );

            subtitle.setText(
                    "VIDEO CONTROL"
            );

        } else {

            title.setText(
                    "CONNECTION"
            );

            subtitle.setText(
                    "SURVEILLANCE CONTROL"
            );
        }


        /*
         * Start camera when entering
         * Live Control.
         */
        if (screen == CONTROL) {

            cameraManager.startCamera();
        }
    }


    private int parentScreen() {

        if (currentScreen == HOME) {
            return HOME;
        }

        return HOME;
    }


    private void saveRobotIp() {

        String address =
                ((EditText) findViewById(
                        R.id.robotIpInput
                ))
                        .getText()
                        .toString()
                        .trim();

        if (address.isEmpty()) {
            return;
        }


        if (!address.startsWith("http://") &&
                !address.startsWith("https://")) {

            address =
                    "http://" + address;
        }


        /*
         * SAME IP FOR:
         *
         * Robot commands
         * Camera streaming
         */
        robotNetworkManager
                .setEsp32Address(
                        address
                );

        cameraManager
                .setRobotAddress(
                        address
                );


        preferences
                .edit()
                .putString(
                        "robot_ip",
                        address
                )
                .apply();


        commandText.setText(
                "Robot IP saved: " + address
        );


        robotNetworkManager
                .checkConnection();


        /*
         * Return to Home after
         * saving the robot IP.
         */
        showScreen(HOME);
    }


    private void sendCommand(
            RobotCommand command
    ) {

        commandText.setText(
                "Command: " +
                        command.name()
        );

        robotNetworkManager
                .sendCommand(
                        command.name()
                );
    }


    private void setupMovementButton(
            int id,
            RobotCommand command
    ) {

        findViewById(id)
                .setOnTouchListener(
                        (v, event) -> {

                            if (event.getAction() ==
                                    MotionEvent.ACTION_DOWN) {

                                if (activeMovement != null &&
                                        activeMovement != command) {

                                    sendCommand(
                                            RobotCommand.STOP
                                    );
                                }

                                activeMovement =
                                        command;

                                sendCommand(
                                        command
                                );

                                return true;
                            }


                            if (event.getAction() ==
                                    MotionEvent.ACTION_UP ||
                                    event.getAction() ==
                                            MotionEvent.ACTION_CANCEL) {

                                if (activeMovement ==
                                        command) {

                                    sendCommand(
                                            RobotCommand.STOP
                                    );

                                    activeMovement =
                                            null;
                                }

                                return true;
                            }

                            return false;
                        }
                );
    }


    private void toggleIRLight() {

        sendCommand(
                irLightOn
                        ? RobotCommand.IR_OFF
                        : RobotCommand.IR_ON
        );

        irLightOn =
                !irLightOn;

        irButton.setText(
                irLightOn
                        ? "IR  ON"
                        : "IR  OFF"
        );

        irButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        getColor(
                                irLightOn
                                        ? R.color.blue
                                        : R.color.navy_800
                        )
                )
        );
    }


    /*
     * RECORDING
     *
     * Existing functionality preserved.
     */
    private void toggleRecording() {

        isRecording =
                !isRecording;


        if (isRecording) {

            recordingStartedAt =
                    System.currentTimeMillis();

            recordingBadge
                    .setVisibility(
                            View.VISIBLE
                    );

            recordButton
                    .setText(
                            "■ STOP"
                    );

            recordingActionButton
                    .setText(
                            "STOP RECORDING"
                    );

            recordButton
                    .setBackgroundTintList(
                            ColorStateList.valueOf(
                                    getColor(
                                            R.color.red
                                    )
                            )
                    );

            recordingHandler.post(
                    timerUpdate
            );

        } else {

            recordingHandler
                    .removeCallbacks(
                            timerUpdate
                    );

            recordingBadge
                    .setVisibility(
                            View.GONE
                    );

            recordButton
                    .setText(
                            "● RECORD"
                    );

            recordingActionButton
                    .setText(
                            "START RECORDING"
                    );

            recordButton
                    .setBackgroundTintList(
                            ColorStateList.valueOf(
                                    getColor(
                                            R.color.purple_dark
                                    )
                            )
                    );

            recordingStatusText
                    .setText(
                            "● Not recording\nDestination: " +
                                    selectedStorage()
                    );
        }
    }


    private String selectedStorage() {

        return ((RadioButton) findViewById(
                R.id.storageSd
        )).isChecked()
                ? "Robot SD card"
                : "Phone storage";
    }


    private void styleButtons() {

        int[] ids = {
                R.id.openControlButton,
                R.id.openRobotIpButton,
                R.id.forwardButton,
                R.id.backwardButton,
                R.id.leftButton,
                R.id.rightButton,
                R.id.irButton,
                R.id.recordButton,
                R.id.connectButton,
                R.id.recordingActionButton,
                R.id.tryAgainButton,
                R.id.disconnectedSettingsButton
        };

        for (int id : ids) {

            Button button = findViewById(id);

            if (button == null) {
                continue;
            }

            button.setTextColor(
                    getColor(R.color.text_primary)
            );

            button.setAllCaps(false);

            button.setBackground(
                    makeRounded(
                            getColor(R.color.purple_dark),
                            16
                    )
            );
        }



        /*
         * Primary buttons.
         */
        for (int id : new int[]{

                R.id.openControlButton,
                R.id.openRobotIpButton,
                R.id.connectButton,
                R.id.recordingActionButton,
                R.id.tryAgainButton

        }) {

            ((Button) findViewById(id))
                    .setBackground(
                            makeRounded(
                                    getColor(
                                            R.color.purple_dark
                                    ),
                                    16
                            )
                    );
        }
    }


    private GradientDrawable makeRounded(
            int color,
            int radiusDp
    ) {

        GradientDrawable shape =
                new GradientDrawable();

        shape.setColor(color);

        shape.setCornerRadius(
                radiusDp *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );

        return shape;
    }


    @Override
    public void onBackPressed() {

        if (currentScreen ==
                HOME) {

            super.onBackPressed();

            return;
        }

        showScreen(
                parentScreen()
        );
    }


    @Override
    protected void onDestroy() {

        recordingHandler
                .removeCallbacks(
                        timerUpdate
                );

        if (robotNetworkManager != null) {

            robotNetworkManager
                    .shutdown();
        }

        if (cameraManager != null) {

            cameraManager
                    .stopCamera();
        }

        super.onDestroy();
    }
}