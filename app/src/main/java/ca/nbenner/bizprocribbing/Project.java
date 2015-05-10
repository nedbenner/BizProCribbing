package ca.nbenner.bizprocribbing;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

public class Project implements Parcelable {

	private int record, id, icon;
    private long timestamp;
	private double latitude, longitude;
	private String customer, address, status;
	Marker marker;

	public Project(int record, String customer, int id, double latitude, double longitude, String address, String status, Long timestamp) {
		super();
        this.record     = record;
		this.customer 	= customer;
		this.id 		= id;
		this.latitude 	= latitude;
		this.longitude 	= longitude;
		this.address 	= address;
		this.status 	= status;
        this.timestamp  = timestamp;
		this.marker		= null;
        setIcon();
	}

    private void setIcon() {
        this.icon 		= R.drawable.s1plannedx;
        if (status.compareTo("Planned")    == 0) { this.icon = R.drawable.s1plannedx;   }
        if (status.compareTo("Staked") 	   == 0) { this.icon = R.drawable.s2stakedx;    }
        if (status.compareTo("Excavated")  == 0) { this.icon = R.drawable.s3excavatorx; }
        if (status.compareTo("Footing In") == 0) { this.icon = R.drawable.s4footingx;   }
        if (status.compareTo("Wall Stood") == 0) { this.icon = R.drawable.s5wallx;      }
        if (status.compareTo("Stripped")   == 0) { this.icon = R.drawable.s6strippedx;  }
        if (status.compareTo("Completed")  == 0) { this.icon = R.drawable.s7clean;      }
    }

    public Project(int record, String customer, int id, double latitude, double longitude, String address, String status) {
        this( record, customer, id, latitude, longitude, address, status, System.currentTimeMillis());
    }

    @Override public boolean equals( Object other ) {
        if (!(other instanceof Project)) {
            return false;
        }

        Project that = (Project) other;

        // Custom equality check here.
        return     this.customer.equals(that.customer)
                && this.customer.equals(that.customer)
                && this.id          ==  that.id
                && this.latitude    ==  that.latitude
                && this.longitude   ==  that.longitude
                && this.address .equals(that.address)
                && this.status  .equals(that.status);
    }
	public int    getRecord() {
		return record;
	}
	public void   setRecord(int record) {
		this.record = record;
	}
    public int    getId() {
		return id;
	}
	public void   setId( int id ) {
		this.id = id;
	}
	public double getLatitude() {
		return latitude;
	}
    public void   setLatitude(double lat) {
		latitude = lat;
	}
	public double getLongitude() {
		return longitude;
	}
    public void   setLongitude(double lng) {
        longitude = lng;
    }
    public String getCustomer() {
		return customer;
	}
    public void   setCustomer(String customer) {
		this.customer = customer;
	}
	public String getStatus() {
		return status;
	}
	public void   setStatus(String status) {
		this.status = status;
	}
	public String getAddress() {
		return address;
	}
	public void   setAddress(String address) {
		this.address = address;
	}
    public long   getTimestamp() {
        return timestamp;
    }
	public Marker getMarker() {
		return marker;
	}
    public void   showWindow() {
        marker.showInfoWindow();
    }
    public Project copy() {
        return new Project(record, customer, id, latitude, longitude, address, status, timestamp);
    }

	public void setMarker(boolean draggable) {
        setIcon();
        marker = ActivityMain.mMap.addMarker(new MarkerOptions()
                .position(new LatLng(latitude, longitude))
                .icon(BitmapDescriptorFactory.fromResource(icon))
                .draggable( draggable )
                .visible(timestamp > GC.displayRecordsSince)
                .title(address)
                .anchor(0.5f, 0.8f));
	}
	public void removeMarker() {
        if (marker != null)
            marker.remove();
	}
    public ArrayList<AsBuilt> createAsBuiltRecords() {
        ArrayList<AsBuilt> records = new ArrayList<AsBuilt>();

        for (int ii : GD.abRequired)
            if ( ii == 1 )
                records.add( new AsBuilt(0, id, ii, GD.doneCode.NOT_DONE, 0, 0) );

        return records;
    }
    public String createURLString(String employeeNumber) {
        String command  =       String.valueOf(id)                           +  "," +
                                String.valueOf(latitude)                     +  "," +
                                String.valueOf(longitude)                    +  "," +
                          "'" + 			  (address==null  ? "":address)  + "'," +
                          "'" + 		      (customer==null ? "":customer) + "'," +
                          "'" + 	   	       status                        + "'," +
                                String.valueOf(System.currentTimeMillis())   +  "," +
                                               employeeNumber                +  ";";
        return command;
    }
    public static int findProjectIndex(List<Project> listIn, int matchId) {
        int index = 0;
        for (Project p : listIn) {
            if ( p.getId() == matchId)
                return index;
            index++;
        }
        return -1;
    }
    public static int maxRecord(List<Project> listIn) {
        if (listIn == null) return 0;

        int maxRecord = 0;
        for (Project p : listIn)
            maxRecord = Math.max( maxRecord, p.getRecord() );

        return maxRecord;
    }
//
//    Methods to implement Parcelable
//
    public int describeContents() {
        return 0;
    }
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(record);
        out.writeInt(customer.length());
        out.writeCharArray(customer.toCharArray());
        out.writeInt(id);
        out.writeDouble(latitude);
        out.writeDouble(longitude);
        out.writeInt(address.length());
        out.writeCharArray(address.toCharArray());
        out.writeInt(status.length());
        out.writeCharArray(status.toCharArray());
        out.writeLong(timestamp);
    }
    public static final Parcelable.Creator<Project> CREATOR
            = new Parcelable.Creator<Project>() {
        public Project createFromParcel(Parcel in) {
            return new Project(in);
        }
        public Project[] newArray(int size) {
            return new Project[size];
        }

    };
    private Project(Parcel in) {

        new Project(   in.readInt(),
                        new String(in.createCharArray()),
                        in.readInt(),
                        in.readDouble(),
                        in.readDouble(),
                        new String(in.createCharArray()),
                        new String(in.createCharArray()),
                        in.readLong() );
    }
}
