package ca.nbenner.bizprocribbing;

import android.app.ActivityManager;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.CycleInterpolator;
import android.view.animation.Interpolator;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;

//import com.google.android.gms.location.LocationClient;


public class ActivityMain extends FragmentActivity implements
        GD.Constants,
        OnMapReadyCallback,
        GoogleMap.OnCameraChangeListener,
	    OnMarkerClickListener,
        OnMarkerDragListener,
        OnMapClickListener,
        SeekBar.OnSeekBarChangeListener,
        LocnListener,                               // location update interface for Location Tracking Service (LocnService)
        ServiceConnection                           // callback interface for Location Tracking Service (LocnService)
    {

// <editor-fold desc="Global Variables and Constants"
    public static ActivityMain ma;
	public static 	GoogleMap 			mMap 				= null;
	public static 	ArrayList<Project>  allProjects 		= new ArrayList<Project>();
    public static   int                 maxNumOfProjects    = 3;
    private static  CameraPosition      currentView         = null;
    private static  LocnBinder          locnBinder          = null;
    private final   Handler             handler             = new Handler();
    private static  int                 cameraMoveCnt       = 0;        // Counter to see if second mapClick zooms on projects or locations
    public static   GC                  mGC;

    public static   long                maxTime             = (long) 10 * 365 * 24 * 60;        // in "minutes"
    public static   long                minTime             =                        10;        // in "minutes"
    public static   double              increment           = Math.log( maxTime/minTime );      // in "minutes"

	private double						earthRadius 		= 6371;
    private float                       TightZoom           = (float) (Math.log(
                                                              2 * Math.PI * earthRadius         // earth circumference at equator
                                                              * Math.cos( 51 / 180 * Math.PI )  // adjust to Calgary's latitude
                                                              / 400f                            // Region to view (in meters) when selected
                                                              * 480f                            // Screen size in dp (about 3 inches)
                                                              / 256f ) / Math.log(2f) ) + 7;    // Finishes conversion to zoom level.  Fudged need todo fix this formula!!!
	private 		boolean				clickAddProject		= false;	// Add Project or Manipulate Map on map click
    private         TextView            textMessage         = null;     // Text box to display messages
	private 		boolean				clickShowTimeline	= false;	// Show Timeline seekbar
	private 		boolean				justGettingPID      = false;
    public          Intent              startTrackingIntent;
    public          String              selectedMarker;
    private         String              STATE_CURRENT_VIEW  = "currentView";
	private			int[]				mapTypes			= {GoogleMap.MAP_TYPE_NORMAL,
															   GoogleMap.MAP_TYPE_SATELLITE,
															   GoogleMap.MAP_TYPE_HYBRID, 
															   GoogleMap.MAP_TYPE_TERRAIN};

// </editor-fold>

//
//    Activity life cycle routines.
//
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        Log.d("myDebug", "starting app " + System.currentTimeMillis());

		setContentView(R.layout.activity_main);
        textMessage = (TextView) findViewById(R.id.textMessage);

        mGC = new GC(this);
        allProjects = new ProjectList(this).readProjectsFromDB();
        new UpdateDatabases(this).downloadData();

        startTrackingIntent = LocationList.SetLocnPeriod(FAST);

	}
    @Override protected void onResume() {
        super.onResume();

        // Binds the location service to this instance.  Once it is unbound, the service will persist for a length of time
        // defined in Locations (some day I will pull this out properly...)
        bindService(startTrackingIntent, this, 0);

        // Connects the View with the actual map (via a callback OnMapReadyCallback)
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.startscreen);
        mapFragment.getMapAsync(this);

        // Depending on how this instance is accessed will limit what can be done
        //      -> root level is the normal mode where all functions are available
        //      -> when accessed from the EditProject mode only selecting a project is available
        String theAction = getIntent().getAction();
        if ( theAction != null && theAction.equals(Intent.ACTION_MAIN) )
            justGettingPID = false;                             // root level
        else
            justGettingPID = true;                              // another instance, limit actions that can be taken

    }
    @Override protected void onStop() {
        super.onStop();
        if (locnBinder != null)
            locnBinder.reportStopListening();
        unbindService(this);            // The service will stop after some time (like a day) unless something re-binds to it.
        mGC.putInternalData();
    }
    @Override public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (mMap != null)
            savedInstanceState.putParcelable(STATE_CURRENT_VIEW, mMap.getCameraPosition());
    }
    @Override public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState( savedInstanceState );
        if (savedInstanceState != null)
            currentView = savedInstanceState.getParcelable( STATE_CURRENT_VIEW );

    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toggle, menu);
        return true;
    }
    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.view_button).setIcon(GC.mapType == 1  ? R.drawable.view_road   : R.drawable.view_satellite);
        menu.findItem(R.id.add_button ).setIcon(clickAddProject  ? R.drawable.add_project : R.drawable.not_add_project);

        return super.onPrepareOptionsMenu(menu);
    }
    @Override public void onMapReady(GoogleMap map) {
        mMap    = map;
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerDragListener(this);
        mMap.setOnCameraChangeListener(this);

        showCurrentLocn();
        if (ActivityMain.allProjects != null) {
            Iterator itr = ActivityMain.allProjects.iterator();
            while (itr.hasNext()) {
                Project p = (Project) itr.next();
                if (p.getStatus().equals("Deleted")) {
                    p.removeMarker();
                    itr.remove();
                } else if (p.getMarker() == null) {
                    p.setMarker( !justGettingPID );
                    p.getMarker().setVisible(p.getTimestamp() > GC.displayRecordsSince);
                }
            }
        }

        final View mapView = ( getFragmentManager().findFragmentById(R.id.startscreen)).getView();
        if (mapView.getViewTreeObserver().isAlive()) {
            mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (currentView == null)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(GC.bounds, 50));
                    else
                        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(currentView));

                    mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // This is called by the resolution method for failures to connect to Google Play
        // That is, if we fail to connect to Google Play and try to resolve.  Upon a successful
        // resolution we can try again (which is where this code comes in...).  BTW, no longer in LocnBP, but LocnBinder

//        if (requestCode == LocnBP.REQUEST_RESOLVE_ERROR) {
//            LocnBP.mResolvingError = false;
//            if (resultCode == Activity.RESULT_OK) {
//                // Make sure the app is not already connected or attempting to connect
//                if (!LocnBP.mGoogleApiClient.isConnecting() &&
//                        !LocnBP.mGoogleApiClient.isConnected()) {
//                    LocnBP.mGoogleApiClient.connect();
//                }
//            }
//        }
    }

    public void onToggleRoads(MenuItem item) {
        GC.mapType 	= (GC.mapType + 1) % 4;
    	mMap.setMapType( mapTypes[ GC.mapType ] );
        invalidateOptionsMenu();
    }
    public void onTrack(MenuItem item) {
        GC.isLocationTracking = !GC.isLocationTracking;
        findViewById(R.id.locationButtons).setVisibility(GC.isLocationTracking ? View.VISIBLE : View.INVISIBLE);
        Locations.display(GC.isLocationTracking);

        if (locnBinder != null)
            if ( GC.isLocationTracking )
                locnBinder.reportStopListening();
            else {
                showCurrentLocn();
                locnBinder.reportLocations(this);
            }

    }
    public void pressedLocationButtons(View v) {
        if (v.getId() == R.id.earlierButton)
            Locations.changeDay(-1, v);
        if (v.getId() == R.id.displayedDateButton)
            Locations.changeDay( 0, v);                         // i.e. Today
        if (v.getId() == R.id.laterButton)
            Locations.changeDay(+1, v);
        if (v.getId() == R.id.doneButton)
            onTrack(null);                                  // toggles displayed positions off
    }
    public void onToggleAdd(MenuItem item) {
        if ( justGettingPID ) {
            clickAddProject = false;
        } else {
            clickAddProject = !clickAddProject;
            if (clickAddProject) {
                Toast.makeText(this, "Click on map to add a new project", Toast.LENGTH_SHORT).show();
            }
            invalidateOptionsMenu();
        }
    }
    public void onToggleTimeline(MenuItem item) {
        clickShowTimeline = !clickShowTimeline;
        SeekBar  viewSeek = (SeekBar)  findViewById(R.id.timeLine);
        long howLong = ( System.currentTimeMillis() - GC.displayRecordsSince ) / 1000 / 60;
        int progress = (int) (Math.log( howLong/minTime ) / increment * 100 + 0.5);
        viewSeek.setProgress( progress );

    	if (clickShowTimeline) {
    		Toast.makeText(this, "Adjust slider to show projects modified in last time period", Toast.LENGTH_SHORT).show();
            viewSeek.setVisibility(View.VISIBLE);
            textMessage.setVisibility(View.VISIBLE);
            viewSeek.setOnSeekBarChangeListener(this);
            onProgressChanged( viewSeek, progress, false );
    	} else {
            viewSeek.setVisibility(View.INVISIBLE);
            textMessage.setVisibility(View.INVISIBLE);
            viewSeek.setOnSeekBarChangeListener(null);
        }

    	invalidateOptionsMenu();
    }
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        long howLong = (long) Math.exp(progress/100f * increment + Math.log(minTime));
        CharSequence                      message = "all records";
        if      (howLong < 60       *1.5) message = String.format("  about %.0f minutes  ", howLong/1f);
        else if (howLong < 60*24    *1.5) message = String.format("  about %.0f hours  ",   howLong/60f);
        else if (howLong < 60*24*30 *1.5) message = String.format("  about %.0f days  ",    howLong/60/24f);
        else if (howLong < 60*24*365*1.5) message = String.format("  about %.0f months  ",  howLong/60/24/30f);
        else if (progress < 100)          message = String.format("  about %.0f years  ",   howLong/60/24/365f);

        textMessage.setText( message );
        if (progress == 100)
            GC.displayRecordsSince = 0;
        else
            GC.displayRecordsSince = System.currentTimeMillis() - howLong * 60 * 1000;

        for (Project p : allProjects ) {
            if (p.getTimestamp() <  GC.displayRecordsSince &&  p.getMarker().isVisible())
                p.getMarker().setVisible(false);
            if (p.getTimestamp() >= GC.displayRecordsSince && !p.getMarker().isVisible())
                p.getMarker().setVisible(true);
        }
    }
    public void onStartTrackingTouch(SeekBar seekBar) {

    }
    public void onStopTrackingTouch(SeekBar seekBar) {
        mGC.putInternalData();
    }
    public void onPreferences(MenuItem item) {
        startActivity(new Intent(this, Settings.class));
    }
    public void onCopyDB(MenuItem item) {
        Log.i("myDebug", "in copy data base at finally");
        try {
            File sd = Environment.getExternalStorageDirectory();
            File data = Environment.getDataDirectory();
            if (sd.canWrite()) {
                String currentDBPath = "/data/" + this.getPackageName() + "/databases/db.cribbing";
                String backupDBPath = "Download/db_bp_backup.sqlite";
                File currentDB = new File(data, currentDBPath);
                File backupDB = new File(sd, backupDBPath);
                if (currentDB.exists()) {
                    FileChannel src = new FileInputStream(currentDB)
                            .getChannel();
                    FileChannel dst = new FileOutputStream(backupDB)
                            .getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();
                }
            }
        } catch (Exception e) {
            Log.i("myDebug", "error when copying database");

        }
    }
    public void onSyncDB(MenuItem item) {
        Log.i("myDebug", "in sync data base");
        try {
            // Clear out database
                                                            // GC.UsedList -- this we can keep
            for (Project p : allProjects)                   // clear out project data
                p.removeMarker();                           //     1. remove markers first
            allProjects = new ArrayList<Project>();         //     2. empty list in memory
            new ProjectList(this).deleteAllData();          //     3. clean DB second
            new TimeSheetList().deleteAllData();            // clear out timesheet
            new ABRecordList().deleteAllData();             // clear out asbuilt records
            GC.localABTestsVersion = 0;                     // this will clear out asbuilt test descriptions

            // Now get all the data from the server database
            new UpdateDatabases(this).downloadData();

        } catch (Exception e) {
            Log.i("myDebug", "error when syncing database");

        }
    }
    public void onDeleteLocations(MenuItem item) {
        Log.i("myDebug", "in on DeleteLocations");
        DialogFragment newFragment = new DeleteLocnFromDB();
        newFragment.show(getFragmentManager(), "datePicker");
    }
    public void onListProjects(MenuItem item) {
        startActivity( new Intent(this, ListProject.class) );
    }
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Location tracking service has now commenced.  No need to do anything.
        // The ComponentName is a concrete component name of the service - so we could
        // access it directly if we wanted to.  Use this code...
        //    mBoundService = ((LocalService.LocalBinder)service).getService();
        // where the casting of "service" is with the method that returns the class
        // (see http://developer.android.com/reference/android/app/Service.html)
        locnBinder = (LocnBinder) service;
        locnBinder.reportLocations(this);
    }
    public void onServiceDisconnected(ComponentName name) {
        // Location tracking has stopped.  It will be restored when the service has restarted.
        // I don't think this should ever happen since the Service and this application are running in
        // the same thread.
        locnBinder = null;
    }
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
//
//        Setup Application
//
	public static void seeWholeMap() {

        LatLngBounds.Builder setOfBounds = new LatLngBounds.Builder();

        if (GC.isLocationTracking && cameraMoveCnt > 0) {       // cameraMoveCnt is used to add extra "zoom level on tap" for
            cameraMoveCnt = 0;                                  //     zooming into the location data only
            setOfBounds = Locations.locnBounds;
        } else {
            cameraMoveCnt = 1;
            if (GC.mCurrentLocation != null) {
                setOfBounds.include(new LatLng(GC.mCurrentLocation.getLatitude(),
                        GC.mCurrentLocation.getLongitude()));
            }

            for (Project mProject : allProjects) {
                if (mProject.getMarker().isVisible())
                    setOfBounds.include(new LatLng(mProject.getLatitude(), mProject.getLongitude()));
            }
        }
        GC.bounds  = setOfBounds.build();

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(GC.bounds, 75), new GoogleMap.CancelableCallback() {
            @Override public void onFinish() {
                if (GC.isLocationTracking)
                    cameraMoveCnt--;
            }

            @Override public void onCancel() { }
        });

	}
    @Override public void onCameraChange(CameraPosition position) {
        cameraMoveCnt++;
    }

//
//    Marker related listeners.
//
    @Override public boolean onMarkerClick(final Marker marker) {
        double markerLon = marker.getPosition().longitude;
        double markerLat = marker.getPosition().latitude;
        int numTooClose = 0;
        double earthCircum = earthRadius * 2 * Math.PI * Math.cos(markerLat / 180 * Math.PI);

        // Test to see if any other projects are so close that it is hard to click on the right one
        double minDistance =
                50                                                                //    Max resolution of click (units = dp)
                        * earthCircum                                                        //  * earth circumference at this Latitude (units = km)
                        / (256 * Math.pow(2, ActivityMain.mMap.getCameraPosition().zoom)); //  / size of earth (units = dp)

        if (clickAddProject) {                      // if we are in Add Project state, skip clicking on Markers
            onMapClick( marker.getPosition() );
            return true;
        }

        LatLngBounds.Builder setOfBounds = new LatLngBounds.Builder();
        for (Project mProject : allProjects) {

            // Approximate Equirectangular -- works if (lat1,lon1) ~ (lat2,lon2)
            double x = (mProject.getLongitude() - markerLon) * Math.cos(markerLat / 180 * Math.PI);
            double y = (mProject.getLatitude() - markerLat);
            double distance = Math.sqrt(x * x + y * y) * Math.PI / 180 * earthRadius;

            if (distance < minDistance) {
                numTooClose++;
                setOfBounds.include(new LatLng(mProject.getLatitude(), mProject.getLongitude()));
                mProject.showWindow();
            }

        }

        if (GC.mCurrentLocation != null) {
            Location testLocn = GC.mCurrentLocation;

            // Approximate Equirectangular -- works if (lat1,lon1) ~ (lat2,lon2)
            double x = (testLocn.getLongitude() - markerLon) * Math.cos(markerLat / 180 * Math.PI);
            double y = (testLocn.getLatitude() - markerLat);
            double distance = Math.sqrt(x * x + y * y) * Math.PI / 180 * earthRadius;

            if (distance < minDistance) {
                numTooClose++;
                setOfBounds.include(new LatLng(testLocn.getLatitude(), testLocn.getLongitude()));
            }
        }

        if (numTooClose > 1) {                                      // If more than one then zoom in and try again
            selectedMarker = marker.getId();
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(setOfBounds.build(), 100));

        } else if (!marker.getId().equals(selectedMarker)) {        // First time click just shows info window
            marker.showInfoWindow();
            selectedMarker = marker.getId();

        } else {                                                    // If just one (i.e. our project) then shift and display dialog

            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(markerLat, markerLon), TightZoom), 1000, null);
            selectedMarker = null;

            String markerID = marker.getId();
            for (int i = 0; i < allProjects.size(); i++) {
                GD.projectToEdit = allProjects.get(i).copy();
                if (markerID.compareTo(allProjects.get(i).getMarker().getId()) == 0) {
                    callActivityEditProject(i, GD.codeIntent.EDIT_RQ_CURRENT);
                    break;
                }
            }
        }

    	return true;
    }
    @Override public void onMarkerDrag(Marker marker){
    }
    @Override public void onMarkerDragStart(Marker marker){
    }
    @Override public void onMarkerDragEnd(Marker marker){
    	String 		markerID 		= marker.getId();
    	for (int i = 0; i < allProjects.size(); i++ ) {
    		if ( markerID.compareTo(allProjects.get(i).getMarker().getId()) == 0 ) {
                GD.projectToEdit = allProjects.get(i);
                GD.projectToEdit.setLatitude(  marker.getPosition().latitude  );
                GD.projectToEdit.setLongitude( marker.getPosition().longitude );
    			callActivityEditProject(i, GD.codeIntent.EDIT_RQ_SHIFTED);
    			break;
    		}
    	}
    }
	@Override public void onMapClick(LatLng point) {
		if (clickAddProject) {
			onToggleAdd(null);
            GD.projectToEdit = new Project(0, null, -1, point.latitude, point.longitude, null, "Planned");
			callActivityEditProject(-1, GD.codeIntent.EDIT_RQ_NEW);
		} else {
			seeWholeMap();
		}
	}
    public void callActivityEditProject(int projectIndex, GD.codeIntent requestCode) {
        try {
            GC.mUsedList.Update(allProjects.get(projectIndex).getId(), false);
        } catch (ArrayIndexOutOfBoundsException e) {}  // no problem, just don't add to UsedList
        if (justGettingPID)
            finish();
        else {
            Intent intent = new Intent(this, ActivityEditProject.class);
            intent.putExtra("Request", requestCode.ii);
            startActivity(intent);
        }
    }
    public void showCurrentLocn() {
        final SimpleDateFormat sdf      = new SimpleDateFormat("yyyy-MM-dd h:mm a");

        if (GC.mCurrentLocation == null)
            return;

        if (GC.myLocnMarker != null)
            GC.myLocnMarker.remove();

        GC.myLocnMarker = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(GC.mCurrentLocation.getLatitude(), GC.mCurrentLocation.getLongitude()))
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.stationary))
                .draggable(false)
                .title(sdf.format(GC.mCurrentLocation.getTime()))
                .alpha(1f));
        GC.myLocnMarker.setAnchor(0.5f, 0.5f);


        final long start = SystemClock.uptimeMillis();
        final long duration = 1500;

        final Interpolator interpolator = new CycleInterpolator(1);

        handler.removeCallbacksAndMessages(null);
        handler.post(new Runnable() {
            @Override public void run() {
                if ( GC.isLocationTracking ) {
                    GC.myLocnMarker.remove();
                } else {
                    long elapsed = SystemClock.uptimeMillis() - start;
                    float t = Math.max(1 - interpolator.getInterpolation((float) elapsed / duration), 0) * 0.8f + 0.2f;
                    GC.myLocnMarker.setAlpha(t);
                    handler.postDelayed(this, 50);
                }
            }
        });

    }
}
