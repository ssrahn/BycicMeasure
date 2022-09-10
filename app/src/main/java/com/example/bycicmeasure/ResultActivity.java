package com.example.bycicmeasure;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class ResultActivity extends AppCompatActivity {

    private ArrayList<ArrayList<Location>> locations;
    private ArrayList<ArrayList<Double>> accelVertical;
    private ArrayList<ArrayList<Double>> accelTimes;
    private int segments = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        String filename = (String)getIntent().getSerializableExtra("filename");
        Log.i("myDebug", filename);

        JSONObject record = null;
        try {
            FileReader fileReader = new FileReader(filename);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            StringBuilder stringBuilder = new StringBuilder();
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuilder.append(line).append("\n");
                line = bufferedReader.readLine();
            }
            bufferedReader.close();// This responce will have Json Format String
            String responce = stringBuilder.toString();
            record  = new JSONObject(responce);
        }catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(record);

        accelVertical = new ArrayList<>();
        accelTimes = new ArrayList<>();
        locations = new ArrayList<>();
        try{
           for(int i=0; i < record.length(); ++i){
              locations.add(new ArrayList<>());
              accelTimes.add(new ArrayList<>());
              accelVertical.add(new ArrayList<>());
              JSONObject section = record.getJSONObject(String.format("section_%d", i));

              JSONObject locations_json = section.getJSONObject("locations");
              for(int loc_idx=0; loc_idx < locations_json.length(); ++loc_idx){
                  JSONArray entry = locations_json.getJSONArray(String.format("%d", loc_idx));
                  Location loc = new Location("");
                  loc.setLatitude(entry.getDouble(0));
                  loc.setLongitude(entry.getDouble(1));
                  locations.get(i).add(loc);
              }

              JSONObject verticalAccel_json = section.getJSONObject("verticalAccel");
              for(int accel_idx=0; accel_idx < verticalAccel_json.length(); ++accel_idx){
                  JSONArray entry = verticalAccel_json.getJSONArray(String.format("%d", accel_idx));
                  accelVertical.get(i).add(entry.getDouble(0));
                  accelTimes.get(i).add(entry.getDouble(1));
              }
              ++segments;
           }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i("myDebug",String.format("%d",record.length()));
        for(int i=0; i <segments; ++i){
            Log.i("myDebug", String.format("segment %d: %f", i, approxIRI(i)));
        }
    }

    private double approxIRI(int segment_idx) {

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
}
