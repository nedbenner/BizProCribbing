package ca.nbenner.bizprocribbing;

import android.app.Activity;
import android.location.Location;
import android.widget.Button;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class Locations {

    static Calendar             t               = Calendar.getInstance();
    static ArrayList <Location> allLocations    = new ArrayList<Location>();
    static ArrayList <Marker>   allMarkers      = new ArrayList<Marker>();
    static SimpleDateFormat     sdf             = new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA );
    static SimpleDateFormat     sdfDisplay      = new SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.CANADA );
    static long                 startTime       = 0;
    static LatLngBounds.Builder locnBounds      = null;

    public static void display(boolean show) {
        t.setTimeInMillis( System.currentTimeMillis() );

        allLocations = new LocationList().readLocationsFromDB(0L);
        hideAllMarkers();
        if (show)
            showSelectedMarkers();
    }
    private static void hideAllMarkers() {
        if (!allMarkers.isEmpty())
            for (Marker m : allMarkers)
                m.remove();

            allMarkers.clear();
    }
    private static void showSelectedMarkers() {

        locnBounds = new LatLngBounds.Builder();

        try {
            startTime = sdf.parse(sdf.format(t.getTime())).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long endTime = startTime + 24 * 60 * 60 * 1000;

        if (!allLocations.isEmpty()) {
            for (Location loc : allLocations) {
                if ( loc.getTime() >= startTime &&
                     loc.getTime() <  endTime ) {
                    LatLng thisPosn = new LatLng(loc.getLatitude(), loc.getLongitude());
                    Marker newMarker = ActivityMain.mMap.addMarker(new MarkerOptions()
                            .position(thisPosn)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.my_locn)) );
                    newMarker.setAnchor(0.5f, 0.5f);
                    newMarker.setTitle(sdfDisplay.format(loc.getTime()));
                    allMarkers.add( newMarker );
                    locnBounds.include(thisPosn);
                }
            }
        }

    }
    public static void changeDay (int adjustment) {
        if (adjustment == 0)
            t.setTimeInMillis( System.currentTimeMillis() );
        else
            t.add(Calendar.DAY_OF_MONTH, adjustment);

        hideAllMarkers();
        showSelectedMarkers();
        Button b = (Button) ((Activity) GD.getAppContext()).findViewById(R.id.displayedDateButton);
        b.setText( new SimpleDateFormat("MMM dd, yyyy", Locale.CANADA).format(t.getTime()));

    }
}


