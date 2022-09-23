package com.example.bycicmeasure;

import static org.apache.commons.math3.util.Precision.round;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.location.GeocoderNominatim;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsDisplay;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {

    // Static threshold values TODO test them, what are good values?
    private final double thresholdSpeed = 0.5; // 0.5 m/s
    private final double thresholdStreetChangeDistance = 20; // 20m
    // Status variables
    private boolean recording = false;
    private boolean autoMode = true;
    // map object
    private MapView map;
    // geocoder to request street names of geo points
    private GeocoderNominatim geocoder;
    // Values which hold information of street names of geo points and street changes
    private String curr_streetname = null;
    private double streetChangeDistance = 0;
    private boolean streetChanged = false;
    // External class that handles all sensors
    private SensorHandler sensorHandler;
    // Stores that hold all recorded data for each segment
    private ArrayList<ArrayList<Double>> accelVertical_list;
    private ArrayList<ArrayList<Double>> accelTimes_list;
    private ArrayList<ArrayList<Location>> locations_list;
    private ArrayList<Location> locations;
    private Polyline tracks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // load layout
        setContentView(R.layout.activity_map);

        // OSM needs this to Download maps to the cache
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // Construct external sensor handler
        sensorHandler = new SensorHandler(this, new Graph(findViewById(R.id.graphMap)));

        // use Nominatim service for reverse geocoding
        geocoder = new GeocoderNominatim("OSMBonusPackTutoUserAgent");
        // change network policy to use reverse geocoding request to nominatim
        // only necessary if sevice is used in main thread, i think!
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Initialize map controlling
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().getDisplay().setPositions(false, CustomZoomButtonsDisplay.HorizontalPosition.RIGHT, CustomZoomButtonsDisplay.VerticalPosition.CENTER);
        IMapController mapController = map.getController();
        mapController.setZoom(18.5);

        // Initialize all storing variables
        accelVertical_list = new ArrayList<>();
        accelTimes_list = new ArrayList<>();
        locations_list = new ArrayList<>();
        locations = new ArrayList<>();

        Resources res = getResources();
        Drawable startDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_play, null);
        Drawable stopDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_stop, null);

        TextView speedText = findViewById(R.id.speed);

        // This object handles location tracking and following on the map
        MyLocationNewOverlay myLocationoverlay = new MyLocationNewOverlay(map) {
            @Override
            public void onLocationChanged(Location location, IMyLocationProvider source) {
                super.onLocationChanged(location, source);

                if (location != null) {
                    double current_speed = location.getSpeed();
                    speedText.setText((int)current_speed + " km/h");
                    if (recording) {
                        Log.i("myDebug", String.format("Speed: %f", current_speed));

                        if (current_speed > thresholdSpeed && sensorHandler.isCalibrated()) {
                            // 1. save location in list
                            locations.add(location);
                            // 2. add location to the polyline to make it visible
                            tracks.addPoint(new GeoPoint(location.getLatitude(), location.getLongitude()));
                            if (autoMode) {
                                // 4. Optional automatic segmentation
                                automaticSegmentation(location);
                            }
                        }
                    }
                }
            }
        };
        myLocationoverlay.enableFollowLocation();
        myLocationoverlay.enableMyLocation();
        map.getOverlays().add(myLocationoverlay);

        FloatingActionButton startPauseButton = findViewById(R.id.start_stop);
        startPauseButton.setOnClickListener(view -> {
            if (!recording) {
                Log.i("myDebug", "Start Recording");
                // start recording/Start new segment
                startPauseButton.setImageDrawable(stopDrawable);
                // Initialize all variables for a new record and Start the sensors
                initSegment();
                sensorHandler.startSensors();
                sensorHandler.calibrate();
                recording = true;
            } else {
                Log.i("myDebug", "Stop recording");
                // Stop recording/Stop segment
                startPauseButton.setImageDrawable(startDrawable);
                // Evaluate the recorded segment and pause sensors
                if (sensorHandler.getAccelTimes().size() > 1) {
                    evaluateSegment();
                }
                sensorHandler.pauseSensors();
                recording = false;
            }
        });

        // Button that will end the current session and bring us to the result screen
        Button endButton = findViewById(R.id.end);
        endButton.setOnClickListener(view -> {
            startPauseButton.setImageDrawable(startDrawable);
            // Pause sensors, evaluate last recorded segment and write it to a json file
            sensorHandler.pauseSensors();
            recording = false;
            if (sensorHandler.getAccelTimes().size() > 1) {
                evaluateSegment();
            }
            if (accelTimes_list.size() > 0) {
                Utils.writeJSON(locations_list, accelVertical_list, accelTimes_list);
            }
            Intent intent = new Intent(view.getContext(), RecordsActivity.class);
            startActivity(intent);
        });

        // With this toogle we can switch the auto segmentation off and on
        ToggleButton toggle = findViewById(R.id.automode);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> autoMode = isChecked);

        // This button handles recentering the map to the current location
        FloatingActionButton recenterButton = findViewById(R.id.recenter);
        recenterButton.setOnClickListener(view -> {
            myLocationoverlay.enableFollowLocation();
            mapController.setZoom(18.5);
        });
    }

    /**
     * Initialize global variables to start a new recording.
     *
     * In @automode, to prevent the creation of new segments (e.g. at intersections),
     * a certain distance must be covered before a new segment is created.
     * The two parameters serve to not lose this distance!
     */
    private void initSegment() {
        // Initialize variables
        sensorHandler.init();
        locations = new ArrayList<>();

        // Initialize tracking polyline
        tracks = new Polyline(map);
        tracks.getOutlinePaint().setColor(Color.parseColor("#0000FF"));
        tracks.setTitle("IRI:");
        tracks.setSnippet("NAN");
        map.getOverlays().add(tracks);
    }

    /**
     * Evaluating a segment means:
     * 1. Check if it is valid (2 or more data points)
     * 2. Approximate the IRI value
     * 3. Get the right coloring for the calculated iri value
     * 4. Save data of Segment to the big Stores
     */
    private void evaluateSegment() {
        ArrayList<Double> av = sensorHandler.getAccelVertical();
        ArrayList<Double> at = sensorHandler.getAccelTimes();

        if (at.size() < 2 || locations.size() < 2) {
            // Segment is not valid
            // Here we take care of the Polyline
            // Everything else will get automatically removed with the next @initSegment call
            map.getOverlays().remove(map.getOverlays().size() - 1);
            Log.i("myDebug", "Evaluation failed");
        }
        else {
            // Segment is valid
            double iri = Utils.approxIRI(av, at, locations);
            Log.i("myDebug", String.format("iri: %f", iri));

            Paint paint = tracks.getOutlinePaint();
            paint.setColor(Color.parseColor(Utils.getIRIColor(iri)));
            tracks.setSnippet(Double.toString(round(iri, 4)));

            Log.i("myDebug", String.format("Color: %d", paint.getColor()));

            accelVertical_list.add(new ArrayList<>(av));
            accelTimes_list.add(new ArrayList<>(at));
            locations_list.add(new ArrayList<>(locations));
            Log.i("myDebug", "Evaluation successfully");
        }
    }

    /**
     * If a street change occurs and the threshold distance is travelled on the new street,
     * a new segment will be automatically generated for the new street
     *
     * @param location
     */
    private void automaticSegmentation(Location location) {
        if (!recording) {
            return;
        }

        getStreet(location);
        if (streetChangeDistance < thresholdStreetChangeDistance && locations.size() > 2) {
            streetChangeDistance += location.distanceTo(locations.get(locations.size()-2));
        }
        else {
            if (streetChanged) {
                streetChanged = false;
                streetChangeDistance = 0;
                curr_streetname = null;
                if (sensorHandler.getAccelTimes().size() > 1) {
                    evaluateSegment();
                    initSegment();
                }
            }
        }
    }

    /**
     * Updates @curr_streetname via reverse geocoding
     *
     * @param location
     */
    private void getStreet(Location location) {
        new Thread(() -> {
            try {
                List<Address> addressList = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addressList.size() > 0) {
                    String street_name = addressList.get(0).getThoroughfare();
                    if (street_name != null) {
                    }
                    if (street_name != null && streetChangeDistance >= thresholdStreetChangeDistance) {
                        if (curr_streetname == null) {
                            curr_streetname = street_name;
                            Log.d("myDebug", "new street: " + curr_streetname);
                            tracks.setSubDescription(curr_streetname);
                        }
                        if (!curr_streetname.equals(street_name)) {
                            streetChanged = true;
                            curr_streetname = street_name;
                        }
                    }
                }
            } catch (IOException e) {

            }
        }).start();
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
