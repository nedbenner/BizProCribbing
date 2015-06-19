package ca.nbenner.bizprocribbing;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
* Created by Superlaptop on 5/18/2015.
*/
public class ConfirmDeletion extends    DialogFragment
                             implements DialogInterface.OnClickListener {

    int year, month, day;

    static ConfirmDeletion newInstance(int year, int month, int day) {

        ConfirmDeletion f = new ConfirmDeletion();
        Bundle b = new Bundle();
        b.putInt("year",  year);
        b.putInt("month", month);
        b.putInt("day", day);
        f.setArguments(b);

        return f;
    }

    @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            year  = args.getInt("year");
            month = args.getInt("month");
            day   = args.getInt("day");
        }

        Calendar t = new GregorianCalendar(year, month, day);
        int countAll    = new LocationList().readLocationsFromDB(                     0, null ).size();
        int countSince  = new LocationList().readLocationsFromDB( t.getTime().getTime(), null ).size();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Delete Locations From Database?");
        builder.setMessage("This will remove " + (countAll - countSince) + " locations \n" +
                           "and will leave " + countSince + " locations in the DB.");
        builder.setPositiveButton("Delete Locations", this);
        builder.setNegativeButton("Cancel", this);
        return builder.create();
    }

    public void onClick (DialogInterface dialog, int which ) {
        long endTime = new GregorianCalendar(year, month, day).getTimeInMillis();
        new LocationList().deleteLocationsFromDB( 0L, endTime, null);
    }

}
