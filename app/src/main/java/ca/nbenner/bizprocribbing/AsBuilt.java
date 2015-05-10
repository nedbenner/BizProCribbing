package ca.nbenner.bizprocribbing;

import android.os.Parcel;
import android.os.Parcelable;

public class AsBuilt implements Parcelable {

    private int     record, projectid, test, measurement, author;
    private GD.doneCode done;
    private long    timestamp;

    public AsBuilt(int record, int projectid, int test, GD.doneCode done, int measurement, int author, long timestamp) {
        this.record         = record;
        this.projectid      = projectid;
        this.test           = test;
        this.done           = done;             // 0 = not done, 1 = not tested, 2 = fail, 3 = pass
        this.measurement    = measurement;
        this.author         = author;
        this.timestamp      = timestamp;
    }
    public AsBuilt(int record, int projectid, int test, GD.doneCode done, int measurement, int author) {
        this(record, projectid, test, done, measurement, author, System.currentTimeMillis());
    }
    public AsBuilt(int projectid, int test) {
        this(0, projectid, test, GD.doneCode.NOT_DONE, 0, 0, 0);
    }

    public int  getRecord() {
        return record;
    }
    public void setRecord(int record) {
        this.record = record;
    }
    public int  getProjectid() {
        return projectid;
    }
    public void setProjectid(int projectid) {
        this.projectid = projectid;
    }
    public GD.doneCode getDone() {
        return done;
    }
    public void setDone(GD.doneCode done) {
        this.done = done;
    }
    public int  getTest() {
        return test;
    }
    public void setTest(int test) {
        this.test = test;
    }
    public int  getMeasurement() {
        return measurement;
    }
    public void setMeasurement(int measurement) {
        this.measurement = measurement;
    }
    public int  getAuthor() {
        return author;
    }
    public void setAuthor(int author) {
        this.author = author;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String createURLString() {
        String values = "('" + String.valueOf(projectid)                  + "','"
                             + String.valueOf(test)                       + "','"
                             + String.valueOf(done.ii)                    + "','"
                             + String.valueOf(measurement)                + "','"
                             + String.valueOf(author)                     + "','"
                             + String.valueOf(System.currentTimeMillis()) + "')";
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
        out.writeInt(projectid);
        out.writeInt(test);
        out.writeInt(done.ii);
        out.writeInt(measurement);
        out.writeInt(author);
        out.writeLong(timestamp);
    }
    public static final Parcelable.Creator<AsBuilt> CREATOR
            = new Parcelable.Creator<AsBuilt>() {
        public AsBuilt createFromParcel(Parcel in) {
            return new AsBuilt(in);
        }
        public AsBuilt[] newArray(int size) {
            return new AsBuilt[size];
        }

    };
    private AsBuilt(Parcel in) {

        new AsBuilt(    in.readInt(),                           // record
                        in.readInt(),                           // projectID
                        in.readInt(),                           // test
                        GD.doneCode.values()[ in.readInt() ],   // done
                        in.readInt(),                           // measurement
                        in.readInt(),                           // author
                        in.readLong() );                        // timestamp
    }
}
