package com.example.bycicmeasure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.sql.Time;
import java.util.ArrayList;

public class MapActivity extends AppCompatActivity implements SensorEventListener {

    private boolean paused = false;
    private MapView map = null;
    private Polyline track;

    private SensorManager sensorManager;
    private Sensor sensor;
    private ArrayList<Double> accelVertical;
    private ArrayList<Double> accelTimes;

    private long startTime;

    private SharedPreferences preferences;
    private double calibration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_map);

        // Setup Sensor stuff
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        // Load calibration variable
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        calibration = (double) preferences.getFloat("calibration", 0);

        accelVertical = new ArrayList<>();
        accelTimes = new ArrayList<>();

        startTime = System.currentTimeMillis();

        // Initialize map controlling
        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        IMapController mapController = map.getController();
        mapController.setZoom(18.5);

        // Initialize tracking polyline
        track = new Polyline(map);
        track.getOutlinePaint().setColor(Color.parseColor("#0000FF"));
        map.getOverlays().add(track);

        // This object handles location tracking and following on the map
        MyLocationNewOverlay myLocationoverlay = new MyLocationNewOverlay(map) {
            @Override
            public void onLocationChanged(Location location, IMyLocationProvider source) {
                super.onLocationChanged(location, source);

                if (!paused) {
                    // On location change, add each location to the polyline to make it visible
                    track.addPoint(new GeoPoint(location.getLatitude(), location.getLongitude()));
                    // TODO ALGORITHMS
                }
            }
        };
        myLocationoverlay.enableFollowLocation();
        myLocationoverlay.enableMyLocation();
        map.getOverlays().add(myLocationoverlay);

        Button stopButton = findViewById(R.id.stop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), ResultActivity.class);
                startActivity(intent);
            }
        });

        Button pauseButton = findViewById(R.id.pause);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                paused = !paused;
                if (paused) {
                    pauseButton.setText("Resume");
                }
                else {
                    pauseButton.setText("Pause");
                }
            }
        });

        Button recenterButton = findViewById(R.id.recenter);
        recenterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myLocationoverlay.enableFollowLocation();
                mapController.setZoom(18.5);
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && !paused) {
            // substract the calibration variable to be more accurate
            accelVertical.add((double) event.values[1] - calibration);
            accelTimes.add((double) (System.currentTimeMillis() - startTime)/1000.0);
            // TODO ALGORITHMS
            Log.d("TESTING", (double) event.values[1] - calibration + "");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    @Override
    public void onPause() {
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }
}
