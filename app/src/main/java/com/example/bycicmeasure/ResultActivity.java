package com.example.bycicmeasure;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsDisplay;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class ResultActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private ArrayList<ArrayList<Location>> locations;
    private ArrayList<ArrayList<Double>> accelVertical;
    private ArrayList<ArrayList<Double>> accelTimes;
    // map object
    private MapView map;
    private Graph graph;

    private ArrayList<Double> iri;
    private ArrayList<String> color;
    private ArrayList<Polyline> polylines;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // OSM needs this to Download maps to the cache
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        String filename = (String)getIntent().getSerializableExtra("filename");

        accelVertical = new ArrayList<>();
        accelTimes = new ArrayList<>();
        locations = new ArrayList<>();

        Utils.readJSON(filename, locations, accelVertical, accelTimes);

        graph = new Graph(findViewById(R.id.graph));
        graph.setDatapoints(accelTimes.get(0), accelVertical.get(0));

        //get the spinner from the xml.
        Spinner spinner = findViewById(R.id.spinner);
        //create a list of items for the spinner.
        String[] items = new String[locations.size()-1];
        for (int i=0; i<locations.size()-1; i++) {
            items[i] = "Segment " + i;
        }

        // Initialize map controlling
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.getZoomController().getDisplay().setPositions(false, CustomZoomButtonsDisplay.HorizontalPosition.LEFT, CustomZoomButtonsDisplay.VerticalPosition.CENTER);
        IMapController mapController = map.getController();
        mapController.setZoom(18.5);

        evaluate(accelVertical, accelTimes, locations);

        //create an adapter to describe how the items are displayed, adapters are used in several places in android.
        //There are multiple variations of this, but this is the basic variant.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        //set the spinners adapter to the previously created one.
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
    }

    private void evaluate(ArrayList<ArrayList<Double>> av, ArrayList<ArrayList<Double>> at, ArrayList<ArrayList<Location>> l) {
        iri = new ArrayList<>();
        color = new ArrayList<>();
        polylines = new ArrayList<>();
        // Initialize tracking polyline
        for (int i=0; i<l.size(); i++) {
            iri.add(Utils.approxIRI(av.get(i), at.get(i), l.get(i)));
            color.add(Utils.getIRIColor(iri.get(i)));

            Polyline polyline = new Polyline(map);
            for (int j=0; j<l.get(i).size(); j++) {
                polyline.addPoint(new GeoPoint(l.get(i).get(j).getLatitude(), l.get(i).get(j).getLongitude()));
            }
            polyline.getOutlinePaint().setColor(Color.parseColor(color.get(i)));
            polyline.setTitle("IRI: " + String.format("%.4f", iri.get(i)));
            polyline.setSnippet("TODO good/bad");
            map.getOverlays().add(polyline);
            polylines.add(polyline);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        Log.d("myDebug", "Spin to segment: " + i);
        Log.d("myDebug", "size " + accelTimes.get(i).size());
        graph.setDatapoints(accelTimes.get(i), accelVertical.get(i));
        map.zoomToBoundingBox(polylines.get(i).getBounds().increaseByScale(3.0f), true);
        map.setTranslationY(250);
        int infoLoc_idx = polylines.get(i).getActualPoints().size()/2;
        polylines.get(i).setInfoWindowLocation(polylines.get(i).getActualPoints().get(infoLoc_idx));
        polylines.get(i).showInfoWindow();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}
