package ca.nbenner.bizprocribbing;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBBaseAdapter {
    // <editor-fold desc="Global Variables and Constants"
    private   static final  String          TAG                 = "DBBaseAdapter";
    protected static final  String          DATABASE_NAME       = "db.cribbing";
    protected static final  int             DATABASE_VERSION    = 64;
    protected static        Context         mContext;
    protected static        DatabaseHelper  mDbHelper;
    // </editor-fold>

    public DBBaseAdapter(Context context) {
        mContext = context.getApplicationContext();
    }

    public SQLiteDatabase openDb() {
        if (mDbHelper == null) {
            mDbHelper = new DatabaseHelper(mContext);
        }
        return mDbHelper.getWritableDatabase();
    }

    public void closeDb() {
        mDbHelper.close();
    }

    protected static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS "
                    + GC.UsedList.TABLE_NAME                + " ("
                    + GC.UsedList._ID                       + " INTEGER PRIMARY KEY,"
                    + GC.UsedList.COLUMN_NAME_PROJECT_ID    + " INTEGER,"
                    + GC.UsedList.COLUMN_NAME_RECORD        + " FLOAT"
                    + ");");

            db.execSQL("CREATE TABLE IF NOT EXISTS "
                    + LocationList.TABLE_NAME              + " ("
                    + LocationList._ID                     + " INTEGER PRIMARY KEY,"
                    + LocationList.COLUMN_NAME_TIMESTAMP   + " BIGINT,"
                    + LocationList.COLUMN_NAME_LATITUDE    + " DOUBLE,"
                    + LocationList.COLUMN_NAME_LONGITUDE   + " DOUBLE,"
                    + LocationList.COLUMN_NAME_ACCURACY    + " FLOAT,"
                    + LocationList.COLUMN_NAME_SPEED       + " FLOAT"
                    + ");");

            db.execSQL("CREATE TABLE IF NOT EXISTS "
                    + ProjectList.TABLE_NAME                + " ("
                    + ProjectList.COLUMN_NAME_RECORD        + " INTEGER,"
                    + ProjectList.COLUMN_NAME_ID            + " INTEGER,"
                    + ProjectList.COLUMN_NAME_LATITUDE      + " DOUBLE,"
                    + ProjectList.COLUMN_NAME_LONGITUDE     + " DOUBLE,"
                    + ProjectList.COLUMN_NAME_ADDRESS       + " VARCHAR(255),"
                    + ProjectList.COLUMN_NAME_STATUS        + " VARCHAR(31),"
                    + ProjectList.COLUMN_NAME_CUSTOMER      + " VARCHAR(255),"
                    + ProjectList.COLUMN_NAME_TIMESTAMP     + " BIGINT"
                    + ");");

            String create_ts = "CREATE TABLE IF NOT EXISTS "
                    + TimeSheetList.TABLE_NAME                      + " ("
                    + TimeSheetList.COLUMN_NAME_RECORD              + " INTEGER,"
                    + TimeSheetList.COLUMN_NAME_DATE                + " BIGINT,"
                    + TimeSheetList.COLUMN_NAME_EMPLOYEE            + " VARCHAR(255)";
            for (int i = 0; i <= ActivityMain.maxNumOfProjects; i++) {
                create_ts = create_ts                               + ", "
                    + TimeSheetList.COLUMN_NAME_PROJECT         + i +" INTEGER,"
                    + TimeSheetList.COLUMN_NAME_PROJECT_START   + i +" INTEGER,"
                    + TimeSheetList.COLUMN_NAME_PROJECT_END     + i +" INTEGER";
            }
            create_ts = create_ts + ");";

            db.execSQL("CREATE TABLE IF NOT EXISTS "
                    + ABRecordList.TABLE_NAME                    + " ("
                    + ABRecordList.COLUMN_NAME_RECORD            + " INTEGER,"
                    + ABRecordList.COLUMN_NAME_PROJECTID         + " INTEGER,"
                    + ABRecordList.COLUMN_NAME_TEST              + " INTEGER,"
                    + ABRecordList.COLUMN_NAME_DONE              + " INTEGER,"
                    + ABRecordList.COLUMN_NAME_MEASUREMENT       + " INTEGER,"
                    + ABRecordList.COLUMN_NAME_AUTHOR            + " INTEGER,"
                    + ABRecordList.COLUMN_NAME_TIMESTAMP         + " BIGINT"
                    + ");");

            db.execSQL("CREATE TABLE IF NOT EXISTS "
                    + ABTestList.TABLE_NAME                    + " ("
                    + ABTestList.COLUMN_NAME_TEST              + " INTEGER,"
                    + ABTestList.COLUMN_NAME_STAGE             + " VARCHAR(31),"
                    + ABTestList.COLUMN_NAME_TITLE             + " VARCHAR(255),"
                    + ABTestList.COLUMN_NAME_DESCRIPTION       + " VARCHAR(2000),"
                    + ABTestList.COLUMN_NAME_UNITS             + " VARCHAR(31),"
                    + ABTestList.COLUMN_NAME_THRESH_A          + " VARCHAR(31),"
                    + ABTestList.COLUMN_NAME_OPERATOR          + " VARCHAR(31),"
                    + ABTestList.COLUMN_NAME_THRESH_B          + " VARCHAR(31),"
                    + ABTestList.COLUMN_NAME_DEPRECATED        + " VARCHAR(31)"
                    + ");");

            db.execSQL( create_ts );
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " +
                    newVersion + ", which will destroy all old data, except locations");
            db.execSQL("DROP TABLE IF EXISTS " + GC.UsedList.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ProjectList.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + TimeSheetList.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ABRecordList.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ABTestList.TABLE_NAME);
            onCreate(db);
        }
    }
}