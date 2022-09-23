package com.example.bycicmeasure;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class RecordsActivity extends AppCompatActivity {

    SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_records);

        // Use this to store the calibration value permanently
        preferences = PreferenceManager.getDefaultSharedPreferences(this);


        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),"bycicmeasure" );
        File[] files = dir.listFiles();
        if (files != null) {
            ArrayList<String> records = new ArrayList<>();
            for (int i = 0; i < files.length; i++) {
                Log.d("Files", "FileName:" + files[i].getName());
                records.add(files[i].getName());
            }
            if (!records.isEmpty()) {
                records.sort(Collections.reverseOrder());
                ListView listview = (ListView) findViewById(R.id.listview_records);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, records);
                listview.setAdapter(adapter);

                listview.setOnItemClickListener((adapterView, view, i, l) -> {

                    Intent intent = new Intent(view.getContext(), ResultActivity.class);
                    intent.putExtra("filename", String.format("%s/%s", dir, records.get(i)));
                    startActivity(intent);
                });
            }
        }
    }
}