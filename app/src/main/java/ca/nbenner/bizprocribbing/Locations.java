package ca.nbenner.bizprocribbing;

import android.location.Location;
import android.view.View;
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
import java.util.GregorianCalendar;
import java.util.Locale;

public class Locations {

    static Calendar             t               = GregorianCalendar.getInstance();
    static ArrayList <Location> allLocations    = new ArrayList<Location>();
    static ArrayList <Marker>   locnMarkers     = new ArrayList<Marker>();
    static SimpleDateFormat     sdfDate         = new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA );
    static SimpleDateFormat     sdfTime         = new SimpleDateFormat("h:mm a", Locale.CANADA );
    static long                 startTime       = 0;
    static LatLngBounds.Builder locnBounds      = null;

    public static void display(boolean show) {
        t.setTimeInMillis( System.currentTimeMillis() );

        allLocations = new LocationList().readLocationsFromDB(0L, null);
        hideAllMarkers();
        if (show)
            showSelectedMarkers();
    }
    private static void hideAllMarkers() {
        if (!locnMarkers.isEmpty())
            for (Marker m : locnMarkers)
                m.remove();

        locnMarkers.clear();
    }
    private static void showSelectedMarkers() {
        String snippet;
        Marker newMarker;

        locnBounds = new LatLngBounds.Builder();

        try {
            startTime = sdfDate.parse(sdfDate.format(t.getTime())).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long endTime = startTime + 24 * 60 * 60 * 1000;

        if (!allLocations.isEmpty()) {

    loop:
            for (Location loc : allLocations) {
                if ( loc.getTime() >= startTime &&
                     loc.getTime() <  endTime ) {

                    LatLng thisPosn = new LatLng(loc.getLatitude(), loc.getLongitude());
                    locnBounds.include(thisPosn);

                    if (loc.getSpeed() == -2 && locnMarkers.size() > 0) {
                        Marker check = locnMarkers.get(locnMarkers.size() - 1);
                        snippet = check.getSnippet();
                        if (snippet.endsWith("11:59 PM")) {
                            snippet = snippet.substring(0, snippet.length() - 8) + sdfTime.format(loc.getTime());
                            check.setSnippet(snippet);
                            continue loop;
                        }
                    }

                    newMarker = ActivityMain.mMap.addMarker(new MarkerOptions()     // Create Marker
                            .position(thisPosn)
                            .title(sdfDate.format(loc.getTime()))
                            .icon(BitmapDescriptorFactory.fromResource(
                                    loc.getSpeed() < 0 ? R.drawable.stationary : R.drawable.my_locn)));
                    newMarker.setAnchor(0.5f, 0.5f);

                    if      (loc.getSpeed() == -2 )
                        snippet = "12:00 AM until " + sdfTime.format(loc.getTime());
                    else if (loc.getSpeed() == -1 )
                        snippet = sdfTime.format(loc.getTime()) + " until 11:59 PM";
                    else
                        snippet = sdfTime.format(loc.getTime());

                    newMarker.setSnippet(snippet);
                    locnMarkers.add(newMarker);
                }
            }
        }

    }
    public static void changeDay (int adjustment, View v) {
        if (adjustment == 0)
            t.setTimeInMillis( System.currentTimeMillis() );
        else
            t.add(Calendar.DAY_OF_MONTH, adjustment);

        hideAllMarkers();
        showSelectedMarkers();
        Button b = (Button) v.getRootView().findViewById(R.id.displayedDateButton);
        b.setText( new SimpleDateFormat("MMM dd, yyyy", Locale.CANADA).format(t.getTime()));
    }
}


