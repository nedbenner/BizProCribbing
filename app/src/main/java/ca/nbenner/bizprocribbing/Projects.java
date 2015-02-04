package ca.nbenner.bizprocribbing;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public class Projects implements Parcelable {

	private int record, id, icon;
    private long timestamp;
	private double latitude, longitude;
	private String customer, address, status;
	Marker marker;


	public Projects(int record, String customer, int id, double latitude, double longitude, String address, String status, Long timestamp) {
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
		
		this.icon 		= R.drawable.s1plannedx;
		if (status.compareTo("Planned")    == 0) { this.icon = R.drawable.s1plannedx;   };
		if (status.compareTo("Staked") 	   == 0) { this.icon = R.drawable.s2stakedx;    };
		if (status.compareTo("Excavated")  == 0) { this.icon = R.drawable.s3excavatorx; };
		if (status.compareTo("Footing In") == 0) { this.icon = R.drawable.s4footingx;   };
		if (status.compareTo("Wall Stood") == 0) { this.icon = R.drawable.s5wallx;      };
		if (status.compareTo("Stripped")   == 0) { this.icon = R.drawable.s6strippedx;  };
		if (status.compareTo("Completed")  == 0) { this.icon = R.drawable.s7clean;      };
	}

    public Projects(int record, String customer, int id, double latitude, double longitude, String address, String status) {
        this( record, customer, id, latitude, longitude, address, status, System.currentTimeMillis());
    }

	public int getRecord() {
		return record;
	}
    public int getId() {
		return id;
	}
	public void setId( int id ) {
		this.id = id;
		return;
	}
	public double getLatitude() {
		return latitude;
	}
    public void setLatitude(double lat) {
		latitude = lat;
	}
	public double getLongitude() {
		return longitude;
	}
    public void setLongitude(double lng) {
        longitude = lng;
    }
    public String getCustomer() {
		return customer;
	}
	public String getStatus() {
		return status;
	}
	public String getAddress() {
		return address;
	}
    public long getTimestamp() {
        return timestamp;
    }
	public int getIcon() {
		return icon;
	}
	public Marker getMarker() {
		return marker;
	}

	public void setMarker() {
        marker = MainActivity.mMap.addMarker(new MarkerOptions()
                .position(new LatLng(latitude, longitude))
                .icon(BitmapDescriptorFactory.fromResource(icon))
                .draggable(true)
                .visible(timestamp > GC.displayRecordsSince)
                .anchor(0.5f, 0.8f));
	}
	public void removeMarker() {
        if (marker != null)
            marker.remove();
	}
    public String createURLString(String employeeNumber) {
        String command = null;
        try {
            command = "id=" 	    + URLEncoder.encode(String.valueOf(id),                         "UTF-8") +
                      "&latitude="  + URLEncoder.encode(String.valueOf(latitude),                   "UTF-8") +
                      "&longitude=" + URLEncoder.encode(String.valueOf(longitude),                  "UTF-8") +
                      "&address="   + URLEncoder.encode(			   address==null  ? "":address, "UTF-8") +
                      "&customer="  + URLEncoder.encode(		       customer==null ? "":customer,"UTF-8") +
                      "&status="    + URLEncoder.encode(	   	       status,                      "UTF-8") +
                      "&timestamp=" + URLEncoder.encode(String.valueOf(System.currentTimeMillis()), "UTF-8") +
                      "&author="    + URLEncoder.encode(String.valueOf(employeeNumber),             "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return command;
    }
    public static int findProjectIndex(List<Projects> listIn, int matchId) {
        int index = 0;
        for (Projects p : listIn) {
            if ( p.getId() == matchId)
                return index;
            index++;
        }
        return -1;
    }
    public static int maxRecord(List<Projects> listIn) {
        if (listIn == null) return 0;

        int maxRecord = 0;
        for (Projects p : listIn)
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
    public static final Parcelable.Creator<Projects> CREATOR
            = new Parcelable.Creator<Projects>() {
        public Projects createFromParcel(Parcel in) {
            return new Projects(in);
        }
        public Projects[] newArray(int size) {
            return new Projects[size];
        }

    };
    private Projects(Parcel in) {

        new Projects(   in.readInt(),
                        new String(in.createCharArray()),
                        in.readInt(),
                        in.readDouble(),
                        in.readDouble(),
                        new String(in.createCharArray()),
                        new String(in.createCharArray()),
                        in.readLong() );
    }
}
