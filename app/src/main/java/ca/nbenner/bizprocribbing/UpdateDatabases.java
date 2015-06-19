package ca.nbenner.bizprocribbing;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.FormatException;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.BaseColumns;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import com.google.android.gms.location.LocationRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.zip.DataFormatException;


public class UpdateDatabases extends AsyncTask<String, Integer, byte[]> {
	private	Context			    c;
	private ConnectivityManager connMgr	         = null;
    private static boolean      busy             = false,
                                callIsOnStack    = false,
                                dataDownLoadOnly = false;
    private static String       sqlValuesTimeSheet, sqlValuesProject, sqlValuesAsBuilt;

	UpdateDatabases(Context context) {
		c			= context;
		connMgr 	= (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

    public void updateProject(Project project) {
        if (sqlValuesProject == null)
            sqlValuesProject = project.createURLString(GC.myEmployeeNumber);
        else
            sqlValuesProject = sqlValuesProject.concat( project.createURLString(GC.myEmployeeNumber) );

        callRemoteServer();
    }
    public void updateTimeSheet(TimeSheet timesheet) {
        if (sqlValuesTimeSheet == null)
            sqlValuesTimeSheet = timesheet.createURLString(GC.myEmployeeNumber);
        else
            sqlValuesTimeSheet = sqlValuesTimeSheet.concat( timesheet.createURLString(GC.myEmployeeNumber) );

        callRemoteServer();
    }
    public void updateAsBuilt(AsBuilt asBuilt) {
        if (sqlValuesAsBuilt == null) {
            sqlValuesAsBuilt = asBuilt.createURLString();
        } else {
            sqlValuesAsBuilt = sqlValuesAsBuilt.concat(",");
            sqlValuesAsBuilt = sqlValuesAsBuilt.concat(asBuilt.createURLString());
        }
        callRemoteServer();
    }
    public void downloadData() {
        dataDownLoadOnly = true;
        callRemoteServer();
    }
    public void delayCall() {
        if (callIsOnStack)
            return;

        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                issueDelayedCall();
            }
        }, 5 * 1000);
        callIsOnStack = true;
    }
    public void issueDelayedCall() {
        callIsOnStack = false;
        callRemoteServer();
    }
    public void callRemoteServer() {

        if (busy) {                             // The system is busy talking to the server
            delayCall();                        // Make sure another call is pending
            return;
        }

        String qualifiers = Project.maxRecord(ActivityMain.allProjects) + "," +
                            new TimeSheetList().getMaxRecord()          + "," +
                            new ABRecordList().getMaxRecord()           + "," +
                            new ABTestList().getABTestsVersion()        + "," +
                            GC.myEmployeeNumber;

        busy = true;
        if (dataDownLoadOnly) {
            execute("Cribbing/data_download.php", qualifiers, null);
            dataDownLoadOnly    = false;

        } else if (sqlValuesProject != null) {
            execute("Cribbing/project_add.php",   qualifiers, sqlValuesProject.concat("last"));
            sqlValuesProject    = null;

        } else if (sqlValuesTimeSheet != null) {
            execute("Cribbing/timesheet_add.php", qualifiers, sqlValuesTimeSheet.concat("last"));
            sqlValuesTimeSheet  = null;

        } else if (sqlValuesAsBuilt != null) {
            execute("Cribbing/asbuilt_add.php",   qualifiers, sqlValuesAsBuilt);
            sqlValuesAsBuilt    = null;

        } else {
            busy                = false;
            return;                             // Since there is nothing to do, we will just leave
        }

        if (sqlValuesProject   != null ||
            sqlValuesTimeSheet != null ||
            sqlValuesAsBuilt   != null )        // Something is still queued to be sent
                delayCall();                    // Make sure another call is pending

    }

	@Override protected byte[] doInBackground(String... command) {
		HttpURLConnection 	conn		= null;
		InputStream 		is 			= null;
        OutputStream        output      = null;
		int					bytesRead 	= 0, count = 0, maxSize = 500*1024;
		byte[]				byteArray 	= new byte[maxSize];
        String              charset     = "UTF-8";
        String              boundary    = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
        String              CRLF        = "\r\n"; // Line separator required by multipart/form-data.

        // todo decide if maxSize is right size and if we need to free array to avoid leaking memory

		Log.d("myDebug", "In Background, first line");
		NetworkInfo 		networkInfo = connMgr.getActiveNetworkInfo();


		if (networkInfo != null && networkInfo.isConnected()) {
            try {
                URL url = new URL("http", "suzannebenner.com", command[0]);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true); // Triggers POST.
                conn.setDoInput(true);
                conn.setRequestProperty("Accept-Charset", charset);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                output = conn.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);

                // Send qualifiers.
                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"qualifiers\"").append(CRLF);
                writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF);
                writer.append(CRLF).append(command[1]).append(CRLF).flush();

                // Send more data if present.
                if (command[2] != null) {
                    writer.append("--" + boundary).append(CRLF);
                    writer.append("Content-Disposition: form-data; name=\"newRecords\"").append(CRLF);
                    writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF);
                    writer.append(CRLF).append(command[2]).append(CRLF).flush();
                }
                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.

                int response = conn.getResponseCode();
                is = conn.getInputStream();
                Log.d("myDebug", "The response is: " + response);
                while (bytesRead != -1 && count < maxSize) {
                    bytesRead = is.read(byteArray, count, maxSize - count);
                    count += bytesRead;
                }

            } catch (IOException e) {
                System.err.println("MalformedURLException: " + e.getMessage());
            } finally {
                // Makes sure that the InputStream is closed after the app is finished using it.
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
			Log.d("myDebug", "Couldn't connect to network");
		}
		return byteArray;
	}
	@Override protected void onProgressUpdate(Integer... progress) {
		return;
	}

    @Override protected void onPostExecute(byte[] byteArray) {
        ArrayList<Project>  inputProjectData;
        ArrayList<TimeSheet> inputTimeSheetData;
        ArrayList<AsBuilt> inputAsBuiltData;
        ArrayList<String[]> inputAsBuiltTests;
        JsonReader reader = null;

        try {
            reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(byteArray), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.err.println("AsBuilt, TimeSheet, Project File or AsBuiltTest Format Error: " + e.getMessage());
        }

        try {
            reader.beginObject();

            // Read in Projects Array
            reader.nextName();    // assumed to be PROJECTS
            inputProjectData = readProjectsArray(reader);

            if (ActivityMain.allProjects == null)
                ActivityMain.allProjects.addAll(inputProjectData);
            else {
                for (Project ip : inputProjectData) {
                    Iterator itr = ActivityMain.allProjects.iterator();
                    while (itr.hasNext()) {
                        Project p = (Project) itr.next();
                        if (p.getId() == ip.getId()) {
                            p.removeMarker();
                            itr.remove();
                        }
                    }
                    ip.setMarker( true );
                    ActivityMain.allProjects.add(ip);
                }
            }

            if (ActivityMain.allProjects != null &&
                ActivityMain.mMap        != null) {
                Iterator itr = ActivityMain.allProjects.iterator();
                while (itr.hasNext()) {
                    Project p = (Project) itr.next();
                    if (p.getStatus().equals("Deleted")) {
                        p.removeMarker();
                        itr.remove();
                    } else if (p.getMarker() == null) {
                        p.setMarker( true );
                        p.getMarker().setVisible(p.getTimestamp() > GC.displayRecordsSince);
                    }
                }
            }

            //  Read in TimeSheet Array
            reader.nextName();    // assumed to be TIMESHEETS
            readTimeSheetArray(reader);

            //  Read in AsBuilt Array
            reader.nextName();                          // assumed to be ASBUILTS
            readAsBuiltArray(reader);                   // puts all new records into database

            //  Read in AsBuilt Tests Array
            reader.nextName();                          // assumed to be VERSION
            int tempVersion = reader.nextInt();         // first entry is the version number
            reader.nextName();                          // assumed to be ASBUILTTESTS
            inputAsBuiltTests = readAsBuiltTestsArray(reader);
            reader.endObject();

            new ABTestList().replaceListInDB(inputAsBuiltTests, tempVersion);

            //  Verify consistency between new Projects and AsBuilt Records (add records to projects that have none)
            GD.getAbMeasureTitles();
            for (Project p : ActivityMain.allProjects)
                new ABRecordList().verifyAsBuiltsExist(p);

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

        busy = false;           // set busy flag to false so that we can send another request to server
	}
	public void readTimeSheetArray(JsonReader reader) throws IOException, FormatException {

		try {
		    reader.beginArray();

			while (reader.hasNext())
                new TimeSheetList().addToDB(readTimeSheet(reader));

            reader.endArray();

		} catch (IOException e) {
			System.err.println("JSON IO error: " + e.getMessage());
		}
	}
	public TimeSheet readTimeSheet(JsonReader reader) throws IOException, FormatException {
        String  date = null, employee = null;
        int     record = 0, j = 3;
        int[] args = new int[(ActivityMain.maxNumOfProjects+1) * 3];
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
    public ArrayList<Project> readProjectsArray(JsonReader reader) throws IOException {
        ArrayList<Project> projectsIn = new ArrayList<Project>();

        try {
            reader.beginArray();
            while (reader.hasNext()) {
                Project p = readProject(reader);
                projectsIn.add(p);
                new ProjectList(c).addToDB(p);
            }
            reader.endArray();
        } catch (IOException e) {
            System.err.println("JSON IO error: " + e.getMessage());
        }
        return projectsIn;
    }
    public Project readProject(JsonReader reader) throws IOException {
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

        return new Project( record, customer, id, lat, lng, address, status, timestamp);
    }
    public void readAsBuiltArray(JsonReader reader) throws IOException {
        ArrayList<AsBuilt> asbuiltIn = new ArrayList<AsBuilt>();

        try {
            reader.beginArray();
            while (reader.hasNext()) {
                AsBuilt ab = readAsBuilt(reader);
                asbuiltIn.add(ab);
                new ABRecordList().addToDB(ab);
            }
            reader.endArray();
        } catch (IOException e) {
            System.err.println("JSON IO error: " + e.getMessage());
        }
    }
    public AsBuilt readAsBuilt(JsonReader reader) throws IOException {
        int     record=0, projectid=0, test=0, done=0, measurement=0, author=0;
        long    timestamp=0;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();

            if 		  (name.equals("record")) 		{
                record 		    = reader.nextInt();
            } else if (name.equals("projectid")) 	{
                projectid 	    = reader.nextInt();
            } else if (name.equals("test")) 	{
                test 		    = reader.nextInt();
            } else if (name.equals("done")) 	{
                done 		    = reader.nextInt();
            } else if (name.equals("measurement")) 	{
                measurement     = reader.nextInt();
            } else if (name.equals("author")) 	{
                author 		    = reader.nextInt();
            } else if (name.equals("timestamp")) 	{
                timestamp       = reader.nextLong();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return new AsBuilt( record, projectid, test, GD.doneCode.values()[done], measurement, author, timestamp);
    }
    public ArrayList<String[]> readAsBuiltTestsArray(JsonReader reader) throws IOException {
        ArrayList<String[]> setOfTests = new ArrayList<String[]>();

        try {
            reader.beginArray();
            while (reader.hasNext()) {
                String[] abTest = readAsBuiltTests(reader);
                setOfTests.add(abTest);
            }
            reader.endArray();
        } catch (IOException e) {
            System.err.println("JSON IO error:  " + e.getMessage());
        }
        return setOfTests;
    }
    public String[] readAsBuiltTests(JsonReader reader) throws IOException {
        String[] test = new String[ ABTestList.numOfColumns ];

        reader.beginArray();
        for (int ii = 0; ii < ABTestList.numOfColumns; ii++ ) {
            test[ii] = reader.nextString();
        }
        reader.endArray();

        return test;
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

    public ArrayList<Project> readProjectsFromDB() {
        SQLiteDatabase db = openDb();
        Cursor c = db.query(
                TABLE_NAME,                             // The table to query
                QUERY_STRING,                           // The columns to return from the query
                null,                                   // The columns for the where clause
                null,                                   // The values for the where clause
                null,                                   // don't group the rows
                null,                                   // don't filter by row groups
                null);                                  // The sort order

        ArrayList<Project> list = new ArrayList<Project>();
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
            list.add(new Project(c.getInt(rx), c.getString(cx), c.getInt(ix), c.getDouble(lt), c.getDouble(lg), c.getString(ax), c.getString(sx), c.getLong(tx)));
            c.moveToNext();
        }

        closeDb();

        return list;
    }
    public void addToDB(Project project) {
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
        values.put( COLUMN_NAME_TIMESTAMP,  project.getTimestamp()      );

        db.insert(  TABLE_NAME,                                 // The table to query
                    null,                                       // Set to NULL so that it won't insert a blank row (if values = null)
                    values);                                    // Optional arguments for the WHERE clause

        db.close();
    }
    public void update(Project project) {
        addToDB(project);
        new UpdateDatabases(c).updateProject( project );
    }
    public void deleteAllData() {
        SQLiteDatabase db = openDb();

        // Delete all rows in the table
        db.delete(  TABLE_NAME,                                     // The table to query
                null,                                               // The WHERE clause
                null);                                              // Optional arguments for the WHERE clause

        db.close();
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

    public static String[] QUERY_STRING = new String[3 + 3 * (ActivityMain.maxNumOfProjects+1)];
    static {
        QUERY_STRING[0] = COLUMN_NAME_RECORD;
        QUERY_STRING[1] = COLUMN_NAME_DATE;
        QUERY_STRING[2] = COLUMN_NAME_EMPLOYEE;
        for (int i = 0; i <= ActivityMain.maxNumOfProjects; i++) {
            QUERY_STRING[3 + i*3] = COLUMN_NAME_PROJECT        + i;
            QUERY_STRING[4 + i*3] = COLUMN_NAME_PROJECT_START  + i;
            QUERY_STRING[5 + i*3] = COLUMN_NAME_PROJECT_END    + i;
        }
    }
    // </editor-fold>

    public TimeSheetList() {
        super(GD.getAppContext());
        c = GD.getAppContext();
    }

    public ArrayList<TimeSheet> readTimeSheetsFromDB( String employee, long fromTime, long toTime) {
        ArrayList<TimeSheet> ts = new ArrayList<TimeSheet>();

        SQLiteDatabase db = openDb();
        Cursor c = db.query(
                TABLE_NAME,                             // The table to query
                QUERY_STRING,                           // The columns to return from the query
                COLUMN_NAME_DATE     + ">=" + fromTime + " AND " + // The WHERE clause
                COLUMN_NAME_DATE     + "<=" + toTime   + " AND " +
                COLUMN_NAME_EMPLOYEE + "=" + employee,
                null,                                   // The values for the where clause
                null,                                   // don't group the rows
                null,                                   // don't filter by row groups
                COLUMN_NAME_DATE     + " DESC");        // The sort order

        if (c.getCount() > 0) {

            c.moveToFirst();
            int rx = c.getColumnIndex(COLUMN_NAME_RECORD);
            int dx = c.getColumnIndex(COLUMN_NAME_DATE);
            int ex = c.getColumnIndex(COLUMN_NAME_EMPLOYEE);

            while (!c.isAfterLast()) {
                int[] args = new int[(ActivityMain.maxNumOfProjects + 1) * 3];
                for (int i = 0; i < 3 * (ActivityMain.maxNumOfProjects + 1); i++) {
                    args[i] = c.getInt(3 + i);
                }
                ts.add(new TimeSheet(c.getInt(rx), new Date(c.getLong(dx)), c.getString(ex), args));
                c.moveToNext();
            }
        }

        closeDb();

        return ts;
    }
    public void addToDB(TimeSheet ts) {
        SQLiteDatabase db = openDb();

        // Delete the record if it exists
        db.delete(  TABLE_NAME,                                         // The table to query
                    COLUMN_NAME_DATE     + "=" + ts.getDate().getTime() + " AND " + // The WHERE clause
                    COLUMN_NAME_EMPLOYEE + "=" + GC.myEmployeeNumber,
                    null);                                              // Optional arguments for the WHERE clause

        if (ts.getDate().getTime() < 0) {
            int a = 0;
        }

        // Insert the record into the database
        ContentValues values = new ContentValues();
        values.put( COLUMN_NAME_RECORD,        ts.getRecord()           );
        values.put( COLUMN_NAME_DATE,          ts.getDate().getTime()   );
        values.put( COLUMN_NAME_EMPLOYEE,      GC.myEmployeeNumber      );
        for (int i = 0; i <= ActivityMain.maxNumOfProjects; i++) {
            values.put( QUERY_STRING[3 + i*3], ts.getProjectID(i)       );
            values.put( QUERY_STRING[4 + i*3], ts.getStartTime(i)       );
            values.put( QUERY_STRING[5 + i*3], ts.getEndTime(i)         );
        }

        db.insert(  TABLE_NAME,                                         // The table to query
                    null,                                               // Set to NULL so that it won't insert a blank row (if values = null)
                    values);                                            // Optional arguments for the WHERE clause

        db.close();
    }
    public boolean update(TimeSheet ts) {
        boolean neededToBeUpdated = false;

        long whichDay = ts.getDate().getTime();
        ArrayList<TimeSheet> current = readTimeSheetsFromDB( ts.getEmployee(), whichDay, whichDay );

        if (!(current.size()>0 && ts.equals( current.get(0) ))) {
            new UpdateDatabases(c).updateTimeSheet(ts);
            addToDB(ts);
            neededToBeUpdated = true;
        }
        return neededToBeUpdated;
    }
    public int  getMaxRecord() {
        SQLiteDatabase db = openDb();
        Cursor cursor = db.query(
                TABLE_NAME,                                         // The table to query
                new String[] {"MAX(" + COLUMN_NAME_RECORD + ")"},   // The columns to return from the query
                null,                                               // The columns for the where clause
                null,                                               // The values for the where clause
                null,                                               // don't group the rows
                null,                                               // don't filter by row groups
                null);                                              // The sort order

        cursor.moveToFirst();
        int maxRecord = cursor.getInt(0);
        closeDb();

        return maxRecord;
    }
    public void deleteAllData() {
        SQLiteDatabase db = openDb();

        // Delete all rows in the table
        db.delete(  TABLE_NAME,                                     // The table to query
                null,                                               // The WHERE clause
                null);                                              // Optional arguments for the WHERE clause

        db.close();
    }

}

class LocationList extends DBBaseAdapter implements BaseColumns, GD.Constants {
    static int rate = FAST;

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

    public LocationList() {
        super( GD.getAppContext() );
    }

    public ArrayList<Location> readLocationsFromDB(long startTime, SQLiteDatabase dbIn) {
        SQLiteDatabase db;

        if (dbIn == null)
            db = openDb();
        else
            db = dbIn;

        Cursor c = db.query(
                TABLE_NAME,                             // The table to query
                QUERY_STRING,                           // The columns to return from the query
                COLUMN_NAME_TIMESTAMP + ">" + String.valueOf(startTime), // The columns for the where clause
                null,                                   // The values for the where clause
                null,                                   // don't group the rows
                null,                                   // don't filter by row groups
                COLUMN_NAME_TIMESTAMP + " ASC");                                  // The sort order

        ArrayList<Location> list = new ArrayList<Location>();
        c.moveToFirst();
        int tx   = c.getColumnIndex( COLUMN_NAME_TIMESTAMP);
        int lt   = c.getColumnIndex( COLUMN_NAME_LATITUDE);
        int lg   = c.getColumnIndex( COLUMN_NAME_LONGITUDE);
        int ax   = c.getColumnIndex( COLUMN_NAME_ACCURACY);
        int sx   = c.getColumnIndex( COLUMN_NAME_SPEED);


        while (!c.isAfterLast()) {
            Location dummy = new Location("dummyProvider");
            dummy.setTime(      c.getLong(   tx ) );
            dummy.setLatitude(  c.getDouble( lt ) );
            dummy.setLongitude( c.getDouble( lg ) );
            dummy.setAccuracy(  c.getFloat(  ax ) );
            dummy.setSpeed(     c.getFloat(  sx ) );

            list.add( dummy );
            c.moveToNext();
        }

        if (dbIn == null)
            closeDb();

        return list;
    }
    public void deleteLocationsFromDB(long startTime, long endTime, SQLiteDatabase dbIn) {
        SQLiteDatabase db;

        if (dbIn == null)
            db = openDb();
        else
            db = dbIn;

        db.delete( TABLE_NAME,                                               // The table to query
                   COLUMN_NAME_TIMESTAMP + ">=" + String.valueOf(startTime) + " AND " +
                   COLUMN_NAME_TIMESTAMP + "<=" + String.valueOf(endTime),   // The WHERE clause
                   null);                                                    // Optional arguments for the WHERE clause

        if (dbIn == null)
            closeDb();

    }
    public void Update( Location locn ) {
        ContentValues values;
        float range = 0;

        SQLiteDatabase db = openDb();

        long startTime = locn.getTime() - 10 * 60 * 1000;
        ArrayList<Location> setOfLocn = readLocationsFromDB(startTime, db);

        if (setOfLocn.size() > 0) {
            double avgLat = locn.getLatitude();
            double avgLng = locn.getLongitude();
            for (Location pastLoc : setOfLocn) {
                range = Math.max(range, locn.distanceTo(pastLoc));
                avgLat += locn.getLatitude();
                avgLng += locn.getLongitude();
            }

            if (range < 75) {
                deleteLocationsFromDB(startTime, System.currentTimeMillis(), db);
                locn.setLatitude(avgLat / (setOfLocn.size() + 1));  // TODO, this averaging is not right - weights too high on the last entry
                locn.setLongitude(avgLng / (setOfLocn.size() + 1));
                locn.setSpeed(-2);

                Location firstEpoch = setOfLocn.get(0);
                if (firstEpoch.getSpeed() != -2) {  // Do I need to put the start of the stationary period in?  There are 3 possibilities:
                                                    //      FirstEpoch speed >=0:  no stationary period determined yet >> YES
                                                    //      FirstEpoch speed =-1:  stationary period determined >> YES (must replace what was deleted above)
                                                    //      FirstEpoch speed =-2:  stationary period but longer than the 20 minutes selected above >> NO
                    values = new ContentValues();
                    values.put( COLUMN_NAME_TIMESTAMP,  firstEpoch.getTime());
                    values.put( COLUMN_NAME_LATITUDE,   firstEpoch.getLatitude()  );
                    values.put( COLUMN_NAME_LONGITUDE,  firstEpoch.getLongitude() );
                    values.put( COLUMN_NAME_ACCURACY,   firstEpoch.getAccuracy()  );
                    values.put( COLUMN_NAME_SPEED,      -1.0     );

                    db.insert(TABLE_NAME,                                       // The table to query
                            null,                                               // Set to NULL so that it won't insert a blank row (if values = null)
                            values);                                            // Optional arguments for the WHERE clause

                }
            }
        }

        // Insert the record into the database
        values = new ContentValues();
        values.put( COLUMN_NAME_TIMESTAMP,  locn.getTime());
        values.put( COLUMN_NAME_LATITUDE,   locn.getLatitude()  );
        values.put( COLUMN_NAME_LONGITUDE,  locn.getLongitude() );
        values.put( COLUMN_NAME_ACCURACY,   locn.getAccuracy()  );
        values.put( COLUMN_NAME_SPEED,      locn.getSpeed()     );

        db.insert(TABLE_NAME,                                       // The table to query
                null,                                               // Set to NULL so that it won't insert a blank row (if values = null)
                values);                                            // Optional arguments for the WHERE clause

        Log.d("myDebug Locn", "Range estimate is " + range + ((range < 200) ? ":  SLOW" : ":  FAST"));
//        if (range < 200)
//            SetLocnPeriod(SLOW);
//        else
//            SetLocnPeriod(FAST);

        db.close();
    }
    static public Intent SetLocnPeriod(int periodicity) {
        Context c = GD.getAppContext();
        rate = periodicity;
        Intent startTrackingIntent = new Intent(c, LocnService.class);

        if (rate == SLOW) {
            startTrackingIntent.putExtra( LOCN_REG_INTERVAL,  (long) ( 15 * 60 * 1000) );
            startTrackingIntent.putExtra( LOCN_FAST_INTERVAL, (long) (  1 * 60 * 1000) );
        }

        if (rate == FAST) {
            startTrackingIntent.putExtra( LOCN_REG_INTERVAL,  (long) (1.0 * 60 * 1000) );
            startTrackingIntent.putExtra( LOCN_FAST_INTERVAL, (long) (0.5 * 60 * 1000) );
        }

        startTrackingIntent.putExtra( LOCN_DURATION,  (long) (2 * 24 * 60 * 60 * 1000) );
        startTrackingIntent.putExtra( LOCN_PRIORITY, LocationRequest.PRIORITY_HIGH_ACCURACY);
        c.startService(startTrackingIntent);

        return startTrackingIntent;
    }
}

class ABRecordList extends DBBaseAdapter implements BaseColumns {
    Context c;

    // <editor-fold desc="Column definitions and Constants"
    public static final String TABLE_NAME                   = "asbuilt";
    public static final String COLUMN_NAME_RECORD           = "record";
    public static final String COLUMN_NAME_PROJECTID        = "projectid";
    public static final String COLUMN_NAME_TEST             = "test";
    public static final String COLUMN_NAME_DONE             = "done";
    public static final String COLUMN_NAME_MEASUREMENT      = "measurement";
    public static final String COLUMN_NAME_AUTHOR           = "author";
    public static final String COLUMN_NAME_TIMESTAMP        = "timestamp";

    public static String[] QUERY_STRING = new String[] {
            COLUMN_NAME_RECORD,
            COLUMN_NAME_PROJECTID,
            COLUMN_NAME_TEST,
            COLUMN_NAME_DONE,
            COLUMN_NAME_MEASUREMENT,
            COLUMN_NAME_AUTHOR,
            COLUMN_NAME_TIMESTAMP };
    // </editor-fold>

    public ABRecordList() {
        super(GD.getAppContext());
        c = GD.getAppContext();
    }

    public ArrayList<AsBuilt> readAsBuiltFromDB(int projectID) {
        SQLiteDatabase db = openDb();
        Cursor cursor = db.query(
                TABLE_NAME,                                 // The table to query
                QUERY_STRING,                               // The columns to return from the query
                COLUMN_NAME_PROJECTID + "=" + projectID,    // The columns for the where clause
                null,                                       // The values for the where clause
                null,                                       // don't group the rows
                null,                                       // don't filter by row groups
                COLUMN_NAME_TEST + " ASC");                 // The sort order

        ArrayList<AsBuilt> list = new ArrayList<AsBuilt>();
        cursor.moveToFirst();
        int rx   = cursor.getColumnIndex( COLUMN_NAME_RECORD);
        int px   = cursor.getColumnIndex( COLUMN_NAME_PROJECTID);
        int tx   = cursor.getColumnIndex( COLUMN_NAME_TEST);
        int dx   = cursor.getColumnIndex( COLUMN_NAME_DONE);
        int mx   = cursor.getColumnIndex( COLUMN_NAME_MEASUREMENT);
        int ax   = cursor.getColumnIndex( COLUMN_NAME_AUTHOR);
        int ts   = cursor.getColumnIndex( COLUMN_NAME_TIMESTAMP);

        while (!cursor.isAfterLast()) {
            list.add( new AsBuilt( cursor.getInt(rx), cursor.getInt(px), cursor.getInt(tx), GD.doneCode.values()[cursor.getInt(dx)],
                                   cursor.getInt(mx), cursor.getInt(ax), cursor.getLong(ts)) );
            cursor.moveToNext();
        }

        closeDb();

        return list;
    }
    public void addToDB(AsBuilt asBuilt) {
        Log.d("myDebug", "Entering addToDB " + System.currentTimeMillis());
        SQLiteDatabase db = openDb();

        // Delete the record if it exists
        db.delete(  TABLE_NAME,                                         // The table to query
                    COLUMN_NAME_PROJECTID + "="  + asBuilt.getProjectid() + " AND " +  // The WHERE clause
                    COLUMN_NAME_TEST      + "="  + asBuilt.getTest(),
                    null);                                              // Optional arguments for the WHERE clause

        // Insert the record into the database
        ContentValues values = new ContentValues();
        values.put( COLUMN_NAME_RECORD,      asBuilt.getRecord()                     );
        values.put( COLUMN_NAME_PROJECTID,   asBuilt.getProjectid()                  );
        values.put( COLUMN_NAME_TEST,        asBuilt.getTest()                       );
        values.put( COLUMN_NAME_DONE,        asBuilt.getDone().ii                    );
        values.put( COLUMN_NAME_MEASUREMENT, asBuilt.getMeasurement()                );
        values.put( COLUMN_NAME_AUTHOR,      Integer.valueOf(GC.myEmployeeNumber)    );
        values.put( COLUMN_NAME_TIMESTAMP,   asBuilt.getTimestamp()                  );

        db.insert(  TABLE_NAME,                                         // The table to query
                    null,                                               // Set to NULL so that it won't insert a blank row (if values = null)
                    values);                                            // Optional arguments for the WHERE clause

        db.close();
    }
    public void updateDB(AsBuilt asBuilt) {
        Log.d("myDebug", "Entering addToDB " + System.currentTimeMillis());
        SQLiteDatabase db = openDb();

        // See if the record if it exists
        Cursor cursor = db.query(
                TABLE_NAME,                                 // The table to query
                QUERY_STRING,                               // The columns to return from the query
                COLUMN_NAME_PROJECTID   + "=" + asBuilt.getProjectid() + " AND " +  // The WHERE clause
                COLUMN_NAME_TEST        + "=" + asBuilt.getTest()      + " AND " +
                COLUMN_NAME_DONE        + "=" + asBuilt.getDone().ii   + " AND " +
                COLUMN_NAME_MEASUREMENT + "=" + asBuilt.getMeasurement(),
                null,                                       // don't group the rows
                null,                                       // don't filter by row groups
                null,                                       // don't filter by row groups
                null);                                      // The sort order

        if (cursor.getCount() != 0)
            return;

        db.delete(  TABLE_NAME,                                         // The table to query
                    COLUMN_NAME_PROJECTID + "="  + asBuilt.getProjectid() + " AND " +  // The WHERE clause
                    COLUMN_NAME_TEST      + "="  + asBuilt.getTest(),
                    null);                                              // Optional arguments for the WHERE clause

        // Insert the record into the database
        ContentValues values = new ContentValues();
        values.put( COLUMN_NAME_RECORD,      asBuilt.getRecord()                     );
        values.put( COLUMN_NAME_PROJECTID,   asBuilt.getProjectid()                  );
        values.put( COLUMN_NAME_TEST,        asBuilt.getTest()                       );
        values.put( COLUMN_NAME_DONE,        asBuilt.getDone().ii                    );
        values.put( COLUMN_NAME_MEASUREMENT, asBuilt.getMeasurement()                );
        values.put( COLUMN_NAME_AUTHOR,      Integer.valueOf(GC.myEmployeeNumber)    );
        values.put( COLUMN_NAME_TIMESTAMP,   asBuilt.getTimestamp()                  );

        db.insert(  TABLE_NAME,                                         // The table to query
                    null,                                               // Set to NULL so that it won't insert a blank row (if values = null)
                    values);                                            // Optional arguments for the WHERE clause

        db.close();
        new UpdateDatabases(c).updateAsBuilt(asBuilt);
    }
    private void update(AsBuilt asBuilt) {
        Log.d("myDebug", "Entering update " + System.currentTimeMillis());
        addToDB(asBuilt);
        new UpdateDatabases(c).updateAsBuilt(asBuilt);
    }
    public void removeRecord(AsBuilt asBuilt) {
        SQLiteDatabase db = openDb();

        // Delete the record if it exists
        db.delete(  TABLE_NAME,                                                 // The table to query
                    COLUMN_NAME_PROJECTID  + "=" + asBuilt.getProjectid() + " AND " +
                    COLUMN_NAME_TEST       + "=" + asBuilt.getTest(),
                    null);                                                      // Optional arguments for the WHERE clause

        db.close();
    }
    public void verifyAsBuiltsExist(Project p) {
        Log.d("myDebug", "Entering verifyAsBuiltsExist " + System.currentTimeMillis());
        if (GD.abRequired == null)
            return;

        int[] checkTests = Arrays.copyOf( GD.abRequired, GD.abRequired.length );  // all required tests are equal to 1

        SQLiteDatabase db = openDb();
        Cursor cursor = db.query(
                TABLE_NAME,                                 // The table to query
                QUERY_STRING,                               // The columns to return from the query
                COLUMN_NAME_PROJECTID + "=" + p.getId(),    // The columns for the where clause
                null,                                       // The values for the where clause
                null,                                       // don't group the rows
                null,                                       // don't filter by row groups
                null);                                      // The sort order

        cursor.moveToFirst();
        int px   = cursor.getColumnIndex( COLUMN_NAME_PROJECTID);
        int tx   = cursor.getColumnIndex( COLUMN_NAME_TEST);

        while (!cursor.isAfterLast()) {                     // Check each AsBuilt record in db
            int test = cursor.getInt(tx);
            if ( test < checkTests.length )
                checkTests[ test ] = 0;
            cursor.moveToNext();
        }

        for (int ii = 0; ii < checkTests.length; ii++ )     // Check each test
            if (checkTests[ii] == 1)                        //   to see if it has not been included and that it is required
                update( new AsBuilt( p.getId(), ii ) );     //   if so, add this test in the database

        closeDb();

    }
    public int  getMaxRecord() {
        SQLiteDatabase db = openDb();
        Cursor cursor = db.query(
                TABLE_NAME,                                         // The table to query
                new String[] {"MAX(" + COLUMN_NAME_RECORD + ")"},   // The columns to return from the query
                null,                                               // The columns for the where clause
                null,                                               // The values for the where clause
                null,                                               // don't group the rows
                null,                                               // don't filter by row groups
                null);                                              // The sort order

        cursor.moveToFirst();
        int maxRecord = cursor.getInt(0);
        closeDb();

        return maxRecord;
    }
    public void deleteAllData() {
        SQLiteDatabase db = openDb();

        // Delete all rows in the table
        db.delete(  TABLE_NAME,                                     // The table to query
                null,                                               // The WHERE clause
                null);                                              // Optional arguments for the WHERE clause

        db.close();
    }

}

class ABTestList extends DBBaseAdapter implements BaseColumns {

    // <editor-fold desc="Column definitions and Constants"
    public static final String TABLE_NAME                   = "asbuilt_tests";
    public static final String COLUMN_NAME_TEST             = "test";
    public static final String COLUMN_NAME_STAGE            = "stage";
    public static final String COLUMN_NAME_TITLE            = "title";
    public static final String COLUMN_NAME_DESCRIPTION      = "description";
    public static final String COLUMN_NAME_UNITS            = "units";
    public static final String COLUMN_NAME_THRESH_A         = "threshA";
    public static final String COLUMN_NAME_OPERATOR         = "operator";
    public static final String COLUMN_NAME_THRESH_B         = "threshB";
    public static final String COLUMN_NAME_DEPRECATED       = "deprecated";
    public static final int    numOfColumns                 = 9;

    public static String[] QUERY_STRING = new String[] {
            COLUMN_NAME_TEST,
            COLUMN_NAME_STAGE,
            COLUMN_NAME_TITLE,
            COLUMN_NAME_DESCRIPTION,
            COLUMN_NAME_UNITS,
            COLUMN_NAME_THRESH_A,
            COLUMN_NAME_OPERATOR,
            COLUMN_NAME_THRESH_B,
            COLUMN_NAME_DEPRECATED };

    enum abDesc {
        STAGE           (0),
        TITLE           (1),
        DESCRIPTION     (2),
        UNITS           (3),
        THRESH_A        (4),
        OPERATOR        (5),
        THRESH_B        (6),
        DEPRECATED      (7);

        int ii;
        private abDesc( int index ) {
            this.ii = index;
        }
    }
    // </editor-fold>

    public ABTestList() {
        super( GD.getAppContext() );
    }

    public String[][] readTestsFromDB() {
        SQLiteDatabase db = openDb();
        Cursor cursor = db.query(
                TABLE_NAME,                                 // The table to query
                QUERY_STRING,                               // The columns to return from the query
                null,                                       // The columns for the where clause
                null,                                       // The values for the where clause
                null,                                       // don't group the rows
                null,                                       // don't filter by row groups
                COLUMN_NAME_TEST + " ASC");                 // The sort order

        String[][] list = new String[ cursor.getCount() ][ numOfColumns-1 ];
        cursor.moveToFirst();

        int ii = 0;
        while (!cursor.isAfterLast()) {
            int testNum                     = cursor.getInt(    cursor.getColumnIndex( COLUMN_NAME_TEST));
            list[ii][abDesc.STAGE.ii]       = cursor.getString( cursor.getColumnIndex( COLUMN_NAME_STAGE)       );
            list[ii][abDesc.TITLE.ii]       = cursor.getString( cursor.getColumnIndex( COLUMN_NAME_TITLE)       );
            list[ii][abDesc.DESCRIPTION.ii] = cursor.getString( cursor.getColumnIndex( COLUMN_NAME_DESCRIPTION) );
            list[ii][abDesc.UNITS.ii]       = cursor.getString( cursor.getColumnIndex( COLUMN_NAME_UNITS)       );
            list[ii][abDesc.THRESH_A.ii]    = cursor.getString( cursor.getColumnIndex( COLUMN_NAME_THRESH_A)    );
            list[ii][abDesc.OPERATOR.ii]    = cursor.getString( cursor.getColumnIndex( COLUMN_NAME_OPERATOR)    );
            list[ii][abDesc.THRESH_B.ii]    = cursor.getString( cursor.getColumnIndex( COLUMN_NAME_THRESH_B)    );
            list[ii][abDesc.DEPRECATED.ii]  = cursor.getString( cursor.getColumnIndex( COLUMN_NAME_DEPRECATED)  );
            cursor.moveToNext();

                    // Test to verify test index matches array index.
                    // If it doesn't, it means we have a corrupted list and we should return null.
            try {
                if (testNum - 1 != ii++)
                    throw new DataFormatException("Indexes don't match test number.");
            } catch (NumberFormatException e) {
                list = null;
                cursor.close();
            } catch (DataFormatException e) {
                list = null;
                cursor.close();
            }
        }

        closeDb();

        return list;
    }
    public void replaceListInDB(ArrayList<String[]> newOrUpdatedList, int newABVersion) {
        Log.d("myDebug", "Entering replaceListInDB " + System.currentTimeMillis());
        if (newABVersion == GC.localABTestsVersion)                     // there was no update
            return;

        SQLiteDatabase db = openDb();

        // Delete all rows in the table
        db.delete(  TABLE_NAME,                                         // The table to query
                    null,                                               // The WHERE clause
                    null);                                              // Optional arguments for the WHERE clause

        // Insert all the records into the database
        for (String[] abTest : newOrUpdatedList) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME_TEST,        Integer.valueOf(abTest[0]) );
            values.put(COLUMN_NAME_STAGE,                       abTest[1]  );
            values.put(COLUMN_NAME_TITLE,                       abTest[2]  );
            values.put(COLUMN_NAME_DESCRIPTION,                 abTest[3]  );
            values.put(COLUMN_NAME_UNITS,                       abTest[4]  );
            values.put(COLUMN_NAME_THRESH_A,                    abTest[5]  );
            values.put(COLUMN_NAME_OPERATOR,                    abTest[6]  );
            values.put(COLUMN_NAME_THRESH_B,                    abTest[7]  );
            values.put(COLUMN_NAME_DEPRECATED,                  abTest[8]  );

            db.insert(TABLE_NAME,                                     // The table to query
                      null,                                           // Set to NULL so that it won't insert a blank row (if values = null)
                      values);                                        // Optional arguments for the WHERE clause
        }

        db.close();

        GC.localABTestsVersion = newABVersion;
    }
    public int  getABTestsVersion() {
        return GC.localABTestsVersion;
    }
}

