package com.example.bycicmeasure;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;

public class SensorHandler implements SensorEventListener {

    private final Context context;

    // sensormanager object used to get sensor objects
    private SensorManager sensorManager = null;
    // gravity object
    private Sensor gravity = null;
    // linear accelerometer object without gravity
    private Sensor linearAccelerometer = null;

    // Arrays used to store latest measured data
    private final Double[] accel_arr;
    private final Double[] gravity_arr;

    // Lists that hold all measurements
    private final ArrayList<Double> accelVertical;
    private final ArrayList<Double> accelTimes;
    private final ArrayList<Double> accelVerticalCalibrate;

    // Status variables
    private boolean readLinearAccel = false;
    private boolean readGravity = false;
    private boolean running = false;
    private boolean calibrated = false;
    private boolean calibrating = false;

    // Start time to calculate timestamps for measurements
    private long startTime;
    // Calibration variables
    private double calibratetime = 2; // 2s
    private long startCalibrateTime;
    private double offset = 0;
    // Dynamic gaph that plots latest measurements
    private final Graph graph;

    /**
     * Construct a new SensorHandler with the given @context.
     * @context is needed to activate the sensors.
     *
     * @param context
     */
    public SensorHandler(Context context, Graph graph) {
        this.context = context;
        accel_arr = new Double[3];
        gravity_arr = new Double[3];
        accelVertical = new ArrayList<>();
        accelVerticalCalibrate = new ArrayList<>();
        accelTimes = new ArrayList<>();
        this.graph = graph;
    }

    /**
     * Reinitialize sensor data.
     * Will be called for every new segment.
     */
    void init() {
        accelVertical.clear();
        accelTimes.clear();
    }

    /**
     * Start Linear acceleration and gravity sensors
     */
    void startSensors() {
        // To get the direction towards gravity, we use the linear accelerometer together with the gravity sensor
        if (!running) {
            sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
            gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (gravity != null) {
                Log.i("myDebug", "Register gravity");
                sensorManager.registerListener(this, gravity,
                        SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
            }
            linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            if (linearAccelerometer != null) {
                Log.i("myDebug", "Register linear accelerometer");
                sensorManager.registerListener(this, linearAccelerometer,
                        SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
            }
            startTime = System.currentTimeMillis();
            running = true;
        }
    }

    /**
     * Function that handles all sensor changes
     *
     * @param event
     */
    public void onSensorChanged(SensorEvent event) {

        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            accel_arr[0] = (double) event.values[0];
            accel_arr[1] = (double) event.values[1];
            accel_arr[2] = (double) event.values[2];
            readLinearAccel = true;
        }

        if (sensor.getType() == Sensor.TYPE_GRAVITY) {
            gravity_arr[0] = (double) event.values[0];
            gravity_arr[1] = (double) event.values[1];
            gravity_arr[2] = (double) event.values[2];
            readGravity = true;
        }

        if (readLinearAccel && readGravity) {
            // Calculate vertical acceleration if linear acc and gravity was measured
            double vAccel = (accel_arr[0] * gravity_arr[0] / 9.8) + (accel_arr[1] * gravity_arr[1] / 9.8) + (accel_arr[2] * gravity_arr[2] / 9.8);
            double time = (double) (System.currentTimeMillis() - startTime) / 1000.0;
            if (calibrated) {
                // Append the data, if the device is calibrated
                accelVertical.add(vAccel-offset);
                accelTimes.add(time);
                graph.appendDatapoint(vAccel-offset);
            }
            else if (calibrating) {
                // Do calibration
                double time_c = (double) (System.currentTimeMillis() - startCalibrateTime) / 1000.0;
                if (time_c < calibratetime) {
                    // Read data while calibration is in progress
                    accelVerticalCalibrate.add(vAccel);
                }
                else {
                    // On finish, calculate the offset
                    offset = Utils.avg(accelVerticalCalibrate);
                    calibrated = true;
                    calibrating = false;
                }
            }
        }
    }

    /**
     * Calibrate the accelerometer
     */
    void calibrate() {
        // Each devices accelerometer comes with an offset.
        // In calibration mode, we calculate that offset
        calibrated = false;
        calibrating = true;
        startCalibrateTime = System.currentTimeMillis();
    }

    /**
     * Check if the sensorHandler is calibrated
     * @return
     */
    boolean isCalibrated() {
        return calibrated;
    }

    /**
     *
     * @return Acceleration times
     */
    ArrayList<Double> getAccelTimes() {
        return new ArrayList<>(accelTimes);
    }

    /**
     *
     * @return Acceleration vertical
     */
    ArrayList<Double> getAccelVertical() {
        return new ArrayList<>(accelVertical);
    }

    /**
     * Pause Linear accelerometer and gravity sensor
     */
    void pauseSensors() {
        if (running) {
            sensorManager.unregisterListener(this, linearAccelerometer);
            sensorManager.unregisterListener(this, gravity);
            readLinearAccel = false;
            readGravity = false;
            running = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
