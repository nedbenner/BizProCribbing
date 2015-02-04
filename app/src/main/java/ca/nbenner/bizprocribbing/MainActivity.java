package ca.nbenner.bizprocribbing;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
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
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;

//import com.google.android.gms.location.LocationClient;


public class MainActivity extends FragmentActivity implements
        OnMapReadyCallback,
	    OnMarkerClickListener,
        OnMarkerDragListener,
        OnMapClickListener,
        SeekBar.OnSeekBarChangeListener  {

// <editor-fold desc="Global Variables and Constants"
	public static 	GoogleMap 			mMap 				= null;
	public static 	Projects            projectToEdit       = null;
	public static 	ArrayList<Projects> allProjects 		= new ArrayList<Projects>();
	public static 	ArrayList<TimeSheet> myTime 		    = new ArrayList<TimeSheet>();
    private static final String         STATE_RESOLVING_ERROR = "resolving_error";
    private final static int  			CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    public static   int                 maxNumOfProjects    = 3;
    private         LocnBP              locnFunctions;
    private static CameraPosition       currentView         = null;

    public static   long                maxTime             = (long) 10 * 365 * 24 * 60;       // in "minutes"
    public static   long                minTime             =                        10;       // in "minutes"
    public static   double              increment           = Math.log( maxTime/minTime ); // in "minutes"
	private double						earthRadius 		= 6371;
    private float                       TightZoom           = (float) (Math.log(
                                                              2 * Math.PI * earthRadius  // earth circumference at equator
                                                              * Math.cos( 51 / 180 * Math.PI ) // adjust to Calgary's latitude
                                                              / 400f                     // Region to view (in meters) when selected
                                                              * 480f                     // Screen size in dp (about 3 inches)
                                                              / 256f ) / Math.log(2f) ) + 7; // Finishes conversion to zoom level.  Fudged need todo fix this formula!!!
	private			int					displayMapType		= 1;		// Roads or Satellite view
	private 		boolean				clickAddProject		= false;	// Add Project or Manipulate Map on map click
    private         TextView            textMessage         = null;     // Text box to display messages
	private 		boolean				clickShowTimeline	= false;	// Show Timeline seekbar
    public static   GC                  mGC;
    private         long                starttime           = 0;
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

		setContentView(R.layout.activity_main);
        Log.d("Timing", "starting app " + System.currentTimeMillis());

        textMessage = (TextView) findViewById(R.id.textMessage);

        mGC = new GC(this);
        allProjects = new ProjectList(this).readProjectsFromDB();
        myTime = new TimeSheetList(this).readTimeSheetsFromDB();
        new UpdateDatabases(this).downloadData();

        Intent startTracking = new Intent(this, TrackLocations.class);
        startTracking.putExtra("FastInterval", 1  * 60 * 1000);
        startTracking.putExtra("Interval",     15 * 60 * 1000);
        startTracking.putExtra("Duration",     3*24*60 * 60 * 1000);
        startTracking.putExtra("Priority",     100 ); //  High Accuracy = 100, Balanced = 102
        startService(startTracking);
//        locnFunctions = new LocnBP(this);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.startscreen);
        mapFragment.getMapAsync(this);

        new UpdateDatabases(this).callRemoteServer("Cribbing/project_and_time_download.php?");
        if (savedInstanceState != null) {
            currentView = savedInstanceState.getParcelable("currentView");
            LocnBP.mResolvingError = savedInstanceState.getBoolean("stateResolvingError");
        }

	}
	@Override protected void onStart() {
        super.onStart();
        if (!LocnBP.mResolvingError) {
            locnFunctions.mGoogleApiClient.connect();
        }
    }
    @Override protected void onResume() {
        super.onResume();
    }
    @Override protected void onStop() {
        Intent locationUpdatesIntent = new Intent(this, LocationUpdatesIntentService.class);
        PendingIntent pendingInent = PendingIntent.getService(this, 0, locationUpdatesIntent, PendingIntent.FLAG_NO_CREATE);
        boolean isLocationUpdatesEnabled = (pendingIntent != null);
        locnFunctions.mGoogleApiClient.disconnect();
        super.onStop();
    }
    @Override public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (mMap != null) {
            CameraPosition currentView = mMap.getCameraPosition();
            savedInstanceState.putParcelable("currentView", currentView);
            savedInstanceState.putBoolean("stateResolvingError", LocnBP.mResolvingError);
        }
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toggle, menu);
        return true;
    }
    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.view_button).setIcon(displayMapType == 1  ? R.drawable.view_road   : R.drawable.view_satellite);
        menu.findItem(R.id.add_button ).setIcon(clickAddProject      ? R.drawable.add_project : R.drawable.not_add_project);

        return super.onPrepareOptionsMenu(menu);
    }
    @Override public void onMapReady(GoogleMap map) {
        mMap    = map;
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerDragListener(this);

        if (MainActivity.allProjects != null) {
            Iterator itr = MainActivity.allProjects.iterator();
            while (itr.hasNext()) {
                Projects p = (Projects) itr.next();
                if (p.getStatus().equals("Deleted")) {
                    p.removeMarker();
                    itr.remove();
                } else if (p.getMarker() == null) {
                    p.setMarker();
                    p.getMarker().setVisible(p.getTimestamp() > GC.displayRecordsSince);
                }
            }
        }

        if (GC.mCurrentLocation != null) {
            LocnBP.showLocationMarkers();
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
        if (requestCode == LocnBP.REQUEST_RESOLVE_ERROR) {
            LocnBP.mResolvingError = false;
            if (resultCode == Activity.RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!LocnBP.mGoogleApiClient.isConnecting() &&
                        !LocnBP.mGoogleApiClient.isConnected()) {
                    LocnBP.mGoogleApiClient.connect();
                }
            }
        }
    }
    public void onToggleRoads(MenuItem item) {
    	displayMapType 	= (displayMapType + 1) % 4;
    	mMap.setMapType( mapTypes[ displayMapType ] );
        invalidateOptionsMenu();
    }
    public void onToggleAdd(MenuItem item) {
    	clickAddProject = !clickAddProject;
    	if (clickAddProject) {
    		Toast.makeText(this, "Click on map to add a new project", Toast.LENGTH_SHORT).show();
    	}
    	invalidateOptionsMenu();
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

        for (Projects p : allProjects ) {
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
        Log.i("info", "in copy data base at finally");
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
            Log.i("info", "error when copying database");

        }
    }
//
//        Setup Application
//
	public static void seeWholeMap() {

            LatLngBounds.Builder setOfBounds = new LatLngBounds.Builder();
            if (GC.mCurrentLocation != null) {
                setOfBounds.include(new LatLng(GC.mCurrentLocation.getLatitude(),
                                               GC.mCurrentLocation.getLongitude()) );
            }

            for( Projects mProject : allProjects ) {
                if (mProject.getMarker().isVisible())
                    setOfBounds.include(new LatLng(mProject.getLatitude(), mProject.getLongitude()));
            }
            GC.bounds  = setOfBounds.build();

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(GC.bounds, 50));

	}

//
//    Marker related listeners.
//
    @Override public boolean onMarkerClick(final Marker marker) {
    	double			markerLon 		= marker.getPosition().longitude;
        double			markerLat 		= marker.getPosition().latitude;
        int				numTooClose 	= 0;
    	double			earthCircum 	= earthRadius * 2 * Math.PI * Math.cos( markerLat / 180 * Math.PI );

    	// Test to see if any other projects are so close that it is hard to click on the right one
        double minDistance = 
      		  50 																//    Max resolution of click (units = dp)
      		* earthCircum 										  				//  * earth circumference at this Latitude (units = km)
      		/ (256 * Math.pow(2, MainActivity.mMap.getCameraPosition().zoom) ); //  / size of earth (units = dp)
        
        LatLngBounds.Builder setOfBounds = new LatLngBounds.Builder();
       	for( Projects mProject : allProjects ) {
       		
       		// Approximate Equirectangular -- works if (lat1,lon1) ~ (lat2,lon2)
            double x = (mProject.getLongitude() - markerLon) * Math.cos( markerLat / 180 * Math.PI );
            double y = (mProject.getLatitude()  - markerLat);
            double distance = Math.sqrt(x * x + y * y) * Math.PI / 180 * earthRadius;
            
            if (distance < minDistance) {
            	numTooClose++;
            	setOfBounds.include( new LatLng( mProject.getLatitude(), mProject.getLongitude() ));
            }
       	}

        if (GC.mCurrentLocation != null) {
            Location testLocn = GC.mCurrentLocation;

       		// Approximate Equirectangular -- works if (lat1,lon1) ~ (lat2,lon2)
            double x = (testLocn.getLongitude() - markerLon) * Math.cos( markerLat / 180 * Math.PI );
            double y = (testLocn.getLatitude()  - markerLat);
            double distance = Math.sqrt(x * x + y * y) * Math.PI / 180 * earthRadius;

            if (distance < minDistance) {
            	numTooClose++;
            	setOfBounds.include( new LatLng( testLocn.getLatitude(), testLocn.getLongitude() ));
            }
       	}

       	if (numTooClose > 1) {														// If more than one then zoom in and try again
       		mMap.animateCamera( CameraUpdateFactory.newLatLngBounds(setOfBounds.build(), 100) );

       	} else {																	// If just one (i.e. our project) then shift and display dialog

   	       	mMap.animateCamera( CameraUpdateFactory.newLatLngZoom(new LatLng(markerLat, markerLon), TightZoom), 1000, null );
   	       	
   	       	String 		markerID 		= marker.getId();
            for (int i = 0; i < allProjects.size(); i++ ) {
                projectToEdit = allProjects.get(i);
   	       		if ( markerID.compareTo(projectToEdit.getMarker().getId()) == 0 ) {
   	       			showEditDialog( i, GC.EDIT_RQ_CURRENT );
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
            projectToEdit = allProjects.get(i);
    		if ( markerID.compareTo(projectToEdit.getMarker().getId()) == 0 ) {
                projectToEdit.setLatitude(  marker.getPosition().latitude  );
                projectToEdit.setLongitude(marker.getPosition().longitude);
    			showEditDialog(i, GC.EDIT_RQ_SHIFTED);
    			break;
    		}
    	}
    }
	@Override public void onMapClick(LatLng point) {
		if (clickAddProject) {
			onToggleAdd(null);
			projectToEdit = new Projects(0, null, -1, point.latitude, point.longitude, null, "Planned");
			showEditDialog( -1, GC.EDIT_RQ_NEW );
		} else {
			seeWholeMap();
		}
	}
    public void showEditDialog( int projectIndex, int requestCode ) {
        GC.mUsedList.Update(allProjects.get(projectIndex).getId(), false);
        Intent intent = new Intent(this, ProjectEdit.class);
        intent.putExtra("Index", projectIndex);
        intent.putExtra("Request", requestCode);
        startActivity(intent);
    }

    private final class ServiceHandler  extends Handler {
        public ServiceHandler (Looper looper) {
            super(looper);
        }
        @Override public void handleMessage(Message msg) {
            switch (msg.arg1) {
                case GC.MSG_CONNECTED:
                    Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                    break;
                default:
            }
        }
    }
}
