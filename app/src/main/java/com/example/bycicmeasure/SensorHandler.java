package com.example.bycicmeasure;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import java.util.ArrayList;

public class SensorHandler implements SensorEventListener {

    private final Context context;

    // sensormanager object used to get sensor objects
    private SensorManager sensorManager = null;
    // gravity object
    private Sensor gravity = null;
    // linear accelerometer object without gravity
    private Sensor linearAccelerometer = null;

    private final Double[] accel_arr;
    private final Double[] gravity_arr;

    // arrays that hold measurements
    private final ArrayList<Double> accelVertical;
    private final ArrayList<Double> accelTimes;
    private final ArrayList<Double> accelVerticalCalibrate;

    boolean readLinearAccel = false;
    boolean readGravity = false;
    boolean running = false;

    // thresholds to prevent unnecessary storing of same data
    private final double thresholdAccel = 0.01;
    // start time for diff calculations
    private long startTime;

    private boolean calibrated = false;
    private boolean calibrating = false;
    private long startCalibrateTime;
    private double calibratetime = 2;
    private double offset = 0;

    /**
     * Construct a new SensorHandler with the given @context.
     * @context is needed to activate the sensors.
     *
     * @param context
     */
    public SensorHandler(Context context) {
        this.context = context;
        accel_arr = new Double[3];
        gravity_arr = new Double[3];
        accelVertical = new ArrayList<>();
        accelVerticalCalibrate = new ArrayList<>();
        accelTimes = new ArrayList<>();
    }

    /**
     * Initialize or reset sensor data.
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
        // Setup Sensors
        // -------------
        // - we use accelerometer with gravity sensor to calculate the rotation
        // matrix between smartphone and earth coordination system
        // - the linear accelerometer is used for IRI computation
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
            double vAccel = (accel_arr[0] * gravity_arr[0] / 9.8) + (accel_arr[1] * gravity_arr[1] / 9.8) + (accel_arr[2] * gravity_arr[2] / 9.8);
            double time = (double) (System.currentTimeMillis() - startTime) / 1000.0;
            Log.d("myDebugAcc", "Accel " + vAccel + " - " + time);
            if (calibrated) {
                accelVertical.add(vAccel-offset);
                accelTimes.add(time);
            }
            else if (calibrating) {
                double time_c = (double) (System.currentTimeMillis() - startCalibrateTime) / 1000.0;
                if (time_c < calibratetime) {
                    accelVerticalCalibrate.add(vAccel);
                }
                else {
                    offset = Utils.avg(accelVerticalCalibrate);
                    Log.i("myDebug", "Calibrated: " + offset);
                    calibrated = true;
                    calibrating = false;
                }
            }
        }
    }

    void calibrate() {
        Log.i("myDebug", "calibrating...");
        calibrated = false;
        calibrating = true;
        startCalibrateTime = System.currentTimeMillis();
    }

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
