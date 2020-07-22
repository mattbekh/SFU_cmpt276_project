package com.example.cmpt276project;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.example.cmpt276project.model.DataDownloader;
import com.example.cmpt276project.model.DataUpdater;
import com.example.cmpt276project.model.Restaurant;
import com.example.cmpt276project.model.RestaurantManager;
import com.example.cmpt276project.ui.LoadDataDialog;
import com.example.cmpt276project.ui.RestaurantListActivity;
import com.example.cmpt276project.ui.UpdateDialog;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.FileDescriptor;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MapsActivity extends AppCompatActivity
        implements OnMapReadyCallback, UpdateDialog.UpdateDialogListener, LoadDataDialog.OnDismissListener
{

    private RestaurantManager manager;

    private ProgressDialog progressBarDialog;
    private LoadDataDialog loadDataDialog;
    private Future<Boolean> downloadDataResult;
    private Future<Boolean> loadDataResult;

    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private boolean mLocationPermissionGranted;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    private Location mLastKnownLocation;
    private static LatLng surrey = new LatLng(49.187500, -122.849000);
    private static int DEFAULT_ZOOM = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        manager = RestaurantManager.getInstance();

        // Construct a GeoDataClient.
//        mGeoDataClient = Places.getGeoDataClient(this, null);

        // Construct a PlaceDetectionClient.
//        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null);

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        setupToolbar();
        checkUpdateDialog();
    }

    private void checkUpdateDialog() {
        Bundle extras = this.getIntent().getExtras();
        boolean isUpdateNeeded = extras.getBoolean("isUpdateNeeded");
        downloadDataResult = null;
        if (isUpdateNeeded) {
            launchUpdateDialog();
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
    }

    // Setup toolbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toolbar_menu, menu);

        MenuItem viewRestaurantListItem = menu.findItem(R.id.ToolbarMenu_switch_context);
        viewRestaurantListItem.setVisible(true);
        viewRestaurantListItem.setTitle(R.string.MapsActivity_toolbar_list_btn_text);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.ToolbarMenu_back:
                finish();
                return true;
            case R.id.ToolbarMenu_switch_context:
                startActivity(RestaurantListActivity.makeIntent(this));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // get permission
        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        // set all restaurant markers
        addRestaurantMarkers();

    }

    /**
    *   Reference Document: Google Maps Platform: https://developers.google.com/maps/documentation/javascript/adding-a-google-map
    */

    public void addRestaurantMarkers() {
        for (Restaurant tmp : manager.getRestaurantList()) {
            double lat = tmp.getLatitude();
            double lng = tmp.getLongitude();
            String title = tmp.getName();
            LatLng restPosition = new LatLng(lat, lng);
            mMap.addMarker(new MarkerOptions().position(restPosition).title(title));
        }
    }

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                mMap.getUiSettings().setZoomControlsEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                Task locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = (Location) task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                        } else {
                            Log.d("Get Current Location", "Current location is null. Using defaults.");
                            Log.e("Get Current Location", "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(surrey, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch(SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    public static Intent makeIntent(Context context, boolean isUpdateNeeded) {
        Intent intent =  new Intent(context, MapsActivity.class);
        intent.putExtra("isUpdateNeeded", isUpdateNeeded);
        return intent;
    }

    private void launchUpdateDialog() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        UpdateDialog dialog = new UpdateDialog();
        dialog.show(fragmentManager, "TestDialog");
    }

    private void launchLoadingDataDialog() {
//        manager.updateData();
//        addRestaurantMarkers();
        FragmentManager fragmentManager = getSupportFragmentManager();
        loadDataDialog = new LoadDataDialog();
        loadDataDialog.show(fragmentManager, "LoadDataDialog");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> dataLoader = new Callable<Boolean>() {
            @Override
            public Boolean call() {
                try {
                    manager.updateData();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addRestaurantMarkers();
                        }
                    });
                } catch (Exception e) {
                    return false;
                }
                return true;
            }
        };
        loadDataResult = executor.submit(dataLoader);
        executor.submit(new LoadDataWaiter());
    }

    @Override
    public void downloadData() {
        checkFilePermissions();
        try {
            if (filePermissionGranted()) {
                DataDownloader downloader = new CsvDataDownloader(this);
                ExecutorService executor = Executors.newFixedThreadPool(1);
                launchProgressDialog();
                downloadDataResult = executor.submit(downloader);
            };
        } catch (InterruptedException e) {
            // Thread was interrupted waiting for permission
        }
    }

    private void checkFilePermissions() {
        int permission = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        int requestCode = 1;
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, requestCode);
    }

    private boolean filePermissionGranted() throws InterruptedException {
        int permission = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        long timeBetweenChecks = 100; // milliseconds
        int maxChecks = 50; // total wait time of 5 seconds
        int numChecks = 0;

        // Check permission until granted, waiting between each check
        while (permission != PackageManager.PERMISSION_GRANTED
               && numChecks < maxChecks)
        {
            Thread.sleep(timeBetweenChecks);
            permission = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            numChecks++;
        }

        return permission == PackageManager.PERMISSION_GRANTED;
    }

    private void launchProgressDialog() {
        progressBarDialog = new ProgressDialog(this);
        progressBarDialog.setTitle(getString(R.string.MapsActivity_download_progress));
        progressBarDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressBarDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                                    getString(R.string.MapsActivity_cancel_download_btn),
                                    new OnCancelDownloadListener());
        progressBarDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                                    getString(R.string.MapsActivity_accept_download_btn),
                                    new OnAcceptDownloadListener());
        progressBarDialog.setProgress(0);
        progressBarDialog.show();
    }

    private class LoadDataWaiter implements Runnable {

        @Override
        public void run() {
            int timeBetweenChecks = 100; // milliseconds
            while (!loadDataResult.isDone()) {
                try {
                    Thread.sleep(timeBetweenChecks);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                loadDataResult.get();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                loadDataDialog.dismiss();
            }
        }
    }

    private class OnAcceptDownloadListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            try {
                boolean isDownloadSuccess = downloadDataResult.get();
                if (isDownloadSuccess) {
                    DataUpdater updater = new DataUpdater();
                    boolean isUpdateSuccess = updater.tryUpdateData();
                    if (isUpdateSuccess) {
                        launchLoadingDataDialog();
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                progressBarDialog.dismiss();
            }
        }
    }

    private class OnCancelDownloadListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            downloadDataResult.cancel(true);
            progressBarDialog.dismiss();
        }
    }

    private class CsvDataDownloader extends DataDownloader {

        public CsvDataDownloader(Context context) {
            super(context);
        }

        public void updateProgress(int progress) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBarDialog.setProgress(progress);
                }
            });
        }
    }
}