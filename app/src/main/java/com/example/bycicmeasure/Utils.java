package com.example.bycicmeasure;

import android.location.Location;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Utils {
    static double avg(ArrayList<Double> list) {
        double sum = 0;
        for (int i=0; i<list.size(); i++) {
            sum += list.get(i);
        }
        return sum/list.size();
    }

    /**
     * Categorize the iri value and return the mathcing color
     *
     * @param iri
     * @return Matching color for @iri
     */
    static String getIRIColor(double iri) {
        //TODO: set color
        String color = "#0000FF";
        if (iri <= 0.1) { // good
            color = "#00FF00";
        }
        if (iri > 0.1 && iri <= 1) { // mhh
            color = "#FFFF00";
        }
        if (iri > 1) { // nono
            color = "#FF0000";
        }
        return color;
    }

    /**
     * Approximates the iri
     *
     * @param av  Acceleration vertical
     * @param at  Acceleration times
     * @param loc Locations
     * @return IRI Value
     */
    static double approxIRI(ArrayList<Double> av, ArrayList<Double> at, ArrayList<Location> loc) {
        double iri = 0;
        for (int i = 0; i < at.size() - 2; i++) {
            double accelTime1 = at.get(i);
            double accelTime2 = at.get(i + 1);
            iri += 0.5 * Math.abs(av.get(i)) * Math.pow(Math.abs(accelTime2 - accelTime1), 2);
        }

        double distance = 0;
        for (int i = 0; i < loc.size() - 2; i++) {
            Location loc1 = loc.get(i);
            Location loc2 = loc.get(i + 1);
            distance += loc1.distanceTo(loc2);
        }

        if (distance == 0) {
            return 0;
        }
        return iri / distance;
    }

    /**
     * Writes the given parameters to a json file
     *
     * @param loc   Locations
     * @param accel Acceleration vertical
     * @param times Acceleration times
     */
    static void writeJSON(ArrayList<ArrayList<Location>> loc, ArrayList<ArrayList<Double>> accel, ArrayList<ArrayList<Double>> times) {
        JSONObject record = new JSONObject();
        try {
            for (int i = 0; i < loc.size(); ++i) {
                JSONObject location_obj = new JSONObject();
                for (int loc_idx = 0; loc_idx < loc.get(i).size(); ++loc_idx) {
                    JSONArray entry = new JSONArray();
                    entry.put(loc.get(i).get(loc_idx).getLatitude());
                    entry.put(loc.get(i).get(loc_idx).getLongitude());
                    location_obj.putOpt(String.format("%d", loc_idx), entry);
                }

                JSONObject accel_obj = new JSONObject();
                double offset = times.get(i).get(0);
                for (int accel_idx = 0; accel_idx < accel.get(i).size(); ++accel_idx) {
                    JSONArray entry = new JSONArray();
                    entry.put(accel.get(i).get(accel_idx));
                    entry.put(times.get(i).get(accel_idx) - offset);
                    accel_obj.putOpt(String.format("%d", accel_idx), entry);
                }

                JSONObject segment = new JSONObject();
                segment.putOpt("locations", location_obj);
                segment.putOpt("verticalAccel", accel_obj);
                record.putOpt(String.format("segment_%d", i), segment);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        System.out.println(record);

        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "bycicmeasure");
        if (!dir.exists()) {
            dir.mkdir();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String currentDateandTime = sdf.format(new Date());
        File file = new File(dir, String.format("record_%s.json", currentDateandTime));

        if (file.exists()) {
            if (file.delete()) {
                Log.i("myDebug", "existing file deleted");
            } else {
                Log.i("myDebug", "could not delete existing file");
            }
        }

        try {
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.append(record.toString());
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i("myDebug", "write JSON File");
        Log.i("myDebug", file.getAbsolutePath());
    }

    static void readJSON(String filename, ArrayList<ArrayList<Location>> locations, ArrayList<ArrayList<Double>> accelVertical, ArrayList<ArrayList<Double>> accelTimes) {
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

        Log.i("myDebug",String.format("Records %d",record.length()));
        try{
            for(int i=0; i < record.length(); ++i){
                locations.add(new ArrayList<>());
                accelTimes.add(new ArrayList<>());
                accelVertical.add(new ArrayList<>());
                JSONObject segment = record.getJSONObject(String.format("segment_%d", i));

                JSONObject locations_json = segment.getJSONObject("locations");
                for(int loc_idx=0; loc_idx < locations_json.length(); ++loc_idx){
                    JSONArray entry = locations_json.getJSONArray(String.format("%d", loc_idx));
                    Location loc = new Location("");
                    loc.setLatitude(entry.getDouble(0));
                    loc.setLongitude(entry.getDouble(1));
                    locations.get(i).add(loc);
                }

                JSONObject verticalAccel_json = segment.getJSONObject("verticalAccel");
                for(int accel_idx=0; accel_idx < verticalAccel_json.length(); ++accel_idx){
                    JSONArray entry = verticalAccel_json.getJSONArray(String.format("%d", accel_idx));
                    accelVertical.get(i).add(entry.getDouble(0));
                    accelTimes.get(i).add(entry.getDouble(1));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}