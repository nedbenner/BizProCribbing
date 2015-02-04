package ca.nbenner.bizprocribbing;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.CycleInterpolator;
import android.view.animation.Interpolator;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class TrackLocations extends Service {
    private static long serviceEndTime = 0;
    private static Bundle args = null;
    private static LocationBP locnFunctions;
    public static boolean mResolvingError = false;                 // Bool to track whether the app is already resolving an error

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            args            = intent.getExtras();
            long duration   = args.getLong("Duration");
            serviceEndTime  = System.currentTimeMillis() + duration;
        }

        locnFunctions = new LocationBP();

        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) {
        return null;
    }
    @Override public void onCreate() {

    }
    @Override public void onDestroy() {

    }


    class LocationBP implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        //    <editor-fold desc="Variables and Constants"
        private final static int  			CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
        public GoogleApiClient mGoogleApiClient;
        private Marker locnMarker = null;

        public static final int REQUEST_RESOLVE_ERROR = 1001;          // Request code to use when launching the resolution activity
        private static final String DIALOG_ERROR = "dialog_error";     // Unique tag for the error dialog fragment
        // </editor-fold>

        public LocationBP() {
            buildGoogleApiClient();
            servicesConnected();
        }
        protected synchronized void buildGoogleApiClient() {
            mGoogleApiClient = new GoogleApiClient.Builder(TrackLocations.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        //
        //    Connection routines and listeners
        //
        private boolean servicesConnected() {							    // Check that Google Play services is available

            int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(TrackLocations.this);

            if (resultCode == ConnectionResult.SUCCESS) {							// If Google Play services is available

                Log.d("Location Updates", "Google Play services is available.");	// In debug mode, log the status
                connectAPI();
                return true;														// Continue

            } else {																// Google Play services was not available for some reason

                showErrorDialog( resultCode );
                return false;
            }
        }
        public void connectAPI() {
            if (!mGoogleApiClient.isConnecting() &&
                    !mGoogleApiClient.isConnected()     ) {
                mGoogleApiClient.connect();
            }
        }
        public void updateLocation() {
            GC.mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (GC.myLocnMarker != null)
                GC.myLocnMarker.setPosition(new LatLng(GC.mCurrentLocation.getLatitude(), GC.mCurrentLocation.getLongitude()));
            else
                showLocationMarkers();
        }
        public void showLocationMarkers() {

            if (GC.myLocnMarker != null)
                GC.myLocnMarker.remove();

            GC.myLocnMarker = MainActivity.mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(GC.mCurrentLocation.getLatitude(), GC.mCurrentLocation.getLongitude()))
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.my_locn))
                    .draggable(false)
                    .alpha(1f));

            final Handler handler = new Handler();
            final long start = SystemClock.uptimeMillis();
            final long duration = 1500;

            final Interpolator interpolator = new CycleInterpolator(1);

            handler.post(new Runnable() {
                @Override
                public void run() {
                    long elapsed = SystemClock.uptimeMillis() - start;
                    float t = Math.max(1 - interpolator.getInterpolation((float) elapsed / duration), 0) * 0.8f + 0.2f;
                    GC.myLocnMarker.setAlpha(t);
                    handler.postDelayed(this, 16);

                }
            });

        }

        @Override public void onConnected(Bundle dataBundle) {								// Called by Location Services when the request to connect the client finishes successfully.

            long   fastInterval = args.getLong("FastInterval");
            long   interval     = args.getLong("Interval");
            int    priority     = args.getInt("Priority");

            Message msg = mServiceHandler.obtainMessage();
            msg.arg1 = GC.MSG_CONNECTED;
            mServiceHandler.sendMessage(msg);
            Log.d("Location Services", "We have connection!");	// In debug mode, log the status
            updateLocation();
            LocationRequest mLocationRequest = new LocationRequest();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setFastestInterval(1 * 15 * 1000);
            mLocationRequest.setInterval(1 * 15 * 1000);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest,
                    new com.google.android.gms.location.LocationListener() {
                        @Override public void onLocationChanged(Location location) {
                            Log.d("Location Services", "Got an answer!");
                            updateLocation();
                            new LocationFixes(c).Update( location );
                        }
                    });

        }
        @Override public void onConnectionSuspended(int cause) {
            // The connection has been interrupted.
            // Disable any UI components that depend on Google APIs
            // until onConnected() is called.
            Toast.makeText(c, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();  // Display the connection status
            Log.d("Location Services", "Somehow we have disconnected :(");	// In debug mode, log the status
        }
        @Override public void onConnectionFailed(ConnectionResult connectionResult) {			// Called by Location Services if the attempt to Location Services fails.
    	/*
    	 * Google Play services can resolve some errors it detects.
    	 * If the error has a resolution, try sending an Intent to
    	 * start a Google Play services activity that can resolve
    	 * error.
    	 */
            Log.d("Location Services", "The connection has failed!");	// In debug mode, log the status
            if (connectionResult.hasResolution()) {
                try {																// Start an Activity that tries to resolve the error
                    connectionResult.startResolutionForResult( a, CONNECTION_FAILURE_RESOLUTION_REQUEST);

                } catch (IntentSender.SendIntentException e) {						// Thrown if Google Play services canceled the original PendingIntent
                    e.printStackTrace();											// Log the error
                }
            } else {
                Toast.makeText(c, connectionResult.getErrorCode(), Toast.LENGTH_SHORT).show();	// If no resolution is available, display a dialog to the user with the error.
                showErrorDialog(connectionResult.getErrorCode());
                mResolvingError = true;
            }
        }

        private static void showErrorDialog(int errorCode) {
            // Create a fragment for the error dialog
            ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
            // Pass the error that should be displayed
            Bundle args = new Bundle();
            args.putInt(DIALOG_ERROR, errorCode);
            dialogFragment.setArguments(args);
            dialogFragment.show(a.getFragmentManager(), "Connection Error Dialog");
        }

        public static class ErrorDialogFragment extends DialogFragment {

            public ErrorDialogFragment() { }

            @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
                // Get the error code and retrieve the appropriate dialog
                int errorCode = this.getArguments().getInt(DIALOG_ERROR);
                return GooglePlayServicesUtil.getErrorDialog(errorCode, a, REQUEST_RESOLVE_ERROR);
            }

            @Override public void onDismiss(DialogInterface dialog) {
                mResolvingError = false;
            }
        }

    }

}
