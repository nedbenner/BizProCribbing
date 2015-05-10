package ca.nbenner.bizprocribbing;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class LocnBinder extends Binder implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        GD.Constants  {

    //    <editor-fold desc="Variables and Constants"
    private         Context         c;
    public          GoogleApiClient mGoogleApiClient;

    private static  LocnListener    reportListener = null;
    private static  long            serviceEndTime;
    private static  long            interval;
    private static  long            fastInterval;
    private static  int             priority;
    public static   boolean         mResolvingError = false;    // Bool to track whether the app is already resolving an error
    private         NextLocn        nextLocn = new NextLocn();
    // </editor-fold>

    LocnBinder() {
        c = GD.getAppContext();
        buildGoogleApiClient();
        checkGooglePlay();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(c)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }
    private boolean checkGooglePlay() {	  						                // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(c);

        if (resultCode == ConnectionResult.SUCCESS) {							// If Google Play services is available
            Log.d("myDebug", "Google Play services is available.");	            // In debug mode, log the status
            if (!mGoogleApiClient.isConnecting() &&
                !mGoogleApiClient.isConnected()     ) {
                    mGoogleApiClient.connect();
            }
            return true;														// Continue
        } else {																// Google Play services was not available for some reason
            showErrorDialog( resultCode );
            return false;
        }
    }
    @Override public void onConnected(Bundle dataBundle) {					    // Called by GoogleApiClient when the request to connect the client finishes successfully.

        Toast.makeText(c, "Connected to Google Play Services", Toast.LENGTH_SHORT).show();
        Log.d("myDebug", "We have connection to GoogleApiClient!");	            // In debug mode, log the status

        setSettings();
    }
    @Override public void onConnectionSuspended(int cause) {                // GoogleApiClient
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Toast.makeText(c, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();   // Display the connection status
        Log.d("myDebug", "Somehow we have disconnected :(");	                            // In debug mode, log the status
    }
    @Override public void onConnectionFailed(ConnectionResult connectionResult) {		    // Called by GoogleApiClient connection fails.
    	/*
    	 * Google Play services can resolve some errors it detects.
    	 * If the error has a resolution, try sending an Intent to
    	 * start a Google Play services activity that can resolve
    	 * error.
    	 */
        Log.d("myDebug", "The connection has failed!");	                        // In debug mode, log the status
        if (connectionResult.hasResolution()) {
            try {																// Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult( ((Activity) c), CONNECTION_FAILURE_RESOLUTION_REQUEST);

            } catch (IntentSender.SendIntentException e) {						// Thrown if Google Play services canceled the original PendingIntent
                e.printStackTrace();											// Log the error
            }
        } else {
            Toast.makeText(c, connectionResult.getErrorCode(), Toast.LENGTH_SHORT).show();	// If no resolution is available, display a dialog to the user with the error.
            showErrorDialog(connectionResult.getErrorCode());
            mResolvingError = true;
        }
    }

    public void updateSettings(Intent intent) {
        boolean change = false;
        long intentValueL;
        int intentValueI;

        intentValueL = intent.getLongExtra(LOCN_REG_INTERVAL, (long) (15 * 60 * 1000));
        if (intentValueL != interval) {
            change = true;
            interval = intentValueL;
        }

        intentValueL = intent.getLongExtra(LOCN_FAST_INTERVAL, (long) (1 * 60 * 1000));
        if (intentValueL != fastInterval) {
            change = true;
            fastInterval = intentValueL;
        }

        intentValueI = intent.getIntExtra(LOCN_PRIORITY, LocationRequest.PRIORITY_HIGH_ACCURACY);
        if (intentValueI != priority) {
            change = true;
            priority = intentValueI;
        }

        serviceEndTime = intent.getLongExtra(LOCN_DURATION, (long) (60 * 60 * 1000)) + System.currentTimeMillis();

        if ( change && mGoogleApiClient.isConnected() )
            setSettings();
    }
    public void setSettings() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(priority);
        mLocationRequest.setFastestInterval(fastInterval);
        mLocationRequest.setInterval(interval);

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, nextLocn);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, nextLocn);

    }

    public void closeGoogleApiClient() {
        if (mGoogleApiClient != null)
            mGoogleApiClient.disconnect();

    }
    public void reportLocations(LocnListener listener) {
        reportListener = listener;
    }
    public void reportStopListening() {
        reportListener = null;
    }
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(((Activity) c).getFragmentManager(), "Connection Error Dialog");
    }

    public static class ErrorDialogFragment extends DialogFragment {

        public ErrorDialogFragment() { }

        @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode, this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override public void onDismiss(DialogInterface dialog) {
            mResolvingError = false;
        }
    }
    public class NextLocn implements LocationListener {
        @Override public void onLocationChanged(Location location) {
            Log.d("myDebug", "Got an answer!     Priority="      + priority     + ", " +
                    "Fast Interval=" + fastInterval + ", " +
                    "Interval="      + interval );
            GC.mCurrentLocation = location;
            new LocationList().Update(location);
            if (reportListener != null) {
                reportListener.showLocnMarkers();
            }

            if (System.currentTimeMillis() > serviceEndTime)
                c.stopService(new Intent(c, LocnService.class));
        }
    }


}
