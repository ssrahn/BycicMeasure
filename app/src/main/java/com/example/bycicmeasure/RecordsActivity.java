package com.example.bycicmeasure;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class RecordsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_records);

        // This text will be visible, if no records are available
        TextView availText = findViewById(R.id.available);

        // Get records from the json file
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),"bycicmeasure" );
        File[] files = dir.listFiles();
        if (files != null) {
            ArrayList<String> records = new ArrayList<>();
            for (int i = 0; i < files.length; i++) {
                records.add(files[i].getName());
            }
            if (!records.isEmpty()) {
                records.sort(Collections.reverseOrder());
                ListView listview = findViewById(R.id.listview_records);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, records);
                listview.setAdapter(adapter);

                listview.setOnItemClickListener((adapterView, view, i, l) -> {
                    Intent intent = new Intent(view.getContext(), ResultActivity.class);
                    intent.putExtra("filename", String.format("%s/%s", dir, records.get(i)));
                    startActivity(intent);
                });
                availText.setVisibility(View.INVISIBLE);
            }
            else {
                availText.setVisibility(View.VISIBLE);
            }
        }
        else {
            availText.setVisibility(View.VISIBLE);
        }
    }
}