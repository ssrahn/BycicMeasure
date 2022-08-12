package com.example.bycicmeasure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.location.GeocoderNominatim;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.PaintList;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements SensorEventListener {

    private boolean paused;
    private MapView map;
    private ArrayList<Polyline> tracks;

    private ArrayList<Location> locations;
    private ArrayList<Double> accelVertical;
    private ArrayList<Double> accelTimes;
    private ArrayList<String> streets;

    private long startTime;

    private double calibration;

    private final double thresholdAccel = 0.01;
    private final double thresholdLocation = 1; // 1m
    private final double thresholdDistance = 100; // 100m

    private GeocoderNominatim geocoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_map);

        paused = true;

        // Setup Sensor stuff
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        // Load calibration variable
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        calibration = preferences.getFloat("calibration", 0);

        accelVertical = new ArrayList<>();
        accelTimes = new ArrayList<>();
        locations = new ArrayList<>();
        streets = new ArrayList<>();

        startTime = System.currentTimeMillis();

        // use Nominatim service for reverse geocoding
        geocoder = new GeocoderNominatim("OSMBonusPackTutoUserAgent");
        // change network policy to use reverse geocoding request to nominatim
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Initialize map controlling
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        IMapController mapController = map.getController();
        mapController.setZoom(18.5);

        // Initialize tracking polyline
        tracks = new ArrayList<>();
        tracks.add(new Polyline(map));
        tracks.get(0).getOutlinePaint().setColor(Color.parseColor("#0000FF"));
        map.getOverlays().add(tracks.get(0));

        // This object handles location tracking and following on the map
        MyLocationNewOverlay myLocationoverlay = new MyLocationNewOverlay(map) {
            @Override
            public void onLocationChanged(Location location, IMyLocationProvider source) {
                super.onLocationChanged(location, source);

                if (!paused) {
                    if (locations.size() > 0) {
                        double distance = locations.get(locations.size() -1).distanceTo(location);
                        // only add a location if
                        if(distance > thresholdLocation) {
                            Log.i("myDebug", "Location change");
                            Log.i("myDebug", String.format("Distance: %f", distance));
                            // On location change, add each location to the polyline to make it visible
                            locations.add(location);

                            tracks.get(tracks.size() -1).addPoint(new GeoPoint(location.getLatitude(), location.getLongitude()));
                            getStreetName(location);

                            newSectionCheck();
                        }
                    }else {
                        locations.add(location);
                        tracks.get(tracks.size() -1).addPoint(new GeoPoint(location.getLatitude(), location.getLongitude()));
                        getStreetName(location);
                    }
                }
            }
        };
        myLocationoverlay.enableFollowLocation();
        myLocationoverlay.enableMyLocation();
        map.getOverlays().add(myLocationoverlay);

        Button finishButton = findViewById(R.id.finish);
        finishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                paused = true;
                Intent intent = new Intent(view.getContext(), ResultActivity.class);
                intent.putExtra("AccelerationList", accelVertical);
                intent.putExtra("AccelerationTimesList", accelTimes);
                intent.putExtra("LocationList", locations);
                startActivity(intent);
            }
        });

        Button startPauseButton = findViewById(R.id.start_pause);
        startPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                paused = !paused;
                if (paused) {
                    startPauseButton.setBackgroundColor(Color.parseColor("#4CBB17"));
                    startPauseButton.setText("Start");
                }
                else {
                    startPauseButton.setBackgroundColor(Color.parseColor("#FF0000"));
                    startPauseButton.setText("Pause");
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
            if(accelVertical.size()>0) {
                // check if the last value differs from current value
                if (accelVertical.get(accelVertical.size() - 1) - (event.values[1] - calibration) >= thresholdAccel) {
                    Log.i("myDebug", "Sensor change");
                    // Substract the calibration variable to be more accurate
                    accelVertical.add((double) event.values[1] - calibration);
                    accelTimes.add((double) (System.currentTimeMillis() - startTime) / 1000.0);
                }
            }else{
                accelVertical.add((double) event.values[1] - calibration);
                accelTimes.add((double) (System.currentTimeMillis() - startTime) / 1000.0);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void getStreetName(Location location){
        new Thread(new Runnable() {
            public void run() {
                // a potentially time consuming task
                String theAddress;
                try {
                    List<Address> addressList = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if(addressList.size() > 0){
                        if(addressList.get(0).getThoroughfare() != null) {
                            streets.add(addressList.get(0).getThoroughfare());
                            Log.d("myDebug", "street: " + addressList.get(0).getThoroughfare()); // streetname
                        }else{
                            Log.d("myDebug", "street not available)"); // streetname
                        }
                    }
                } catch (IOException e) {
                    theAddress = null;
                }
            }
        }).start();

    }

    private void newSectionCheck(){
        // check if the street name has changed
        boolean newSection = false;
        if(streets.size() >1) {
            if (!streets.get(streets.size() - 1).equals(streets.get(streets.size() - 2))) {
                Log.i("myDebug", String.format("new street name %s", streets.get(streets.size() - 1)));
                newSection = true;
            }
        }

        // check if the section exceeds the max distance
        if(locations.size() > 1) {
            double distance = 0;
            for (int i = 0; i < locations.size() - 1; i++) {
                distance += locations.get(i).distanceTo(locations.get(i + 1));
            }
            if(distance > thresholdDistance){
                Log.i("myDebug", String.format("max section distance: %f", distance));
                newSection = true;
            }
        }

        //TODO: triangle inequality check

        if(newSection) {
            //TODO: calc iri
            double iri = approxIRI();
            Log.i("myDebug", String.format("iri: %f", iri));


            //TODO: set color
            String color = "#0000FF";
            if(iri <= 0.1){ // good
                color = "#00FF00";
            }
            if(iri > 0.1 && iri <= 1){ // mhh

                color = "#FFFF00";
            }
            if(iri > 1){ // nono

                color = "#FF0000";
            }
            Paint paint = tracks.get(tracks.size() -1).getOutlinePaint();
            paint.setColor(Color.parseColor(color));
            Log.i("myDebug", String.format("Color: %d", paint.getColor()));

            tracks.add(new Polyline(map));
            tracks.get(tracks.size() -1).getOutlinePaint().setColor(Color.parseColor("#0000FF"));
            map.getOverlays().add(tracks.get(tracks.size() -1));

            locations.clear();
            accelVertical.clear();
            accelTimes.clear();
            streets.clear();
        }
    }

    private double approxIRI() {

        if(accelVertical.size() < 2 || locations.size() < 2){
            Log.i("myDebug", "iri calculation failed");
           return 0;
        }

        double iri = 0;
        for (int i=0; i < accelVertical.size() -2; i++) {
            iri += 0.5 * Math.abs(accelVertical.get(i)) * Math.pow(accelTimes.get(i+1) - accelTimes.get(i), 2);
        }

        double distance = 0;
        for (int i=0; i < locations.size()-1; i++) {
            distance += locations.get(i).distanceTo(locations.get(i+1));
        }

        if (distance == 0) {
            return 0;
        }
        return iri/distance;
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
