package ca.nbenner.bizprocribbing;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBBaseAdapter {
    // <editor-fold desc="Global Variables and Constants"
    private   static final  String          TAG                 = "DBBaseAdapter";
    protected static final  String          DATABASE_NAME       = "db.cribbing";
    protected static final  int             DATABASE_VERSION    = 17;
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
                    + LocationFixes.TABLE_NAME              + " ("
                    + LocationFixes._ID                     + " INTEGER PRIMARY KEY,"
                    + LocationFixes.COLUMN_NAME_TIMESTAMP   + " BIGINT,"
                    + LocationFixes.COLUMN_NAME_LATITUDE    + " DOUBLE,"
                    + LocationFixes.COLUMN_NAME_LONGITUDE   + " DOUBLE,"
                    + LocationFixes.COLUMN_NAME_ACCURACY    + " FLOAT,"
                    + LocationFixes.COLUMN_NAME_SPEED       + " FLOAT"
                    + ");");

            db.execSQL("CREATE TABLE IF NOT EXISTS "
                    + ProjectList.TABLE_NAME + " ("
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
                    + TimeSheetList.COLUMN_NAME_DATE                + " INTEGER,"
                    + TimeSheetList.COLUMN_NAME_EMPLOYEE            + " VARCHAR(31)";
            for (int i = 0; i <= MainActivity.maxNumOfProjects; i++) {
                create_ts = create_ts                               + ", "
                    + TimeSheetList.COLUMN_NAME_PROJECT         + i +" INTEGER,"
                    + TimeSheetList.COLUMN_NAME_PROJECT_START   + i +" INTEGER,"
                    + TimeSheetList.COLUMN_NAME_PROJECT_END     + i +" INTEGER";
            }
            create_ts = create_ts + ");";

            db.execSQL( create_ts );
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " +
                    newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + GC.UsedList.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ProjectList.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + TimeSheetList.TABLE_NAME);
            onCreate(db);
        }
    }
}