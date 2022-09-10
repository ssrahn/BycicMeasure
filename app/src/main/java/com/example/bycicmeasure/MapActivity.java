package com.example.bycicmeasure;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.location.GeocoderNominatim;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapActivity extends AppCompatActivity implements SensorEventListener {

    // sensormanager object used to get sensor objects
    private SensorManager sensorManager = null;
    // normal accelerometer object with gravity
    private Sensor accelerometer = null;
    // magnetometer object
    private Sensor magneticField = null;
    // linear accelerometer object without gravity
    private Sensor linearAccelerometer = null;

    // array to save one measurement of normal accelerometer with gravity
    private final float[] accelerometerReading = new float[3];
    // array to save one measurement of the magnetometer
    private final float[] magnetometerReading = new float[3];
    // flags
    private boolean readAccelerometer = false;
    private boolean readMagnerometer = false;
    private boolean calibrated = false;

    // matrix objects to hold rotation matrix between earth and smartphone coordinate system
    private RealMatrix rotationMatrix = null;
    // inverse rotation matrix which is used to transform linear accelerometer readings in earth coordinate system
    private RealMatrix inverseRotationMatrix = null;

    // not used
    private boolean recording = false;

    // map object
    private MapView map;
    // array of path segments which are drawn on the map
    private ArrayList<Polyline> tracks;

    // arrays that hold measurements and geo locations
    private ArrayList<ArrayList<Location>> locations;
    private ArrayList<ArrayList<Double>> accelVertical;
    private ArrayList<ArrayList<Double>> accelTimes;
    private int segment_idx = 0;

    // array which holds street names of geo points
    private ArrayList<String> streets;

    // start time for diff calculations
    private long startTime;

    // thresholds to prevent unnecessary storing of same data
    private final double thresholdAccel = 0.01;
    private final double thresholdLocation = 1; // 1m
    private final double thresholdDistance = 100; // 100m
    private final double thresholdCurve = 0.8;

    // geocoder to request street names of geo points
    private GeocoderNominatim geocoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // load app context to access saved preference variables
        // TODO: the calibration is now in this activity, this makes the context unnecessary I think
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // load layout
        setContentView(R.layout.activity_map);


        accelVertical = new ArrayList<>();
        accelVertical.add(new ArrayList<>());

        accelTimes = new ArrayList<>();
        accelTimes.add(new ArrayList<>());

        locations = new ArrayList<>();
        locations.add(new ArrayList<>());

        // TODO: a list is unnecessary we only need last and current street name
        streets = new ArrayList<>();

        startTime = System.currentTimeMillis();

        // use Nominatim service for reverse geocoding
        geocoder = new GeocoderNominatim("OSMBonusPackTutoUserAgent");
        // change network policy to use reverse geocoding request to nominatim
        // only necessary if sevice is used in main thread, i think!
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

                if (recording) {
                    double distance = 0;
                    if (locations.get(segment_idx).size() > 0) {
                        // calculate the distance between current and last location
                        Location last_location = locations.get(segment_idx).get(locations.get(segment_idx).size() -1);
                        distance = last_location.distanceTo(location);
                    }
                    else{
                        // if location list is empty make sure that current location is added
                        distance = thresholdDistance;
                    }

                    // On location change
                    if(distance >= thresholdLocation) {
                        Log.i("myDebug", String.format("Distance: %f", distance));

                        // 1. save location in list
                        locations.get(segment_idx).add(location);
                        // 2. add location to the polyline to make it visible
                        tracks.get(tracks.size() -1).addPoint(new GeoPoint(location.getLatitude(), location.getLongitude()));
                        // 3. get street name associated with location
                        // - get street name service is not synced with main thread. It is likely
                        // that a street name change is only detected on the next location change
                        getStreetName(location);
                        // 4. check if a new path section is necessary
                        newSectionCheck();
                    }
                }
            }
        };

        myLocationoverlay.enableFollowLocation();
        myLocationoverlay.enableMyLocation();
        map.getOverlays().add(myLocationoverlay);

        Button startPauseButton = findViewById(R.id.start_finish);
        startPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recording = !recording;

                if (recording) {
                    // start recording
                    startPauseButton.setBackgroundColor(Color.parseColor("#D6D7D7"));
                    startPauseButton.setText("Finish");

                    // register needed sensors
                    startSensors();
                }
                else {
                    // check if last recording can be used as a section
                    newSection();

                    pauseLinearAccelerometer();
                    // finish recording
                    writeJSON();
                    Intent intent = new Intent(view.getContext(), RecordsActivity.class);
                    startActivity(intent);
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

    private void startSensors(){
        // Setup Sensors
        // -------------
        // - we use accelerometer with gravity and the magneticField sensor to calculate the rotation
        // matrix between smartphone and earth coordination system
        // - the linear accelerometer is used for IRI computation
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            Log.i("myDebug", "Register accelerometer");
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            Log.i("myDebug", "Register magneticField");
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(linearAccelerometer != null){
            Log.i("myDebug", "Register linear accelerometer");
            sensorManager.registerListener(this, linearAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
            readAccelerometer = true;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
            readMagnerometer = true;
        }
        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && calibrated){

            // transform in earth coordination system
            // TODO: vertical acceleration is index 2 after transformation, but why?
            RealVector linearAccelerationVec = MatrixUtils.createRealVector(new double[]{(double) event.values[0], (double) event.values[1], (double) event.values[2]});
            linearAccelerationVec = inverseRotationMatrix.preMultiply(linearAccelerationVec);

            double[] linearAccelArray = linearAccelerationVec.toArray();

            double delta = 0;
            if(accelVertical.get(segment_idx).size()>0) {
                double last_accelVertical = accelVertical.get(segment_idx).get(accelVertical.get(segment_idx).size() -1);
                delta = last_accelVertical - (linearAccelArray[2]);
            }
            else{
                delta = thresholdAccel;
            }

            // check if the last value differs from current value
            if ( delta >= thresholdAccel) {
                Log.i("myDebug", String.format(" %.02f", linearAccelArray[0]) +String.format(" %.02f", linearAccelArray[1]) +String.format(" %.02f", linearAccelArray[2]) );
                // save vertical acceleration with timestamp
                accelVertical.get(segment_idx).add(linearAccelArray[2]);
                accelTimes.get(segment_idx).add((double) (System.currentTimeMillis() - startTime) / 1000.0);
            }
        }

        if(readMagnerometer && readAccelerometer){
            updateOrientationAngles();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private void pauseCalibration(){
        sensorManager.unregisterListener(this, accelerometer);
        sensorManager.unregisterListener(this, magneticField);
    }

    private void pauseLinearAccelerometer(){
        sensorManager.unregisterListener(this, linearAccelerometer);
    }

    private void updateOrientationAngles() {
        readAccelerometer = false;
        readMagnerometer = false;

        // calculate rotation matrix
        float[] localRotationMatrix = new float[9];
        SensorManager.getRotationMatrix(localRotationMatrix, null,
                accelerometerReading, magnetometerReading);

        // typecast in 2D array and create RealMatix object
        double [][] rotationMatrix2D = {{localRotationMatrix[0], localRotationMatrix[1], localRotationMatrix[2]},{localRotationMatrix[3], localRotationMatrix[4], localRotationMatrix[5]},{localRotationMatrix[6], localRotationMatrix[7], localRotationMatrix[8]}};
        rotationMatrix = MatrixUtils.createRealMatrix(rotationMatrix2D);

        // calculate inverse rotation matrix
        inverseRotationMatrix = MatrixUtils.inverse(rotationMatrix);

        printMat(inverseRotationMatrix.getData());
        calibrated = true;
        pauseCalibration();
    }

    private void printMat(double[][] mat){
        Log.i("myDebug", "mat" + String.format(" %d",mat.length) + "x" +String.format("%d",mat[0].length));
        Log.i("myDebug", "-----------");
        for(int row = 0; row < mat.length; ++row){
            String row_string = "";
            for(int col = 0; col < mat[0].length; ++col){
              row_string += String.format(" %.02f",mat[row][col]);
            }
            Log.i("myDebug", row_string);
        }
        Log.i("myDebug", "-----------");
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

        // check if the segment exceeds the max distance
        if(locations.get(segment_idx).size() >= 2) {
            double distance = 0;
            for (int i = 0; i < locations.get(segment_idx).size() - 1; i++) {
                Location loc1 = locations.get(segment_idx).get(i);
                Location loc2 = locations.get(segment_idx).get(i + 1);
                distance += loc1.distanceTo(loc2);
            }
            if(distance > thresholdDistance){
                Log.i("myDebug", String.format("max section distance: %f", distance));
                newSection = true;
            }
        }

        //curve idx check
        if(locations.get(segment_idx).size() >= 3){
            Location loc1 = locations.get(segment_idx).get(locations.get(segment_idx).size() -3);
            Location loc2 = locations.get(segment_idx).get(locations.get(segment_idx).size() -2);
            Location loc3 = locations.get(segment_idx).get(locations.get(segment_idx).size() -1);

            double dist1 = loc1.distanceTo(loc2) + loc2.distanceTo(loc3);
            double dist2 = loc1.distanceTo(loc3);
            Log.i("myDebug", String.format("curve idx: %f", dist2/dist1));
            if(dist2/dist1 <= thresholdCurve){
                newSection = true;
            }
        }

        if(newSection) {
            newSection();
        }
    }

    private void newSection(){
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

        locations.add(new ArrayList<>());
        accelVertical.add(new ArrayList<>());
        accelTimes.add(new ArrayList<>());
        segment_idx +=1;

        streets.clear();
    }

    private double approxIRI() {

        if(accelVertical.get(segment_idx).size() < 2 || locations.get(segment_idx).size() < 2){
            Log.i("myDebug", "iri calculation failed");
           return 0;
        }

        double iri = 0;
        for (int i=0; i < accelVertical.get(segment_idx).size() -2; i++) {
            double accelTime1 = accelTimes.get(segment_idx).get(i);
            double accelTime2 = accelTimes.get(segment_idx).get(i +1);
            iri += 0.5 * Math.abs(accelVertical.get(segment_idx).get(i)) * Math.pow(Math.abs(accelTime2 - accelTime1), 2);
        }

        double distance = 0;
        for (int i=0; i < locations.get(segment_idx).size() - 2; i++) {
            Location loc1 = locations.get(segment_idx).get(i);
            Location loc2 = locations.get(segment_idx).get(i + 1);
            distance += loc1.distanceTo(loc2);
        }

        if (distance == 0) {
            return 0;
        }
        return iri/distance;
    }

    private void writeJSON() {
        JSONObject record = new JSONObject();
        try {
            for(int i=0; i < locations.size(); ++i) {

                JSONObject location_obj = new JSONObject();
                for (int loc_idx = 0; loc_idx < locations.get(i).size(); ++loc_idx) {
                    JSONArray entry = new JSONArray();
                    entry.put(locations.get(i).get(loc_idx).getLatitude());
                    entry.put(locations.get(i).get(loc_idx).getLongitude());
                    location_obj.putOpt(String.format("%d", loc_idx), entry);
                }

                JSONObject accel_obj = new JSONObject();
                for(int accel_idx = 0; accel_idx < accelVertical.get(i).size(); ++accel_idx){
                    JSONArray entry = new JSONArray();
                    entry.put(accelVertical.get(i).get(accel_idx));
                    entry.put(accelTimes.get(i).get(accel_idx));
                    accel_obj.putOpt(String.format("%d", accel_idx), entry);
                }

                JSONObject segment = new JSONObject();
                segment.putOpt("locations", location_obj);
                segment.putOpt("verticalAccel", accel_obj);
                record.putOpt(String.format("section_%d", i), segment);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(record);

        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),"bycicmeasure" );
        if(!dir.exists()){
            dir.mkdir();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String currentDateandTime = sdf.format(new Date());
        File file = new File(dir, String.format("record_%s.json", currentDateandTime));

        if(file.exists()){
            if(file.delete()){
                Log.i("myDebug", "existing file deleted");
            } else{
                Log.i("myDebug", "could not delete existing file");
            }
        }

        try{
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.append(record.toString());
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        Log.i("myDebug", "write JSON File");
        Log.i("myDebug", file.getAbsolutePath());
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
