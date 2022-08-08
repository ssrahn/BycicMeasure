package com.example.bycicmeasure;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        ArrayList<Double> accList = (ArrayList<Double>) getIntent().getSerializableExtra("AccelerationList");
        ArrayList<Double> accTList = (ArrayList<Double>) getIntent().getSerializableExtra("AccelerationTimesList");
        ArrayList<Location> locations = (ArrayList<Location>) getIntent().getSerializableExtra("LocationList");
        accTList.add(0, 0.0);

        double iri = approxIRI(accList, accTList, locations);

        TextView iriText = findViewById(R.id.iri_text);
        iriText.setText("IRI: " + iri);

        Log.d("IRI", iri + "");

        Button againButton = findViewById(R.id.again);
        againButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                Intent intent = new Intent(view.getContext(), MapActivity.class);
                startActivity(intent);
            }
        });
    }

    private double approxIRI(ArrayList<Double> acc, ArrayList<Double> times, ArrayList<Location> locations) {

        double iri = 0;
        for (int i=0; i < acc.size(); i++) {
            iri += 0.5 * Math.abs(acc.get(i)) * Math.pow(times.get(i+1) - times.get(i), 2);
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
}
