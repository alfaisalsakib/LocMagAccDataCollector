package com.health.envdatacollector;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener,SensorEventListener {

    private TextView sessionTimeTxt, speedTxt, latitudeTxt, longitudeTxt, accValueTxt, gyroTxt,magnetTxt;


    private static final String TAG = "MainActivity";
    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COURSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    private static final float DEFAULT_ZOOM = 16f;
    private Boolean mLocationPermissionsGranted = false;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private Handler mainHandler = new Handler();

    private GoogleMap mMap;
    private MapView mapView;
    private Location currentLocation;

    private ArrayList<LatLng> travelledLocations;

    // Sensors & SensorManager
    private Sensor mSensor;
    private Sensor gSensor;
    private Sensor aSensor;

    private SensorManager mSensorManager;
    private SensorManager aSensorManager;
    private SensorManager gSensorManager;

    // Storage for Sensor readings
    private float[] mGeomagnetic = null;
    private float[] accelarometer = null;
    private float[] gyroscope = null;

    private ArrayList<String> mList;
    private ArrayList<String> aList;
    private ArrayList<String> gList;
    private ArrayList<String> lList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionTimeTxt = findViewById(R.id.dateTimeLbl);
        speedTxt = findViewById(R.id.velocityTxt);
        latitudeTxt = findViewById(R.id.latitude);
        longitudeTxt = findViewById(R.id.longitude);
        accValueTxt = findViewById(R.id.accValue);
        gyroTxt = findViewById(R.id.gyroValue);
        magnetTxt = findViewById(R.id.magnetoValue);

        mList = new ArrayList<>();
        lList= new ArrayList<>();
        aList = new ArrayList<>();

        mapView = (MapView) findViewById(R.id.map);
        if (mapView != null) {
            mapView.onCreate(null);
            mapView.onResume();
            mapView.getMapAsync(this);
        }

        getLocationPermission();
        getDeviceLocation();

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);
        try {
            if (mLocationPermissionsGranted) {
                final Task loc = mFusedLocationProviderClient.getLastLocation();
                loc.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        try {
                            if (task.isSuccessful()) {
                                Location tempCur = (Location) task.getResult();
                                Location location = new Location(LocationManager.GPS_PROVIDER);
                                location.setLongitude(tempCur.getLongitude());
                                location.setLatitude(tempCur.getLatitude());

                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom
                                        (new LatLng(tempCur.getLatitude(), tempCur.getLongitude()),
                                                DEFAULT_ZOOM));

                                updateCurrLocation();
                            }
                        } catch (Exception ex) {
                            //Log.d("ErrorCatch", "inside catch");
                        }
                    }
                });
            }
        } catch (SecurityException e){}

        getCurrentTimeUsingDate();


        // Get a reference to the SensorManager
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        aSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        gSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Get a reference to the magnetometer
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gSensor = gSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        aSensor = aSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Exit unless sensor are available
        if (null == mSensor)
            Log.d(TAG, "mSensor");
        if (null == gSensor)
            Toast.makeText(MainActivity.this,"The Device has no Gyroscope",Toast.LENGTH_LONG).show();
        if (null == aSensor)
            Log.d(TAG, "aSensor");

    }

    public void getCurrentTimeUsingDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy , HH:mm:ss");
        String strDate = sdf.format(c.getTime());
        sessionTimeTxt.setText(strDate);
        Log.d("Date","DATE : " + strDate);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register for sensor updates

        mSensorManager.registerListener(this, mSensor,
                SensorManager.SENSOR_DELAY_NORMAL);

        gSensorManager.registerListener(this, gSensor,
                SensorManager.SENSOR_DELAY_NORMAL);

        aSensorManager.registerListener(this, aSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister all sensors
        mSensorManager.unregisterListener(this);
        aSensorManager.unregisterListener(this);
        gSensorManager.unregisterListener(this);

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        // Acquire magnetometer event data

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = new float[3];
            System.arraycopy(event.values, 0, mGeomagnetic, 0, 3);
        }
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            accelarometer = new float[3];
            System.arraycopy(event.values, 0, accelarometer, 0, 3);
        }
        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
            gyroscope = new float[3];
            System.arraycopy(event.values, 0, gyroscope, 0, 3);
        }

        if (mGeomagnetic != null) {
            //Log.d(TAG, "mx : "+mGeomagnetic[0]+" my : "+mGeomagnetic[1]+" mz : "+mGeomagnetic[2]);
            String magnet = "mx : "+mGeomagnetic[0]+" my : "+mGeomagnetic[1]+" mz : "+mGeomagnetic[2];

            String mtxt = mGeomagnetic[0]+"  "+mGeomagnetic[1]+"  "+mGeomagnetic[2];

            try{
                mList.add(mtxt);
            }catch (Exception e){

            }
            magnetTxt.setText(magnet);
        }
        if(accelarometer != null){
            //Log.d(TAG, "ax : "+accelarometer[0]+" ay : "+accelarometer[1]+" az : "+accelarometer[2]);
            String acc = "ax : "+accelarometer[0]+" ay : "+accelarometer[1]+" az : "+accelarometer[2];

            String atxt = accelarometer[0]+"  "+accelarometer[1]+"  "+accelarometer[2];
            try{
                aList.add(atxt);
            }catch (Exception e){

            }
            accValueTxt.setText(acc);
        }
        if(gyroscope != null){
            Log.d(TAG, "gx : "+gyroscope[0]+" gy : "+gyroscope[1]+" gz : "+gyroscope[2]);
            String gyro = "gx : "+gyroscope[0]+" gy : "+gyroscope[1]+" gz : "+gyroscope[2];

            String gtxt = gyroscope[0]+"  "+gyroscope[1]+"  "+gyroscope[2];
            try{
                gList.add(gtxt);
            }catch (Exception e){

            }
            gyroTxt.setText(gyro);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // N/A
    }

    public void updateCurrLocation() {

            getDeviceLocation();

            GoogleMap.OnMyLocationChangeListener myLocationChangeListener = new GoogleMap.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(Location location) {
                    try {
                        getDeviceLocation();

                        currentLocation = location;

                        latitudeTxt.setText("Latitude " + Double.toString(currentLocation.getLatitude()));
                        longitudeTxt.setText("Longitude " + Double.toString(currentLocation.getLongitude()));

                        lList.add(Double.toString(currentLocation.getLatitude())
                                + "  " + Double.toString(currentLocation.getLongitude()));

                    } catch (Exception ex) {
                    }
                }
            };
            mMap.setOnMyLocationChangeListener(myLocationChangeListener);
        }

        private void getDeviceLocation() {
           // Log.d(TAG, "getDeviceLocation: getting the devices current location");

            try {
                if (MainActivity.this != null) {
                    mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);

                    if (mLocationPermissionsGranted) {
                        final Task location = mFusedLocationProviderClient.getLastLocation();
                        location.addOnCompleteListener(new OnCompleteListener() {
                            @Override
                            public void onComplete(@NonNull Task task) {
                                try {
                                    if (task.isSuccessful()) {
                                        Location tempLoc = (Location) task.getResult();
                                        float accuracy = tempLoc.getAccuracy();
                                        //Log.d(TAG, "* Accuracy : " + Float.toString(accuracy));
                                        if (accuracy >= 0 && accuracy <= 40) {
                                            currentLocation = tempLoc;
//                                        latitudeTxt.setText(Double.toString(currentLocation.getLatitude()));
//                                        longitudeTxt.setText(Double.toString(currentLocation.getLongitude()));

                                            travelledLocations.add(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()));
                                        }
                                    } else {
                                        //Log.d(TAG, "onComplete: current location is null");
                                        Toast.makeText(MainActivity.this, "unable to get current location", Toast.LENGTH_SHORT).show();
                                    }
                                } catch (Exception ex) {
                                    Log.d(TAG, "Check LOcation");
                                }
                            }
                        });
                    }
                } else {

                }
            } catch (SecurityException e) {
                //Log.e(TAG, "getDeviceLocation: SecurityException: " + e.getMessage());
            }
        }

        private void getLocationPermission() {
            Log.d(TAG, "getLocationPermission: getting location permissions");
            String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION};

            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        COURSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionsGranted = true;
                    //initMap();
                } else {
                    ActivityCompat.requestPermissions((Activity) MainActivity.this,
                            permissions,
                            LOCATION_PERMISSION_REQUEST_CODE);
                }
            } else {
                ActivityCompat.requestPermissions((Activity) MainActivity.this,
                        permissions,
                        LOCATION_PERMISSION_REQUEST_CODE);
            }
        }

    private final String fileName = "note.txt";

    public void permission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // Do the file write
        } else {
            // Request permission from the user
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 0:
                // Re-attempt file write
        }
    }

        public void RefreshWindow(View view) {
            //updateCurrLocation();

            permission();

            File sdcard = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File dir = new File(sdcard.getAbsolutePath() + "/EnvFolder");

            File locationfile = new File(dir, "LocationCoord.txt");// for example "myData.txt"
            File accfile = new File(dir, "Accelarometer.txt");// for example "myData.txt"
            File magfile = new File(dir, "Magnetometer.txt");// for example "myData.txt"
            Log.d("directory", dir.getAbsolutePath());

            try {
                dir.mkdirs();
                FileOutputStream lf = new FileOutputStream(locationfile);
                PrintWriter pwl = new PrintWriter(lf);
                for(String s : lList){
                    pwl.println(s);
                }
                pwl.flush();
                pwl.close();
                lf.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                dir.mkdirs();
                FileOutputStream lf = new FileOutputStream(accfile);
                PrintWriter pwl = new PrintWriter(lf);
                for(String s : aList){
                    pwl.println(s);
                }
                pwl.flush();
                pwl.close();
                lf.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                dir.mkdirs();
                FileOutputStream lf = new FileOutputStream(magfile);
                PrintWriter pwl = new PrintWriter(lf);
                for(String s : mList){
                    pwl.println(s);
                }
                pwl.flush();
                pwl.close();
                lf.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Toast.makeText(MainActivity.this, "Data is Saved to " + dir.getAbsolutePath(), Toast.LENGTH_SHORT).show();

        }

        @Override
        public boolean onKeyDown(int KeyCode, KeyEvent event) {
            if ((KeyCode == KeyEvent.KEYCODE_BACK)) {

                MainActivity.this.finish();
                return true;
            }
            return super.onKeyDown(KeyCode, event);
        }


        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            //Toast.makeText(this, "Map is Ready", Toast.LENGTH_SHORT).show();
            //Log.d(TAG, "onMapReady: map is ready");
            MapsInitializer.initialize(MainActivity.this);
            mMap = googleMap;

            if (mLocationPermissionsGranted) {
                getDeviceLocation();

                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mMap.setTrafficEnabled(true);
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

            }

            updateCurrLocation();
        }


}