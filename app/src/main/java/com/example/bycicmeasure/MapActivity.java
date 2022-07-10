package com.example.bycicmeasure;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class MapActivity extends AppCompatActivity {

    private MapView map = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_map);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        IMapController mapController = map.getController();
        mapController.setZoom(18.5);

        // This object handles location tracking and following on the map
        MyLocationNewOverlay myLocationoverlay = new MyLocationNewOverlay(map) {
            @Override
            public void onLocationChanged(Location location, IMyLocationProvider source) {
                super.onLocationChanged(location, source);
                // TODO ALGORITHMS
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
                // Do something
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
