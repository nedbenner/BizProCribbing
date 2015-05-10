package ca.nbenner.bizprocribbing;

import android.app.Application;
import android.content.Context;

public class GD extends Application {               // Global Data
    private static Context      context;
    public  static String[][]   abTests;
    public  static int[]        abRequired;
    public  static Project      projectToEdit;

    @Override public void onCreate() {
        super.onCreate();
        GD.context = getApplicationContext();
        getAbMeasureTitles();
    }

    public static Context getAppContext() {
        return GD.context;
    }

    public static void getAbMeasureTitles() {
        abTests = new ABTestList().readTestsFromDB();
        if (abTests == null)
            abRequired = null;
        else {
            abRequired = new int[abTests.length];
            for (int ii = 0; ii < abTests.length; ii++)
                abRequired[ii] = abTests[ii][ABTestList.abDesc.DEPRECATED.ii].equals("No") ? 1 : 0;
        }
    }

    enum resultType {               // used in evaluating whether mesaurements of AsBuilts are Pass or Fail
        NO_TEST,
        BETWEEN,
        OUTSIDE,
        LESS_THAN,
        GREATER_THAN;
    }
    enum doneCode {                 // outcome of tests
        NOT_DONE        (0),
        DONE_NO_EVAL    (1),
        FAIL            (2),
        PASS            (3);

        int ii;
        private doneCode(int index) {
            this.ii = index;
        }
    }
    enum editProgress {             // current state of editing project data when exiting ActivityEditProject
        IN_PROGRESS     (0),
        COMPLETED       (1),
        GETTING_PID     (2);

        int ii;
        private editProgress(int index) {
            this.ii = index;
        }
    }
    enum codeIntent {               // current state of editing project data when exiting ActivityEditProject
        NOT_USED        (0),
        RECORD_PICTURE  (1),
        GET_PROJECT_ID  (2),
        EDIT_RQ_CURRENT (3),        //  edit project at selected marker
        EDIT_RQ_SHIFTED (4),        //  marker has been shifted in location
        EDIT_RQ_NEW     (5);        //  a new project is requested

        int ii;
        private codeIntent(int index) {
            this.ii = index;
        }
    }
    enum listColumns {              // the columns used in the list of all projects
        NONE            (0),
        ADDRESSES       (1),
        LATITUDE        (2),
        LONGITUDE       (3),
        STATUS          (4),
        CUSTOMER        (5),
        CHANGE          (6);        // the latest timestamp on the project

        int ii;
        private listColumns(int index) {
            this.ii = index;
        }
    }

    public interface Constants {
        public final static int     SLOW                = 0;
        public final static int     MEDIUM              = 1;
        public final static int     FAST                = 2;

        public final static String  LOCN_DURATION       = "duration";
        public final static String  LOCN_REG_INTERVAL   = "reg_interval";
        public final static String  LOCN_FAST_INTERVAL  = "fast_interval";
        public final static String  LOCN_PRIORITY       = "priority";

        public final static int     CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
        public final static int     REQUEST_RESOLVE_ERROR = 1001;           // Request code to use when launching the resolution activity
        public final static String  DIALOG_ERROR        = "dialog_error";   // Unique tag for the error dialog fragment
    }

}
