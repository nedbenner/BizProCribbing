package ca.nbenner.bizprocribbing;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TimeSheet {

    private String      date, employee;
    private int         record;
    private int[]       projectID, startTime, endTime;  // where the zeroth element corresponds to lunch (i.e. unpaid and ID=0)
    private static int  maxNumOfProjects = MainActivity.maxNumOfProjects;

    public TimeSheet(int record, String date, String employee, int[] args) {

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
    public static String whatDay( int daysAgo ) {
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, -daysAgo);

        return (new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA)).format(t.getTime());
    }
    public static int maxRecord(List<TimeSheet> listIn) {
        if (listIn == null) return 0;

        int maxRecord = 0;
        for (TimeSheet t : listIn)
            maxRecord = Math.max( maxRecord, t.getRecord() );

        return maxRecord;
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
        int denom = 100;            // this is a fixed number to add the flag of whether the way point is starting or ending
        float[] timeWayPoints = new float[2 * (maxNumOfProjects+1) ];

        timeWayPoints[0]         = startTime[0] + 0.5f - 10f/denom;       //  lunch time is a big flag to make sure it is not billable
        timeWayPoints[1]         = endTime[0]   + 0.5f + 10f/denom;       //  lunch time is a big flag to make sure it is not billable

        for (int j = 1; j <= maxNumOfProjects; j++) {
            if (startTime[j] < 0 || endTime[j] < 0) {
                timeWayPoints[j * 2 + 0] = 0.5f;                            //  time is not valid until both start and end times are set
                timeWayPoints[j * 2 + 1] = 0.5f;
            } else {
                timeWayPoints[j * 2 + 0] = startTime[j] + 0.5f + 1f / denom;    //  project time is a small flag to show it is billable
                timeWayPoints[j * 2 + 1] = endTime[j] + 0.5f - 1f / denom;      //  project time is a small flag to show it is billable
            }
        }

        int accumTime = 0, working = 0, lastTime = 0;
        Arrays.sort( timeWayPoints );
        for (float twp : timeWayPoints) {
            if (working > 0)
                accumTime += twp - lastTime;
            working += (twp % 1 - 0.5) * denom;
            lastTime = (int) twp;
        }

        return accumTime;
    }
    public String getDate() {
        return date;
    }
    public String createURLString(String employeeNumber) {
        String command = null;
        try {
            command =
                    "Date="           + URLEncoder.encode(date,                         "UTF-8") +
                    "&Employee="       + URLEncoder.encode(employeeNumber,               "UTF-8") +
                    "&Lunch_Start="    + URLEncoder.encode(String.valueOf(startTime[0]), "UTF-8") +
                    "&Lunch_End="      + URLEncoder.encode(String.valueOf(endTime[0]),   "UTF-8") +
                    "&Project1="       + URLEncoder.encode(String.valueOf(projectID[1]), "UTF-8") +
                    "&Project1_Start=" + URLEncoder.encode(String.valueOf(startTime[1]), "UTF-8") +
                    "&Project1_End="   + URLEncoder.encode(String.valueOf(endTime[1]),   "UTF-8") +
                    "&Project2="       + URLEncoder.encode(String.valueOf(projectID[2]), "UTF-8") +
                    "&Project2_Start=" + URLEncoder.encode(String.valueOf(startTime[2]), "UTF-8") +
                    "&Project2_End="   + URLEncoder.encode(String.valueOf(endTime[2]),   "UTF-8") +
                    "&Project3="       + URLEncoder.encode(String.valueOf(projectID[3]), "UTF-8") +
                    "&Project3_Start=" + URLEncoder.encode(String.valueOf(startTime[3]), "UTF-8") +
                    "&Project3_End="   + URLEncoder.encode(String.valueOf(endTime[3]),   "UTF-8") +
                    "&Author="         + URLEncoder.encode(employeeNumber,               "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return command;
    }

}
