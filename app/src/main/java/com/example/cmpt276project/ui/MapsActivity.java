package com.example.cmpt276project.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.example.cmpt276project.R;
import com.example.cmpt276project.model.AllRestaurant;
import com.example.cmpt276project.model.Inspection;
import com.example.cmpt276project.model.Restaurant;
import com.example.cmpt276project.model.RestaurantManager;
import com.example.cmpt276project.model.DataDownloader;
import com.example.cmpt276project.model.DataUpdater;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;


import java.util.Objects;
import java.util.concurrent.Callable;
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
    private boolean mDownloadPermissionGranted;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private Location mLastKnownLocation;
    private final LatLng surrey = new LatLng(49.187500, -122.849000);
    private final int DEFAULT_ZOOM = 10;

    // Declare a variable for the cluster manager.
    private ClusterManager<AllRestaurant> mClusterManager;

    public static Intent makeIntent(Context context, boolean isUpdateNeeded) {
        Intent intent =  new Intent(context, MapsActivity.class);
        intent.putExtra("isUpdateNeeded", isUpdateNeeded);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPermissions();
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        manager = RestaurantManager.getInstance();

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        setupToolbar();
    }

    private void setPermissions() {
        int locationPermission = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);
        int downloadPermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        mLocationPermissionGranted = false;
        mDownloadPermissionGranted = false;

        if (locationPermission == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        }
        if (downloadPermission == PackageManager.PERMISSION_GRANTED) {
            mDownloadPermissionGranted = true;
        }
    }

    private void checkUpdateDialog() {
        Bundle extras = this.getIntent().getExtras();
        boolean isUpdateNeeded = extras.getBoolean("isUpdateNeeded");
        downloadDataResult = null;
        if (isUpdateNeeded) {
            launchUpdateDialog();
        } else {
            setUpCluster();
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

        if (!mLocationPermissionGranted) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            initializeMap();
        }
    }

    private void initializeMap() {
        updateLocationUI();
        checkUpdateDialog();
    }

    /**
    *   Reference Document: Google Maps Platform: https://developers.google.com/maps/documentation/javascript/adding-a-google-map
    */

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                mMap.getUiSettings().setZoomControlsEnabled(true);
                getDeviceLocation();
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(surrey, DEFAULT_ZOOM));
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
                        }
                    }
                });
            }
        } catch(Exception e)  {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(surrey, DEFAULT_ZOOM));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    mLocationPermissionGranted = true;
                }
                initializeMap();
                break;
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    mDownloadPermissionGranted = true;
                    startDownload();
                } else {
                    setUpCluster();
                }
                break;
        }
    }

    // Marker clustering
    public void setUpCluster() {
        // Initialize the manager with the context and the map.
        // (Activity extends context, so we can pass 'this' in the constructor.)
        mClusterManager = new ClusterManager<AllRestaurant>(this, mMap);

        // Point the map's listeners at the listeners implemented by the cluster manager
        mMap.setOnCameraIdleListener(mClusterManager);
        mMap.setOnMarkerClickListener(mClusterManager);

        // Add cluster items (markers) to the cluster manager.
        addRestaurants();
        mClusterManager.setRenderer(new MyClusterRenderer(getApplicationContext()));

        // custom info window
        clusterInfoWindow();
    }

    private void clusterInfoWindow() {
        mClusterManager.getMarkerCollection().setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                final View view = getLayoutInflater().inflate(R.layout.info_window, null);
                TextView nameView = view.findViewById(R.id.text_name);
                TextView detailsView = view.findViewById(R.id.text_detail);

                String name = (marker.getTitle() != null) ? marker.getTitle() : "Zoom in for Details";
                nameView.setText(name);
                String details = (marker.getSnippet() != null) ? marker.getSnippet() : "Zoom in for Details";
                detailsView.setText(details);

                return view;
            }
        });

        mClusterManager.setRenderer(new MyClusterRenderer(getApplicationContext()));

        mClusterManager.setOnClusterItemInfoWindowClickListener(new ClusterManager.OnClusterItemInfoWindowClickListener<AllRestaurant>() {
            @Override
            public void onClusterItemInfoWindowClick(AllRestaurant item) {
                Intent intent = RestaurantActivity.makeIntent(MapsActivity.this);
                intent.putExtra("tracking_number",item.getTrackingNumber());
                startActivity(intent);
            }
        });


    }

    public Bitmap resizeMapIcons (String iconName, int width, int height) {
        Bitmap imageBitmap  = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier(iconName, "drawable", getPackageName()));
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
        return resizedBitmap;
    }



    // add restaurant markers
    private void addRestaurants() {
        for (Restaurant tmp:manager.getRestaurantList()) {
            double lat = tmp.getLatitude();
            double lng = tmp.getLongitude();
            String title = tmp.getName();
            String address = tmp.getAddress();
            String trackingNum = tmp.getTrackingNumber();
            String snippet;
            String hazard;
            Inspection inspection = tmp.getInspectionByIndex(0);

            if(inspection.getTrackingNumber().equals("EMPTY")) {
                snippet = address + "\nHazard Level: No Inspection Yet";
                hazard = "hazard_unknown";
            } else {
                switch (inspection.getHazardRating()) {
                    case LOW:
                        snippet = address + "\nHazard Level: LOW";
                        hazard = "hazard_low";
                        break;
                    case MODERATE:
                        snippet = address + "\nHazard Level: MODERATE";
                        hazard = "hazard_mid";
                        break;
                    case HIGH:
                        snippet = address + "\nHazard Level: HIGH";
                        hazard = "hazard_high";
                        break;
                    default:
                        snippet = "Hazard Level: No Inspection Yet";
                        hazard = "hazard_unknown";
                        break;
                }
            }

            AllRestaurant offsetItem = new AllRestaurant(lat, lng, title, snippet, hazard, trackingNum);
            mClusterManager.addItem(offsetItem);
        }
    }

    private class MyClusterRenderer extends DefaultClusterRenderer<AllRestaurant> {
        public MyClusterRenderer(Context context) {
            super(context, mMap, mClusterManager);
        }
        @Override
        protected void onBeforeClusterItemRendered(@NonNull AllRestaurant item, @NonNull MarkerOptions markerOptions) {
            Bitmap resized = resizeMapIcons(item.getHazard(), 100, 100);
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(resized));
        }
    }

    private void launchUpdateDialog() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        UpdateDialog dialog = new UpdateDialog();
        dialog.show(fragmentManager, "TestDialog");
    }

    private void launchLoadingDataDialog() {
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
                            setUpCluster();
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
        if (mDownloadPermissionGranted) {
            startDownload();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    private void startDownload() {
        DataDownloader downloader = new CsvDataDownloader(this);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        launchProgressDialog();
        downloadDataResult = executor.submit(downloader);
    }

    private void launchProgressDialog() {
        progressBarDialog = new ProgressDialog(this);
        progressBarDialog.setTitle(getString(R.string.MapsActivity_download_progress));
        progressBarDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressBarDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                                    getString(R.string.MapsActivity_cancel_download_btn),
                                    new OnCancelDownloadListener(this));
        progressBarDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                                    getString(R.string.MapsActivity_accept_download_btn),
                                    new OnAcceptDownloadListener(this));
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

        private Context context;

        public OnAcceptDownloadListener(Context context) {
            this.context = context;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            try {
                boolean isDownloadSuccess = downloadDataResult.get();
                if (isDownloadSuccess) {
                    DataUpdater updater = new DataUpdater(context);
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

        private Context context;

        public OnCancelDownloadListener(Context context) {
            this.context = context;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            downloadDataResult.cancel(true);
            progressBarDialog.dismiss();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DataUpdater updater = new DataUpdater(context);
                    updater.deleteTempData();
                    setUpCluster();
                }
            });
        }
    }

    private class CsvDataDownloader extends DataDownloader {

        public CsvDataDownloader(Context context) {
            super(context);
        }

        public void updateProgress(final int progress) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBarDialog.setProgress(progress);
                }
            });
        }
    }
}