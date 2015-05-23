package com.glennsayers.mapapp;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.StreetViewPanorama;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, GoogleMap.OnMapLongClickListener {

    /**
     * Member variables *
     */
    private static String TAG = "MainAtivity";
    GoogleMap m_googleMap;
    StreetViewPanorama m_StreetView;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLocationRequest;
    private boolean mRequestingLocationUpdates = false;
    private boolean mResolvingError = false;
    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;

    private EditText searchText = null;
    private Button locateButton = null;
    private Context context = null;
    private MarkerOptions markerOptions;
    public static final int zoom = 15;

    private double lat = 0;
    private double lng = 0;

    private static final double EARTH_RADIUS = 6378100.0;
    private int offset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        searchText = (EditText) findViewById(R.id.searchText);
        locateButton = (Button) findViewById(R.id.locateButton);
        locateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (searchText.getText().length() > 0) {
                    (new GetAddressFromSearchStringTask(context)).execute(searchText.getText().toString());
                }
            }
        });

        if (checkPlayServices()) {

            // Building the GoogleApi client
            buildGoogleApiClient();
        }

        createMapView();
        createStreetView();
        /*addMarker();
        zoomingToSpecificLocation();*/


        /**
         * Set up the onClickListener that will pass the selected lat/long
         * co-ordinates through to the Street View fragment for loading
         */
        m_googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {

                /**
                 * Ensure the street view has been initialise correctly and
                 * pass it through the selected lat/long co-ordinates.
                 */
                if (m_StreetView != null) {

                    /**
                     * Hide the map view to expose the street view.
                     */
                    Fragment mapView = getFragmentManager().findFragmentById(R.id.mapView);
                    getFragmentManager().beginTransaction().hide(mapView).commit();

                    /** Passed the tapped location through to the Street View **/
                    m_StreetView.setPosition(latLng);
                }
            }
        });
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Toast.makeText(getApplicationContext(),
                        "This device is not supported.", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
            return false;
        }
        return true;
    }

    private synchronized void buildGoogleApiClient() {
        Log.i(TAG, "buildGoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }


    /**
     * Initialises the street view member variable with the appropriate
     * fragment from the FragmentManager
     */
    private void createStreetView() {
        /*m_StreetView = ((StreetViewPanoramaFragment)
                getFragmentManager().findFragmentById(R.id.streetView))
                .getStreetViewPanorama();*/
    }

    /**
     * Initialises the mapview
     */
    private void createMapView() {
        /**
         * Catch the null pointer exception that
         * may be thrown when initialising the map
         */
        try {
            if (null == m_googleMap) {
                m_googleMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.mapView)).getMap();
                m_googleMap.setOnMapLongClickListener(this);

                /**
                 * If the map is still null after attempted initialisation,
                 * show an error to the user
                 */
                if (null == m_googleMap) {
                    Toast.makeText(getApplicationContext(),
                            "Error creating map", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (NullPointerException exception) {
            Log.e("mapApp", exception.toString());
        }
    }

    /**
     * Adds a marker to the map
     */
    private void addMarker() {

        /** Make sure that the map has been initialised **/
        if (null != m_googleMap) {
            markerOptions = new MarkerOptions()
                    .position(new LatLng(0, 0))
                    .title("Marker")
                    .draggable(true);
            m_googleMap.addMarker(markerOptions
            );
        }
    }

    private void zoomingToSpecificLocation() {
        if (null != m_googleMap) {
            CameraPosition cameraPosition = new CameraPosition.Builder().target(
                    new LatLng(0, 0)).zoom(zoom).build();

            m_googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }

    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "OnConnected");
        createLocationRequest();
        startLocationUpdates();


        if (mLastLocation != null) {
            updateUI();
        }
    }

    private void updateUI() {
        LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        lat = mLastLocation.getLatitude();
        lng = mLastLocation.getLongitude();
        //markerOptions = new MarkerOptions().markerOptions(latLng);
        if(markerOptions == null) {
            markerOptions = new MarkerOptions();
        }
        markerOptions.position(getCoords(lat, lng));
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(getBitmap()));
        markerOptions.snippet("Hi...");
        m_googleMap.addMarker(markerOptions);

        m_googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        m_googleMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));

        getAddress(mLastLocation);

    }

    private void updateUI(double latitude, double longitude) {
        lat = latitude;
        lng = longitude;
        LatLng latLng = new LatLng(latitude, longitude);

        if(markerOptions == null) {
            markerOptions = new MarkerOptions();
        }
        markerOptions.position(getCoords(lat, lng));
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(getBitmap()));

        m_googleMap.addMarker(markerOptions);

        //markerOptions = new MarkerOptions().markerOptions(latLng);
        //m_googleMap.addMarker(markerOptions);
        m_googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

        m_googleMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));
    }

    public void getAddress(Location location) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Geocoder.isPresent()) {
            (new GetAddressTask(this)).execute(location);
        }
    }

    public void getAddressBylatLng(LatLng latLng) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Geocoder.isPresent()) {
            (new GetAddressFromLatLngTask(this)).execute(latLng);
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        getAddressBylatLng(latLng);
        if(markerOptions == null) {
            markerOptions = new MarkerOptions();
        }
        markerOptions.position(getCoords(lat, lng));
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(getBitmap()));
        m_googleMap.addMarker(markerOptions.position(latLng));
        m_googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        m_googleMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));
    }

    private class GetAddressTask extends AsyncTask<Location, Void, String> {
        Context mContext;

        public GetAddressTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected String doInBackground(Location... params) {
            Geocoder geocoder =
                    new Geocoder(mContext, Locale.getDefault());
            // Get the current location from the input parameter list
            Location loc = params[0];
            // Create a list to contain the result address
            List<Address> addresses = null;
            try {
                /*
                 * Return 1 address.
                 */
                addresses = geocoder.getFromLocationName("452009", 3);
                /*addresses = geocoder.getFromLocation(loc.getLatitude(),
                        loc.getLongitude(), 1);*/
            } catch (IOException e1) {
                Log.e("LocationSampleActivity",
                        "IO Exception in getFromLocation()");
                e1.printStackTrace();
                return ("IO Exception trying to get address");
            } catch (IllegalArgumentException e2) {
                // Error message to post in the log
                String errorString = "Illegal arguments " +
                        Double.toString(loc.getLatitude()) +
                        " , " +
                        Double.toString(loc.getLongitude()) +
                        " passed to address service";
                Log.e("LocationSampleActivity", errorString);
                e2.printStackTrace();
                return errorString;
            }

            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {
                // Get the first address
                Address address = addresses.get(0);
                /*
                 * Format the first line of address (if available),
                 * city, and country name.
                 */
                String addressText = String.format(
                        "%s, %s, %s, %s",
                        // If there's a street address, add it
                        address.getMaxAddressLineIndex() > 0 ?
                                address.getAddressLine(0) : "",
                        // Locality is usually a city
                        address.getLocality(),
                        // The country of the address
                        address.getCountryName(),
                        address.getPostalCode());
                // Return the text
                return addressText;
            } else {
                return "No address found";
            }
        }

        @Override
        protected void onPostExecute(String address) {

            // Display the results of the lookup.
            System.out.println("Address :: " + address);
        }
    }

    private class GetAddressFromLatLngTask extends AsyncTask<LatLng, Void, String> {
        Context mContext;

        public GetAddressFromLatLngTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected String doInBackground(LatLng... params) {
            Geocoder geocoder =
                    new Geocoder(mContext, Locale.getDefault());
            // Get the current location from the input parameter list
            LatLng latlng = params[0];
            // Create a list to contain the result address
            List<Address> addresses = null;
            try {
                /*
                 * Return 1 address.
                 */
                addresses = geocoder.getFromLocation(latlng.latitude,
                        latlng.longitude, 1);
            } catch (IOException e1) {
                Log.e("LocationSampleActivity",
                        "IO Exception in getFromLocation()");
                e1.printStackTrace();
                return ("IO Exception trying to get address");
            } catch (IllegalArgumentException e2) {
                // Error message to post in the log
                String errorString = "Illegal arguments " +
                        Double.toString(latlng.latitude) +
                        " , " +
                        Double.toString(latlng.longitude) +
                        " passed to address service";
                Log.e("LocationSampleActivity", errorString);
                e2.printStackTrace();
                return errorString;
            }

            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {
                // Get the first address
                Address address = addresses.get(0);
                /*
                 * Format the first line of address (if available),
                 * city, and country name.
                 */
                String addressText = String.format(
                        "%s, %s, %s",
                        // If there's a street address, add it
                        address.getMaxAddressLineIndex() > 0 ?
                                address.getAddressLine(0) : "",
                        // Locality is usually a city
                        address.getLocality(),
                        // The country of the address
                        address.getCountryName());
                // Return the text
                return addressText;
            } else {
                return "No address found";
            }
        }

        @Override
        protected void onPostExecute(String address) {

            // Display the results of the lookup.
            System.out.println("Address :: " + address);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (connectionResult.hasResolution()) {
            try {
                mResolvingError = true;
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            mResolvingError = true;
        }
    }

    protected void startLocationUpdates() {
        mRequestingLocationUpdates = true;
        System.out.println("startLocationUpdates");
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    private class GetAddressFromSearchStringTask extends AsyncTask<String, Void, Address> {
        Context mContext;

        public GetAddressFromSearchStringTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Address doInBackground(String... params) {
            Geocoder geocoder =
                    new Geocoder(mContext, Locale.getDefault());
            // Get the current location from the input parameter list
            String searchTerm = params[0];
            // Create a list to contain the result address
            List<Address> addresses = null;
            try {
                /*
                 * Return 1 address.
                 */
                addresses = geocoder.getFromLocationName(searchTerm, 3);
            } catch (IOException e1) {
                Log.e("LocationSampleActivity",
                        "IO Exception in getFromLocation()");
                e1.printStackTrace();
                return null;
            } catch (IllegalArgumentException e2) {
                // Error message to post in the log
                String errorString = "Illegal arguments " +
                        searchTerm +
                        " passed to address service";
                Log.e("LocationSampleActivity", errorString);
                e2.printStackTrace();
                return null;
            }

            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {
                // Get the first address
                Address address = addresses.get(0);
                /*
                 * Format the first line of address (if available),
                 * city, and country name.
                 */


                return address;
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Address address) {

            if (address == null) {
                Log.i(TAG, "Address not found");
            } else {
                // Display the results of the lookup.
                System.out.println("Address :: " + address);
                String addressText = String.format(
                        "%s, %s, %s",
                        // If there's a street address, add it
                        address.getMaxAddressLineIndex() > 0 ?
                                address.getAddressLine(0) : "",
                        // Locality is usually a city
                        address.getLocality(),
                        // The country of the address
                        address.getCountryName());
                // Return the text
                updateUI(address.getLatitude(), address.getLongitude());
            }
        }
    }


    private int convertMetersToPixels(double lat, double lng, double radiusInMeters) {

        double lat1 = radiusInMeters / EARTH_RADIUS;
        double lng1 = radiusInMeters / (EARTH_RADIUS * Math.cos((Math.PI * lat / 180)));

        double lat2 = lat + lat1 * 180 / Math.PI;
        double lng2 = lng + lng1 * 180 / Math.PI;

        Point p1 = m_googleMap.getProjection().toScreenLocation(new LatLng(lat, lng));
        Point p2 = m_googleMap.getProjection().toScreenLocation(new LatLng(lat2, lng2));

        return Math.abs(p1.x - p2.x);
    }

    private Bitmap getBitmap() {

        // fill color
        Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint1.setColor(0x110000FF);
        paint1.setStyle(Paint.Style.FILL);

        // stroke color
        Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setColor(0xFF0000FF);
        paint2.setStyle(Paint.Style.STROKE);

        // icon

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.pointer);


        // circle radius - 200 meters
        int radius = offset = convertMetersToPixels(lat, lng, 500);

        // if zoom too small
        if (radius < icon.getWidth()) {

            radius = icon.getWidth();
        }

        // create empty bitmap
        Bitmap b = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        // draw blue area if area > icon size
        if (radius != icon.getWidth()) {

            c.drawCircle(radius, radius, radius, paint1);
            c.drawCircle(radius, radius, radius, paint2);
        }
        // draw icon
        c.drawBitmap(icon, radius - icon.getWidth(), radius - icon.getHeight(), new Paint());

        return b;
    }

    private LatLng getCoords(double lat, double lng) {

        LatLng latLng = new LatLng(lat, lng);

        Projection proj = m_googleMap.getProjection();
        Point p = proj.toScreenLocation(latLng);
        p.set(p.x, p.y + offset);

        return proj.fromScreenLocation(p);
    }

}
