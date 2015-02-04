package ca.nbenner.bizprocribbing;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.FormatException;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;


public class UpdateDatabases extends AsyncTask<String, Integer, byte[]> {
	private	static	Context					c;
	private static 	ConnectivityManager 	connMgr	= null;

	UpdateDatabases(Context context) {
		c			= context;
		connMgr 	= (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

    public void updateProject(Projects project) {
        String urlCommand = "Cribbing/project_add.php?" + project.createURLString(GC.myEmployeeNumber) + "&";
        callRemoteServer( urlCommand );
    }
    public void updateTimeSheet(TimeSheet timesheet) {
        String urlCommand = "Cribbing/timesheet_add.php?" + timesheet.createURLString(GC.myEmployeeNumber) + "&";
        callRemoteServer( urlCommand );
    }
    public void downloadData() {
        String urlCommand = "Cribbing/project_and_time_download.php?";
        callRemoteServer( urlCommand );
    }
    public void callRemoteServer(String urlCommand) {
        String qualifiers = "project_record=" + Projects.maxRecord( MainActivity.allProjects ) +
                            "&time_record="   + TimeSheet.maxRecord( MainActivity.myTime )     +
                            "&employee="      + GC.myEmployeeNumber;

        execute(urlCommand + qualifiers);
    }

	@Override protected byte[] doInBackground(String... command) {
		HttpURLConnection 	conn		= null;
		InputStream 		is 			= null;
		int					bytesRead 	= 0, count = 0, maxSize = 16*1024;
		byte[]				byteArray 	= new byte[maxSize];

        // todo decide if maxSize is right size and if we need to free array to avoid leaking memory
		
		Log.d("my Debug", "In Background, first line");
		NetworkInfo 		networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			try {
				URL url = new URL("http", "suzannebenner.com", command[0]);
				conn = (HttpURLConnection) url.openConnection();
				conn.setReadTimeout(10000 /* milliseconds */);
				conn.setConnectTimeout(15000 /* milliseconds */);
				conn.setRequestMethod("GET");
				conn.setDoInput(true);

				// Starts the query
				conn.connect();
				int response = conn.getResponseCode();
				Log.d("my Debug", "The response is: " + response);
				is = conn.getInputStream();
				while( bytesRead != -1 && count < maxSize ) {
					bytesRead = is.read(byteArray, count, maxSize - count);
					count += bytesRead;
				}

				// Makes sure that the InputStream is closed after the app is finished using it.
			} catch (IOException e) {
				System.err.println("MalformedURLException: " + e.getMessage());
			} finally {
				if (is != null) {
					try {
						is.close();
						conn.disconnect();
					} catch (IOException e) {
						System.err.println("MalformedURLException: " + e.getMessage());
					}
				} 
			} 
		} else {
			Log.d("my Debug", "Couldn't connect to network");
		}
		return byteArray;
	}
	@Override protected void onProgressUpdate(Integer... progress) {
		return;
	}

    @Override protected void onPostExecute(byte[] byteArray) {
        ArrayList<Projects>  inputProjectData;
        ArrayList<TimeSheet> inputTimeSheetData;
        JsonReader reader = null;

        try {
            reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(byteArray), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.err.println("TimeSheet or Project File Format Error: " + e.getMessage());
        }

        try {
            // Read in Projects Array
            reader.beginObject();

            String name = reader.nextName();    // assumed to be PROJECTS
            inputProjectData = readProjectsArray(reader);

            if (MainActivity.allProjects == null)
                MainActivity.allProjects.addAll(inputProjectData);
            else {
                for (Projects ip : inputProjectData) {
                    Iterator itr = MainActivity.allProjects.iterator();
                    while (itr.hasNext()) {
                        Projects p = (Projects) itr.next();
                        if (p.getId() == ip.getId()) {
                            p.removeMarker();
                            itr.remove();
                        }
                    }
                    ip.setMarker();
                    MainActivity.allProjects.add(ip);
                }
            }

            if (MainActivity.allProjects != null &&
                MainActivity.mMap        != null) {
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

            //  Read in TimeSheet Array
            name = reader.nextName();    // assumed to be TIMESHEETS
            inputTimeSheetData = readTimeSheetArray(reader);
            reader.endObject();

            if (inputTimeSheetData != null) {
                for (TimeSheet newTS : inputTimeSheetData) {
                    String newDate = newTS.getDate();
                    String newEmployee = newTS.getEmployee();
                    if (MainActivity.myTime != null) {
                        Iterator itr = MainActivity.myTime.iterator();
                        while (itr.hasNext()) {
                            TimeSheet ts = (TimeSheet) itr.next();
                            if (ts.getDate().equals(     newDate     ) &&
                                ts.getEmployee().equals( newEmployee )    )
                                itr.remove();
                        }
                    }
                    MainActivity.myTime.add(newTS);
                }
            }

		} catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (FormatException e) {
            System.err.println("TimeSheet or Project File Format Error: " + e.getMessage());
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
	}
	public ArrayList<TimeSheet> readTimeSheetArray(JsonReader reader) throws IOException, FormatException {
        ArrayList<TimeSheet> timeSheetsIn = new ArrayList<TimeSheet>();

		try {
		    reader.beginArray();
			while (reader.hasNext()) {
                TimeSheet ts = readTimeSheet(reader);
                timeSheetsIn.add(ts);
                new TimeSheetList(c).AddToDB(ts);
			}
            reader.endArray();
		} catch (IOException e) {
			System.err.println("JSON IO error: " + e.getMessage());
		}
		return timeSheetsIn;
	}
	public TimeSheet readTimeSheet(JsonReader reader) throws IOException, FormatException {
        String  date = null, employee = null;
        int     record = 0, j = 3;
        int[] args = new int[(MainActivity.maxNumOfProjects+1) * 3];
        for (int i = 3; i < args.length; i++)
            args[i] = -1;
        args[0] = 0;  // Project ID for Lunch;

		reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
            String value = "-1";
            if (reader.peek() == JsonToken.NULL)
                reader.nextNull();
            else
                value = reader.nextString();

            try {
                if (name.equals("Record")) {
                    record 		= Integer.valueOf(value);
                } else if (name.equals("Date")) {
                    date        = value;
                } else if (name.startsWith("Employee")) {
                    employee    = value;
                } else if (name.startsWith("Project")) {
                    args[j++] = Integer.valueOf(value);
                } else if (name.equals("Lunch_start")) 	{
                    args[1] = Integer.valueOf(value);
                } else if (name.equals("Lunch_end")) 	{
                    args[2] = Integer.valueOf(value);
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
		reader.endObject();

		return new TimeSheet(record, date, employee, args);
	}
    public ArrayList<Projects> readProjectsArray(JsonReader reader) throws IOException {
        ArrayList<Projects> projectsIn = new ArrayList<Projects>();

        try {
            reader.beginArray();
            while (reader.hasNext()) {
                Projects p = readProject(reader);
                projectsIn.add(p);
                new ProjectList(c).AddToDB(p);
            }
            reader.endArray();
        } catch (IOException e) {
            System.err.println("JSON IO error: " + e.getMessage());
        }
        return projectsIn;
    }
    public Projects readProject(JsonReader reader) throws IOException {
        int		id 		= 0,    record  = 0;
        long    timestamp = 0;
        double	lat 	= 0, 	lng 	= 0;
        String	address = null,	status 	= "Planned",	customer 	= null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();

            if 		(name.equals("record")) 		{
                record 		= reader.nextInt();
            } else if (name.equals("id")) 	{
                id 		    = reader.nextInt();
            } else if (name.equals("latitude")) 	{
                lat 		= reader.nextDouble();
            } else if (name.equals("longitude")) 	{
                lng 		= reader.nextDouble();
            } else if (name.equals("address")) 	{
                address	= reader.nextString();
            } else if (name.equals("status")) 	{
                status 	= reader.nextString();
            } else if (name.equals("customer")) 	{
                customer 	= reader.nextString();
            } else if (name.equals("timestamp")) 	{
                timestamp = reader.nextLong();
            } else if (name.equals("author")) 	{
                reader.skipValue();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return new Projects( record, customer, id, lat, lng, address, status, timestamp);
    }

}

class ProjectList extends DBBaseAdapter implements BaseColumns {
    Context c;

    // <editor-fold desc="Column definitions and Constants"
    public static final String TABLE_NAME               = "project_list";
    public static final String COLUMN_NAME_RECORD       = "record";
    public static final String COLUMN_NAME_ID           = "id";
    public static final String COLUMN_NAME_LATITUDE     = "latitude";
    public static final String COLUMN_NAME_LONGITUDE    = "longitude";
    public static final String COLUMN_NAME_ADDRESS      = "address";
    public static final String COLUMN_NAME_STATUS       = "status";
    public static final String COLUMN_NAME_CUSTOMER     = "customer";
    public static final String COLUMN_NAME_TIMESTAMP    = "timestamp";

    public static final String[] QUERY_STRING = new String[] {
            COLUMN_NAME_RECORD,
            COLUMN_NAME_ID,
            COLUMN_NAME_LATITUDE,
            COLUMN_NAME_LONGITUDE,
            COLUMN_NAME_ADDRESS,
            COLUMN_NAME_STATUS,
            COLUMN_NAME_CUSTOMER,
            COLUMN_NAME_TIMESTAMP };
    // </editor-fold>

    public ProjectList(Context context) {
        super(context);
        c = context;
    }

    public ArrayList<Projects> readProjectsFromDB() {
        SQLiteDatabase db = openDb();
        Cursor c = db.query(
                TABLE_NAME,                             // The table to query
                QUERY_STRING,                           // The columns to return from the query
                null,                                   // The columns for the where clause
                null,                                   // The values for the where clause
                null,                                   // don't group the rows
                null,                                   // don't filter by row groups
                null);                                  // The sort order

        ArrayList<Projects> list = new ArrayList<Projects>();
        c.moveToFirst();
        int rx = c.getColumnIndex( COLUMN_NAME_RECORD);
        int cx = c.getColumnIndex( COLUMN_NAME_CUSTOMER);
        int ix = c.getColumnIndex( COLUMN_NAME_ID);
        int lt = c.getColumnIndex( COLUMN_NAME_LATITUDE);
        int lg = c.getColumnIndex( COLUMN_NAME_LONGITUDE);
        int ax = c.getColumnIndex( COLUMN_NAME_ADDRESS);
        int sx = c.getColumnIndex( COLUMN_NAME_STATUS);
        int tx = c.getColumnIndex( COLUMN_NAME_TIMESTAMP);
        while (!c.isAfterLast()) {
            list.add(new Projects(c.getInt(rx), c.getString(cx), c.getInt(ix), c.getDouble(lt), c.getDouble(lg), c.getString(ax), c.getString(sx), c.getLong(tx)));
            c.moveToNext();
        }

        closeDb();

        return list;
    }
    public void AddToDB( Projects project ) {
        SQLiteDatabase db = openDb();

        // Delete the record if it exists
        db.delete(  TABLE_NAME,                                                 // The table to query
                    COLUMN_NAME_ID     + "=" + project.getId(),
                    null);                                                      // Optional arguments for the WHERE clause

        // Insert the record into the database
        ContentValues values = new ContentValues();
        values.put( COLUMN_NAME_RECORD,     project.getRecord()         );
        values.put( COLUMN_NAME_ID,         project.getId()             );
        values.put( COLUMN_NAME_LATITUDE,   project.getLatitude()       );
        values.put( COLUMN_NAME_LONGITUDE,  project.getLongitude()      );
        values.put( COLUMN_NAME_ADDRESS,    project.getAddress()        );
        values.put( COLUMN_NAME_STATUS,     project.getStatus()         );
        values.put( COLUMN_NAME_CUSTOMER,   project.getCustomer()       );
        values.put( COLUMN_NAME_TIMESTAMP,  System.currentTimeMillis()  );

        db.insert(  TABLE_NAME,                                 // The table to query
                    null,                                       // Set to NULL so that it won't insert a blank row (if values = null)
                    values);                                    // Optional arguments for the WHERE clause

        db.close();
    }
    public void Update( Projects project ) {
        AddToDB( project );
        new UpdateDatabases(c).updateProject( project );
    }
}

class TimeSheetList extends DBBaseAdapter implements BaseColumns {
    Context c;

    // <editor-fold desc="Column definitions and Constants"
    public static final String TABLE_NAME                   = "timesheet_list";
    public static final String COLUMN_NAME_RECORD           = "record";
    public static final String COLUMN_NAME_DATE             = "date";
    public static final String COLUMN_NAME_EMPLOYEE         = "employee";
    public static final String COLUMN_NAME_PROJECT          = "project";
    public static final String COLUMN_NAME_PROJECT_START    = "project_start";
    public static final String COLUMN_NAME_PROJECT_END      = "project_end";
    public static final String COLUMN_NAME_AUTHOR           = "author";

    public static String[] QUERY_STRING = new String[3 + 3 * (MainActivity.maxNumOfProjects+1)];
    {
        QUERY_STRING[0] = COLUMN_NAME_RECORD;
        QUERY_STRING[1] = COLUMN_NAME_DATE;
        QUERY_STRING[2] = COLUMN_NAME_EMPLOYEE;
        for (int i = 0; i <= MainActivity.maxNumOfProjects; i++) {
            QUERY_STRING[3 + i*3] = COLUMN_NAME_PROJECT        + i;
            QUERY_STRING[4 + i*3] = COLUMN_NAME_PROJECT_START  + i;
            QUERY_STRING[5 + i*3] = COLUMN_NAME_PROJECT_END    + i;
        }
    }
    // </editor-fold>

    public TimeSheetList(Context context) {
        super(context);
        c = context;
    }

    public ArrayList<TimeSheet> readTimeSheetsFromDB() {
        SQLiteDatabase db = openDb();
        Cursor c = db.query(
                TABLE_NAME,                             // The table to query
                QUERY_STRING,                           // The columns to return from the query
                null,                                   // The columns for the where clause
                null,                                   // The values for the where clause
                null,                                   // don't group the rows
                null,                                   // don't filter by row groups
                null);                                  // The sort order

        ArrayList<TimeSheet> list = new ArrayList<TimeSheet>();
        c.moveToFirst();
        int rx   = c.getColumnIndex( COLUMN_NAME_RECORD);
        int dx   = c.getColumnIndex( COLUMN_NAME_DATE);
        int ex   = c.getColumnIndex( COLUMN_NAME_EMPLOYEE);

        while (!c.isAfterLast()) {
            int[] args = new int[(MainActivity.maxNumOfProjects+1) * 3];
            for (int i = 0; i < 3*(MainActivity.maxNumOfProjects+1); i++) {
                args[i] = c.getInt(3+i);
            }
            list.add(new TimeSheet(c.getInt(rx), c.getString(dx), c.getString(ex), args) );
            c.moveToNext();
        }

        closeDb();

        return list;
    }
    public void AddToDB( TimeSheet ts ) {
        SQLiteDatabase db = openDb();

        // Delete the record if it exists
        db.delete(  TABLE_NAME,                                         // The table to query
                    COLUMN_NAME_DATE     + "=" + ts.getDate() + " AND " + // The WHERE clause
                    COLUMN_NAME_EMPLOYEE + "=" + GC.myEmployeeNumber,
                    null);                                              // Optional arguments for the WHERE clause

        // Insert the record into the database
        ContentValues values = new ContentValues();
        values.put( COLUMN_NAME_RECORD,        ts.getRecord()           );
        values.put( COLUMN_NAME_DATE,          ts.getDate()             );
        values.put( COLUMN_NAME_EMPLOYEE,      GC.myEmployeeNumber      );
        for (int i = 0; i <= MainActivity.maxNumOfProjects; i++) {
            values.put( QUERY_STRING[3 + i*3], ts.getProjectID(i)       );
            values.put( QUERY_STRING[4 + i*3], ts.getStartTime(i)       );
            values.put( QUERY_STRING[5 + i*3], ts.getEndTime(i)         );
        }

        db.insert(  TABLE_NAME,                                         // The table to query
                    null,                                               // Set to NULL so that it won't insert a blank row (if values = null)
                    values);                                            // Optional arguments for the WHERE clause

        db.close();
    }
    public void Update( TimeSheet ts ) {
        AddToDB( ts );
        new UpdateDatabases(c).updateTimeSheet(ts);
    }
}

class LocationFixes extends DBBaseAdapter implements BaseColumns {
    Context c;

    // <editor-fold desc="Column definitions and Constants"
    public static final String TABLE_NAME                   = "where_i_was";
    public static final String COLUMN_NAME_TIMESTAMP        = "timestamp";
    public static final String COLUMN_NAME_LATITUDE         = "latitude";
    public static final String COLUMN_NAME_LONGITUDE        = "longitude";
    public static final String COLUMN_NAME_ACCURACY         = "accuracy";
    public static final String COLUMN_NAME_SPEED            = "speed";

    public static String[] QUERY_STRING = new String[] {
            COLUMN_NAME_TIMESTAMP,
            COLUMN_NAME_LATITUDE,
            COLUMN_NAME_LONGITUDE,
            COLUMN_NAME_ACCURACY,
            COLUMN_NAME_SPEED       };
    // </editor-fold>

    public LocationFixes(Context context) {
        super(context);
        c = context;
    }

    public ArrayList<Location> readLocationsFromDB() {
        SQLiteDatabase db = openDb();
//        Cursor c = db.query(
//                TABLE_NAME,                             // The table to query
//                QUERY_STRING,                           // The columns to return from the query
//                null,                                   // The columns for the where clause
//                null,                                   // The values for the where clause
//                null,                                   // don't group the rows
//                null,                                   // don't filter by row groups
//                null);                                  // The sort order
//
        ArrayList<Location> list = new ArrayList<Location>();
//        c.moveToFirst();
//        int rx   = c.getColumnIndex( COLUMN_NAME_RECORD);
//        int dx   = c.getColumnIndex( COLUMN_NAME_DATE);
//        int ex   = c.getColumnIndex( COLUMN_NAME_EMPLOYEE);
//
//        while (!c.isAfterLast()) {
//            int[] args = new int[(MainActivity.maxNumOfProjects+1) * 3];
//            for (int i = 0; i < 3*(MainActivity.maxNumOfProjects+1); i++) {
//                args[i] = c.getInt(3+i);
//            }
//            list.add(new TimeSheet(c.getInt(rx), c.getString(dx), c.getString(ex), args) );
//            c.moveToNext();
//        }
//
//        closeDb();

        return list;
    }
    public void Update( Location locn ) {
        SQLiteDatabase db = openDb();

        // Insert the record into the database
        ContentValues values = new ContentValues();
        values.put( COLUMN_NAME_TIMESTAMP,  locn.getTime()      );
        values.put( COLUMN_NAME_LATITUDE,   locn.getLatitude()  );
        values.put( COLUMN_NAME_LONGITUDE,  locn.getLongitude() );
        values.put( COLUMN_NAME_ACCURACY,   locn.getAccuracy()  );
        values.put( COLUMN_NAME_SPEED,      locn.getSpeed()     );

        db.insert(  TABLE_NAME,                                         // The table to query
                    null,                                               // Set to NULL so that it won't insert a blank row (if values = null)
                    values);                                            // Optional arguments for the WHERE clause

        db.close();
    }
}
