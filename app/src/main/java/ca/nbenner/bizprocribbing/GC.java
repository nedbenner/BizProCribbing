package ca.nbenner.bizprocribbing;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;
import java.util.Arrays;

// Global Constants

public class GC {
    // <editor-fold desc="Global Variables and Constants"
    public final static int HIT_THUMB           = 0;			//  check to see which Thumb is chosen
    public final static int HIT_LABEL           = 1;			//  check to see which Project Label is chosen

    public static String    myEmployeeNumber    = null;
    public static LatLngBounds bounds;
    public static long      displayRecordsSince = 0;
    public static SharedPreferences prefs       = null;
    public static UsedList  mUsedList;
    public static Location  mCurrentLocation    = null;
    public static Marker    myLocnMarker        = null;
    public static boolean   isLocationTracking  = false;
    public static ArrayList<String> statusList, shortStatusList;
    public static int       cells;
    public static int       localABTestsVersion;
    private static float    maxEntry            = 0;
    private static float    viewEntry           = 0;
    public static int       mapType             = 1;            // Roads or Satellite view, default = Roads

    // </editor-fold>

    public GC(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        myEmployeeNumber    = prefs.getString("pref_key_employee_number",      "0"    );
        localABTestsVersion = prefs.getInt(   "pref_key_ab_tests_version",      0     );
        displayRecordsSince = prefs.getLong(  "pref_key_display_records_since", 0     );
        mapType             = prefs.getInt(   "pref_key_map_type",              1     );
        LatLng southwest    = new LatLng( prefs.getFloat("pref_key_bounds_sw_lat", 0),
                                          prefs.getFloat("pref_key_bounds_sw_lng", 0) );
        LatLng northeast    = new LatLng( prefs.getFloat("pref_key_bounds_ne_lat", 1),
                                          prefs.getFloat("pref_key_bounds_ne_lng", 1) );
        bounds              = new LatLngBounds( southwest, northeast );

        mUsedList           = new UsedList(context);

        Resources res       = context.getResources();
        cells               = res.getInteger(R.integer.ab_cells);
        statusList          = new ArrayList<String>( Arrays.asList(res.getStringArray(R.array.statusList)) );
        shortStatusList     = (ArrayList<String>) statusList.clone();
        shortStatusList.remove( shortStatusList.size()-1 );

    }
    public void putInternalData () {
        SharedPreferences.Editor mEditor = prefs.edit();
        mEditor.putString("pref_key_employee_number",       myEmployeeNumber);
        mEditor.putInt(   "pref_key_ab_tests_version",      localABTestsVersion);
        mEditor.putLong(  "pref_key_display_records_since", displayRecordsSince);
        mEditor.putFloat( "pref_key_bounds_ne_lat", (float) bounds.northeast.latitude);
        mEditor.putFloat( "pref_key_bounds_ne_lng", (float) bounds.northeast.longitude);
        mEditor.putFloat( "pref_key_bounds_sw_lat", (float) bounds.southwest.latitude);
        mEditor.putFloat( "pref_key_bounds_sw_lng", (float) bounds.southwest.longitude);
        mEditor.putInt(   "pref_key_map_type",              mapType);
        mEditor.apply();
    }

    public class UsedList extends DBBaseAdapter implements BaseColumns {
        // Column definitions
        protected static final String TABLE_NAME = "used_recently";
        public static final String COLUMN_NAME_PROJECT_ID = "project_id";
        public static final String COLUMN_NAME_RECORD = "record";

        public UsedList(Context context) {
            super(context);
        }

        public ArrayList<Integer> GetList(int findProjectID) {
            SQLiteDatabase db = openDb();
            Cursor c = db.query(
                    TABLE_NAME,                             // The table to query
                    new String[] {COLUMN_NAME_PROJECT_ID, COLUMN_NAME_RECORD},  // The columns to return from the query
                    null,                                   // The columns for the where clause
                    null,                                   // The values for the where clause
                    null,                                   // don't group the rows
                    null,                                   // don't filter by row groups
                    COLUMN_NAME_RECORD + " DESC" );         // The sort order

            ArrayList<Integer> list = new ArrayList<Integer>();
            maxEntry = 0;
            int count = 0;
            float entry3 = 1, entry4 = 0;
            c.moveToFirst();
            while (!c.isAfterLast()) {
                list.add(c.getInt(0));
                if (maxEntry == 0)                  maxEntry  = c.getFloat(1);
                if (count   <= 2)                   entry3    = c.getFloat(1);
                if (count   == 3)                   entry4    = c.getFloat(1);
                if (findProjectID == c.getInt(0))   viewEntry = c.getFloat(1);
                c.moveToNext();
                if (count++ == 10) break;
            }
            viewEntry = Math.max(viewEntry, (entry3 + entry4) / 2f);
            closeDb();

            return list;
        }

        public void Update( int[] projectID, boolean isUsed ) {
            for (int i = projectID.length - 1; i == 0; i--) {
                int pid = projectID[i];
                Update( pid, isUsed);
            }
        }
        public void Update( int pid, boolean isUsed ) {                   // Ensures that projectID is the last entry in the table
            if (pid > 0) {
                GetList(pid);

                SQLiteDatabase db = openDb();
                db.delete(TABLE_NAME,                                 // The table to query
                        COLUMN_NAME_PROJECT_ID + "=" + pid,         // The WHERE clause
                        null);                                      // Optional arguments for the WHERE clause

                ContentValues values = new ContentValues();
                values.put(COLUMN_NAME_PROJECT_ID, pid);
                values.put(COLUMN_NAME_RECORD, isUsed ? maxEntry + 1f : viewEntry);
                db.insert(TABLE_NAME,                                 // The table to query
                        null,                                       // Set to NULL so that it won't insert a blank row (if values = null)
                        values);                                    // Optional arguments for the WHERE clause
                db.close();

            }
        }
    }

}
