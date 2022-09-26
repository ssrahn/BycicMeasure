package com.example.bycicmeasure;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set Night Mode Off
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_main);

        // Build dialog for location permission handling
        AlertDialog.Builder locDial = new AlertDialog.Builder(this);
        locDial.setMessage("This app needs the precise location of the device to track the distance and location on the map. Please grant the permission, otherwise it wont function.")
                .setTitle("Location Permission Denied");
        locDial.setPositiveButton("go to settings...", (dialog, id) -> {
            // This will open the settings menu of the app, so the user can change permissions there
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        locDial.setNegativeButton("cancel", (dialog, id) -> {});

        // Build dialog for google play service error handling
        AlertDialog.Builder playDial = new AlertDialog.Builder(this);
        playDial.setMessage("Failed to connect to google play sevice. Please check if it is installed and running and try again.")
                .setTitle("Google Play Service failed");
        playDial.setPositiveButton("i understand", (dialog, id) -> {
            // Kill the app
            finishAffinity();
        });

        // Register the permissions callback, which handles the user's response to the
        // system permissions dialog. Save the return value, an instance of
        // ActivityResultLauncher, as an instance variable.
        ActivityResultLauncher<String> requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        // Permission is granted, continue the workflow.
                        Intent intent = new Intent(this, MapActivity.class);
                        startActivity(intent);
                    } else {
                        // Explain to the user that the feature is unavailable because the
                        // features requires a permission that the user has denied.
                        locDial.show();
                    }
                });

        // Check google play status
        int googlePlayStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (googlePlayStatus == ConnectionResult.SUCCESS) {
            // GooglePlayServices available. Make start button available
            Button startButton = findViewById(R.id.start);
            startButton.setOnClickListener(view -> {
                // Check location permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, start map activity
                    Intent intent = new Intent(view.getContext(), MapActivity.class);
                    startActivity(intent);
                } else {
                    // Permission denied, try to ask for the permission
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                }
            });
        } else {
            // Failed to connect to google play service. Show dialog and kill the app
            playDial.show();
        }

        // Button to show all recordings
        Button recordButton = findViewById(R.id.records);
        recordButton.setOnClickListener(view -> {
            Intent intent = new Intent(view.getContext(), RecordsActivity.class);
            startActivity(intent);
        });

        // Button to show user guide
        Button helpButton = findViewById(R.id.help);
        helpButton.setOnClickListener(view -> {
            // Do something
            // TODO show help
        });
    }
}