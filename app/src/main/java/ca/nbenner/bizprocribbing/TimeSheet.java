package ca.nbenner.bizprocribbing;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

public class TimeSheet implements Parcelable {

    private String      employee;
    private int         record;
    private Date        date;
    private int[]       projectID, startTime, endTime;  // where the zeroth element corresponds to lunch (i.e. unpaid and ID=0)
    private static int  maxNumOfProjects = ActivityMain.maxNumOfProjects;
    private static SimpleDateFormat sdf = new  SimpleDateFormat("yyyy-MM-dd", Locale.CANADA);

    public TimeSheet(int record, Date date, String employee, int[] args) {

        this.record         = record;
        this.date 	        = date;
        this.employee       = employee;
        this.projectID      = new int[maxNumOfProjects+1];
        this.startTime      = new int[maxNumOfProjects+1];
        this.endTime        = new int[maxNumOfProjects+1];

        for (int i = 0; i <= maxNumOfProjects; i++) {
            projectID[i] = i * 3 + 0 < args.length ? args[i*3+0] : -1;
            startTime[i] = i * 3 + 1 < args.length ? args[i*3+1] : -1;
            endTime[i]   = i * 3 + 2 < args.length ? args[i*3+2] : -1;
        }

    }

    private static int[] defaultArgs = {0, 12 * 60, 12 * 60 + 30, -1, -1, -1, -1, -1, -1};
    public TimeSheet(Date date, String employee) {
        this( 0, date, employee, defaultArgs);
    }
    public TimeSheet(long date, String employee) {
        this( 0, new Date(date), employee, defaultArgs);
    }

    public TimeSheet(int record, String date, String employee, int[] args) {
        this(   record, sdf.parse(date, new ParsePosition(0)), employee, args );
    }

    //  Copy Constructor
    public static TimeSheet newInstance(TimeSheet toCopy) {
        int[] args = new int[(maxNumOfProjects+1) * 3];
        for (int i = 0; i <= maxNumOfProjects; i++) {
            args[i*3 + 0] = toCopy.projectID[i];
            args[i*3 + 1] = toCopy.startTime[i];
            args[i*3 + 2] = toCopy.endTime[i];
        }
        return new TimeSheet( toCopy.getRecord(), toCopy.getDate(), toCopy.getEmployee(), args);
    }


    public static String whatDayS(int daysAgo) {
        Calendar t = GregorianCalendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, -daysAgo);

        return sdf.format(t.getTime());
    }
    public static Date whatDayD(int daysAgo) {
        Calendar t = GregorianCalendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, -daysAgo);

        return t.getTime();
    }
    public static long whatDayL(int daysAgo) {
        Calendar t = GregorianCalendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, -daysAgo);
        t.set(Calendar.HOUR_OF_DAY, 0);
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);

        return t.getTime().getTime();
    }
    public static int maxRecord(List<TimeSheet> listIn) {
        if (listIn == null) return 0;

        int maxRecord = 0;
        for (TimeSheet t : listIn)
            maxRecord = Math.max( maxRecord, t.getRecord() );

        return maxRecord;
    }

    @Override public boolean equals( Object other ) {
        if (!(other instanceof TimeSheet)) {
            return false;
        }

        TimeSheet that = (TimeSheet) other;

        // Custom equality check here.
        return     this.date    .equals(that.date)
                && this.employee.equals(that.employee)
                && Arrays.equals(this.projectID, that.projectID)
                && Arrays.equals(this.startTime, that.startTime)
                && Arrays.equals(this.endTime,   that.endTime);
    }
    public int getRecord() {
        return record;
    }
    public String getEmployee() {
        return employee;
    }
    public int getStartTime(int i) {
        return startTime[i];
    }
    public int getEndTime(int i) {
        return endTime[i];
    }
    public int[] getProjectList() {
        return projectID;
    }
    public int getProjectIndex(int pID) {
        for (int x = 0; x < maxNumOfProjects; x++)
            if (projectID[x] == pID) return x;
        return -1;
    }
    public int getProjectID(int pIndex) {
        return projectID[pIndex];
    }
    public void setProject(int i, int pID) {
        projectID[i] = pID;
    }
    public void setTime(int i, int s, int e) {
        startTime[i] = s;
        endTime[i] = e;
    }
    public void setProjectTime(int i, int pID, int s, int e) {
        projectID[i] = pID;
        startTime[i] = s;
        endTime[i] = e;
    }
    public int billableTime() {
        float denom = 100;            // this is a fixed number to add the flag of whether the way point is starting or ending
        float[] timeWayPoints = new float[2 * (maxNumOfProjects+1) ];

        timeWayPoints[0]         = startTime[0] + 0.5f - 10f/denom;       //  lunch time is a big flag to make sure it is not billable
        timeWayPoints[1]         = endTime[0]   + 0.5f + 10f/denom;       //  lunch time is a big flag to make sure it is not billable

        for (int j = 1; j <= maxNumOfProjects; j++) {
            if (startTime[j] < 0 || endTime[j] < 0) {                     //  time is not valid until both start and end times are set
                timeWayPoints[j * 2 + 0] = 0.5f;
                timeWayPoints[j * 2 + 1] = 0.5f;
            } else {
                timeWayPoints[j * 2 + 0] = startTime[j] + 0.5f + 1f / denom;    //  project time is a small flag to show it is billable
                timeWayPoints[j * 2 + 1] = endTime[j] + 0.5f - 1f / denom;      //  project time is a small flag to show it is billable
            }
        }

        int accumTime = 0, lastTime = 0;
        float working = 0;
        Arrays.sort( timeWayPoints );
        for (float twp : timeWayPoints) {
            if (working > 0)
                accumTime += (int) twp - lastTime;
            working += (twp % 1f - 0.5f) * denom;
            lastTime = (int) twp;
        }

        return accumTime;
    }
    public int compareDate(Object dateIn) {
        Date testDate = new Date(0);

        if (dateIn instanceof String) {
            try {
                testDate = sdf.parse((String) dateIn);
            } catch (ParseException e) {
                return -2;          // FAIL
            }
        }

        if (dateIn instanceof Long) {
            testDate = new Date((Long) dateIn);
        }

        return this.date.compareTo( testDate );
    }
    public Date getDate() {
        return date;
    }
    public String createURLString(String employeeNumber) {

        String values = "'" + sdf.format( date )                + "',"
                            + employeeNumber                    + ","
                            + startTime[0] + "," + endTime[0]   + ","
                            + projectID[1] + "," + startTime[1] + "," + endTime[1] + ","
                            + projectID[2] + "," + startTime[2] + "," + endTime[2] + ","
                            + projectID[3] + "," + startTime[3] + "," + endTime[3] + ","
                            + GC.myEmployeeNumber               + ";";

        return values;
    }
    //
    //    Methods to implement Parcelable
    //
    public int describeContents() {
        return 0;
    }
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(record);
        out.writeLong(date.getTime());
        out.writeInt(employee.length());
        out.writeCharArray(employee.toCharArray());

        int[] args = new int[ maxNumOfProjects * 3 ];
        for (int ii = 0; ii < maxNumOfProjects; ii++) {
            args[ii*3 + 0] = projectID[ii];
            args[ii*3 + 1] = startTime[ii];
            args[ii*3 + 2] = endTime  [ii];
        }
        out.writeIntArray( args );
    }
    public static final Parcelable.Creator<TimeSheet> CREATOR
            = new Parcelable.Creator<TimeSheet>() {
        public TimeSheet createFromParcel(Parcel in) {
            return new TimeSheet(in);
        }
        public TimeSheet[] newArray(int size) {
            return new TimeSheet[size];
        }

    };
    private TimeSheet(Parcel in) {

        new TimeSheet(  in.readInt(),                       // record
                        new Date( in.readLong() ),          // date
                        new String(in.createCharArray()),   // employee
                        in.createIntArray() );              // args
    }

}
