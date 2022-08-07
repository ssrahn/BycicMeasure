package com.example.bycicmeasure;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class CalibrateActivity extends AppCompatActivity implements SensorEventListener {

    private static final double WAITTIME = 5000;
    private static final double ERRORBOUND = 0.5;

    private TextView progressText;
    private boolean calibrating;
    private long startTime;
    private ArrayList<Double> accel;
    SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_calibrate);

        calibrating = false;

        // Initialize sensoring stuff
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        // Stores all acceleration data during calibration
        accel = new ArrayList<>();

        // Use this to store the calibration value permanently
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Button startButton = findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!calibrating) {
                    // Make progress text visible and modify it
                    progressText = (TextView) findViewById(R.id.text_calibrate);
                    progressText.setText("Calibrating... ");
                    progressText.setTextColor(Color.parseColor("#4CBB17"));
                    progressText.setVisibility(View.VISIBLE);
                    // Start the calibration -> see onSensorChange()
                    calibrating = true;
                    startTime = System.currentTimeMillis();
                }

            }
        });

        Button cancelButton = findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), MainActivity.class);
                startActivity(intent);
            }
        });
    }

    private double calculateAverage(ArrayList<Double> l) {
        return l.stream()
                .mapToDouble(d -> d)
                .average()
                .orElse(0.0);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        // Start calibration progress, if @calibration is true
        if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && calibrating) {
            accel.add((double) event.values[1]);
            // Show remaining time in progress text
            int remainingTime = (int) (WAITTIME - (System.currentTimeMillis() - startTime))/1000;
            progressText.setText("Calibrating... " + remainingTime);

            // Enter, if calibration time finished
            if (remainingTime < 0) {
                calibrating = false;

                // Calculate standard deviation
                double mean = calculateAverage(accel);
                double deviation = 0;
                for (int i=0; i<accel.size(); i++) {
                    deviation += Math.pow(accel.get(i) - mean, 2);
                }
                deviation = Math.sqrt(deviation);

                accel.clear();

                // Check if the process was good enough
                if (deviation < ERRORBOUND) {
                    // Save calibration permanently
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putFloat("calibration", (float)mean);
                    editor.apply();

                    progressText.setText("Done!");
                }
                else {
                    progressText.setTextColor(Color.parseColor("#FF0000"));
                    progressText.setText("Failed... try again!");
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
