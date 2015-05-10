package ca.nbenner.bizprocribbing;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;

public class ActivityEditProject extends FragmentActivity implements
        ActionBar.TabListener,
        DialogInterface.OnClickListener {
    // <editor-fold desc="Constants"
    public static ActivityEditProject aep;
    public static GD.codeIntent requestCode;
    ViewPager mViewPager;
    ProjectPagerAdapter mProjectPagerAdapter;
    ActionBar actionBar;
    public static Project selectedProject;
    public static ArrayList<TimeSheet> listOfTimeSheets;
    public static TimeSheet selectedTimeSheet;
    public static GD.editProgress progress = GD.editProgress.IN_PROGRESS;
    static TimeWindow tw = null;
    static Context c;
    static int indexToProject;
    final String STATE_PROGRESS = "progress";
    final String STATE_EDIT_PROJECT = "editProject";
    final String STATE_TIMESHEET = "timeSheet";
    final String STATE_TIMESHEET_LIST = "timeSheetList";
    final String STATE_WHICH_ENTRY = "whichTimeEntry";
    // </editor-fold>

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int daysAgoTS = 0;
        Intent intent = getIntent();
        requestCode = GD.codeIntent.values()[ intent.getIntExtra("Request", 0) ];
        c = this;
        aep = this;

        setContentView(R.layout.project_window);
        setupActionBar(daysAgoTS);
        findViewById(R.id.saveButton).setOnClickListener(new ExecuteSaveOnClickListener());
        findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        if (savedInstanceState != null) {
            selectedProject = savedInstanceState.getParcelable(STATE_EDIT_PROJECT);
            selectedTimeSheet = savedInstanceState.getParcelable(STATE_TIMESHEET);
            listOfTimeSheets = savedInstanceState.getParcelableArrayList(STATE_TIMESHEET_LIST);
            indexToProject = savedInstanceState.getInt(STATE_WHICH_ENTRY);
            progress = GD.editProgress.values()[ savedInstanceState.getInt(STATE_PROGRESS) ];

        } else {
            selectedProject = GD.projectToEdit.copy();

            long today = TimeSheet.whatDayL(0);
            ArrayList<TimeSheet> temp = new TimeSheetList().readTimeSheetsFromDB(GC.myEmployeeNumber, today, today);
            if (temp.size() == 0)
                selectedTimeSheet = new TimeSheet(today, GC.myEmployeeNumber);      // set empty timesheet as default
            else
                selectedTimeSheet = temp.get(0);

            if (selectedTimeSheet.getProjectID(1) == -1)
                selectedTimeSheet.setProject(1, selectedProject.getId());

            listOfTimeSheets = new ArrayList<TimeSheet>();
            listOfTimeSheets.add(selectedTimeSheet);

            switch (requestCode) {
                case EDIT_RQ_NEW:
                case EDIT_RQ_SHIFTED:
                    actionBar.setSelectedNavigationItem(0);
                    mViewPager.setCurrentItem(0);
                    break;
                case EDIT_RQ_CURRENT:
                default:
                    actionBar.setSelectedNavigationItem(2);
                    mViewPager.setCurrentItem(1);
            }
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if ( progress.equals( GD.editProgress.GETTING_PID )) {
            selectedTimeSheet.setProject( indexToProject, GD.projectToEdit.getId() );
            GD.projectToEdit = selectedProject;
        }
    }
    @Override public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save ActivityEditProject information
        savedInstanceState.putInt(STATE_PROGRESS, progress.ii);

        // Save EditFragment information
        savedInstanceState.putParcelable(STATE_EDIT_PROJECT, selectedProject);

        // Save Chooser information
            // nothing to save

        // Save TimeFragment information
        savedInstanceState.putParcelable(STATE_TIMESHEET, selectedTimeSheet);
        savedInstanceState.putParcelableArrayList(STATE_TIMESHEET_LIST, listOfTimeSheets);
        savedInstanceState.putInt(STATE_WHICH_ENTRY, indexToProject);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public void setupActionBar(int daysAgo) {
        actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayOptions(0);  // Don't show Title or Icon (saves space up top)

        mProjectPagerAdapter = new ProjectPagerAdapter(getFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.project_pager);
        mViewPager.setAdapter(mProjectPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPagerChanges());

        actionBar.addTab(actionBar.newTab()
                .setText("EDIT")
                .setTabListener(this), 0);

        actionBar.addTab(actionBar.newTab()
                                  .setText("RECORDS")
                                  .setTabListener(this), 1);

        actionBar.addTab(actionBar.newTab()
                                  .setText("NEW")
                                  .setTabListener(this), 2);

        addDateTabToActionBar(daysAgo);
    }
    public void addDateTabToActionBar(int daysAgo) {
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, -daysAgo);
        String title = (new SimpleDateFormat("MMM d", Locale.CANADA)).format(t.getTime());

        actionBar.addTab(actionBar.newTab()
                                  .setText(title)
                                  .setTabListener(this), actionBar.getTabCount()-1);
    }

    @Override public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {  }
    @Override public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {  }
    @Override public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        int selectedTab = tab.getPosition();
        if (selectedTab <= 1) {
            mViewPager.setCurrentItem(selectedTab);
        } else if (selectedTab < actionBar.getTabCount() - 1) {
            mViewPager.setCurrentItem(2);
            selectedTimeSheet = listOfTimeSheets.get(selectedTab - 2);
            if (tw != null)
                tw.invalidate();
            hideKeyboard();
        } else {
            mViewPager.setCurrentItem(2);
            if (actionBar.getTabCount() < 7) {
                DialogFragment chooser = new Chooser();
                chooser.show(getFragmentManager(), "Tab");
            }
        }
    }
    private class ViewPagerChanges extends ViewPager.SimpleOnPageChangeListener {
        @Override public void onPageSelected(int position) {
            hideKeyboard();
            actionBar.getTabAt(position).select();
            ExtractData.FromEditPage( findViewById(R.id.edit_project) );    // To save the data if we navigate away from the Edit tab
            if (position == 1) {
                GridView v = (GridView) findViewById(R.id.list_of_stages);
                if (v != null)
                    ((BaseAdapter) v.getAdapter()).notifyDataSetChanged();
            }
        }
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(R.id.projectWindow).getWindowToken(), 0);
    }

    public class ProjectPagerAdapter extends FragmentPagerAdapter {

        public ProjectPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override public Fragment getItem(int i) {

            switch (i) {
                case 0:
                    return new EditFragment();
                case 1:
                    return new AsBuiltRecords();
                case 2:
                default:
                    return new TimeFragment();
            }
        }
        @Override public int getCount() {
            return 3;
        }

    }
    public static class EditFragment extends Fragment {
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            View v = inflater.inflate(R.layout.edit_fragment, container, false);

            Spinner spinner = (Spinner) v.findViewById(R.id.status);
            ArrayAdapter<String> adaptStatus = new ArrayAdapter<String>(getActivity(), R.layout.my_spinner, GC.statusList);
            spinner.setAdapter(adaptStatus);
            int i = GC.statusList.indexOf( selectedProject.getStatus() );
            if (i > -1)
                    spinner.setSelection(i);

            ((TextView) v.findViewById(R.id.projectId)).setText(String.valueOf(          selectedProject.getId())        );
            ((TextView) v.findViewById(R.id.address))  .setText(                         selectedProject.getAddress()    );
            ((TextView) v.findViewById(R.id.lat))      .setText(String.format("% 10.5f", selectedProject.getLatitude())  );
            ((TextView) v.findViewById(R.id.lng))      .setText(String.format("% 10.5f", selectedProject.getLongitude()) );
            ((TextView) v.findViewById(R.id.customer)) .setText(                         selectedProject.getCustomer()   );

            return v;
        }

    }
    public static class Chooser extends DialogFragment {

        @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
            ArrayList<CharSequence>     items       = new ArrayList<CharSequence>();
            CharSequence[]              itemsArray  = new CharSequence[1];
            AlertDialog.Builder         builder     = new AlertDialog.Builder(getActivity());

            long now = System.currentTimeMillis();
            long day = 24 * 3600 * 1000;
            int jj = 0;
            ArrayList<TimeSheet> tsList = new TimeSheetList().readTimeSheetsFromDB(
                    GC.myEmployeeNumber, now - 31 * day, now );

            for (int i = 0; i < 31; i++) {
                String nextDate = TimeSheet.whatDayS(i);
                if (jj < tsList.size() && tsList.get(jj).compareDate(nextDate) == 0) {
                    items.add(nextDate + String.format(", %.2f hours",  tsList.get(jj++).billableTime() / 60f));
                } else {
                    items.add(nextDate + ", no timesheet");
                }
            }

            itemsArray = items.toArray(itemsArray);
            builder.setTitle("Select a Date")
                   .setItems(itemsArray, (DialogInterface.OnClickListener) getActivity());
            return builder.create();
        }

    }
    public static class TimeFragment extends Fragment {

        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            super.onCreateView(inflater, container, savedInstanceState);

            View v = inflater.inflate(R.layout.time_window, container, false);
            tw = (TimeWindow) v.findViewById(R.id.timeLines);

            Button plusButton = (Button) v.findViewById(R.id.plusButton);
            plusButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tw.whichThumb != -1) {
                        int j = tw.whichThumb / 2;
                        int st = Math.min( 1440, tw.mTimeSheet.getStartTime(j) + (tw.whichThumb % 2 == 0 ? tw.quantized : 0));
                        int et = Math.min( 1440, tw.mTimeSheet.getEndTime(j)   + (tw.whichThumb % 2 == 1 ? tw.quantized : 0));
                        tw.mTimeSheet.setTime(j, st, Math.max(st, et));
                        tw.invalidate();
                    }
                }
            });

            Button minusButton = (Button) v.findViewById(R.id.minusButton);
            minusButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tw.whichThumb != -1) {
                        int j = tw.whichThumb / 2;
                        int st = Math.max(    0, tw.mTimeSheet.getStartTime(j) - (tw.whichThumb % 2 == 0 ? tw.quantized : 0));
                        int et = Math.max(    0, tw.mTimeSheet.getEndTime(j)   - (tw.whichThumb % 2 == 1 ? tw.quantized : 0));
                        tw.mTimeSheet.setTime(j, Math.min(st, et), et);
                        tw.invalidate();
                    }
                }
            });

            return v;
        }
    }
    public static class AsBuiltRecords extends Fragment {

        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            super.onCreateView(inflater, container, savedInstanceState);

            return inflater.inflate(R.layout.activity_record_as_built, container, false);
        }

    }

    private class ExecuteSaveOnClickListener implements android.view.View.OnClickListener {
        public void onClick(View buttonView) {

            saveProject();
            saveTimeSheets();
            saveAsBuilts();
            finish();
        }

        private void saveProject() {
            ExtractData.FromEditPage(findViewById(R.id.edit_project));  // In case the data was changed and we are on the Edit tab.
            selectedProject.removeMarker();                             // so that we can compare with projectToSave (won't affect actual Project data)
            if ( (selectedProject.equals( GD.projectToEdit ) &&         // no edits
                 (requestCode == GD.codeIntent.EDIT_RQ_NEW)) ||         //          but NEW
                 !selectedProject.equals( GD.projectToEdit ) )   {      // or   there are edits
                new ProjectList(ActivityEditProject.this).update(selectedProject);
                Iterator itr = ActivityMain.allProjects.iterator();
                while (itr.hasNext()) {
                    Project p = (Project) itr.next();
                    if (p.getId() == selectedProject.getId()) {
                        p.removeMarker();
                        itr.remove();
                    }
                }
                selectedProject.setMarker( true );
                ActivityMain.allProjects.add(selectedProject);
            }
        }
        private void saveTimeSheets() {
            for ( TimeSheet ts : listOfTimeSheets ) {                   // Go through each timesheet (TS) pulled up in this edit session
                if (new TimeSheetList().update( ts ))                   // Tests whether the timesheet was changed
                    GC.mUsedList.Update( ts.getProjectList(), true);    // Update the "Projects Used" list with the set of projects in the TS
            }
        }
        private void saveAsBuilts() {
            for (AsBuilt ab : FragABStages.records)
                new ABRecordList().updateDB( ab );
        }
    }
    private static class ExtractData {
        static public void FromEditPage(View v) {
            if (v != null) {
                selectedProject.setRecord(0);
                selectedProject.setLongitude(Double.parseDouble(((TextView) v.findViewById(R.id.lng     )).getText().toString()));
                selectedProject.setLatitude( Double.parseDouble(((TextView) v.findViewById(R.id.lat     )).getText().toString()));
                selectedProject.setAddress(( (TextView)                     v.findViewById(R.id.address )).getText().toString() );
                selectedProject.setStatus(   (String) ((Spinner)            v.findViewById(R.id.status  )).getSelectedItem() );
                selectedProject.setCustomer(((TextView)                     v.findViewById(R.id.customer)).getText().toString());
            }
        }

    }
    // Called by clicking on a date in the dropdown list
    public void onClick(DialogInterface dialog, int which) {
        boolean found = false;

        long whichDay = TimeSheet.whatDayL( which );

        //  Check to see if the requested timesheet is already being modified.
        int tabCount = 2;
        for (TimeSheet ts : listOfTimeSheets) {
            if (ts.compareDate( whichDay ) == 0 ) {
                selectedTimeSheet = ts;
                actionBar.setSelectedNavigationItem(tabCount);
                found = true;
            }
            tabCount++;
        }

        //  Check to see if a timesheet for that date already exists  Else, create new timesheet for editing
        if (!found) {  // i.e. not already opened
            ArrayList<TimeSheet> ts = new TimeSheetList().readTimeSheetsFromDB(GC.myEmployeeNumber, whichDay, whichDay );
            if (ts.size() > 0)
                selectedTimeSheet = ts.get(0);
            else
                selectedTimeSheet = new TimeSheet(whichDay, GC.myEmployeeNumber);

            listOfTimeSheets.add(selectedTimeSheet);
            addDateTabToActionBar(which);
            actionBar.setSelectedNavigationItem(actionBar.getTabCount() - 2);
        }

        if (tw != null) tw.invalidate();
        return;
    }
    public void onRadioButtonClicked(View view) {
        FragABTests.saveRecord();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (GD.codeIntent.values()[requestCode]) {
            case RECORD_PICTURE:
                if (resultCode == RESULT_OK) {
                    // Image captured and saved to fileUri specified in the Intent
                    String fname = FragABTests.testPictURI.getPath();
                    Toast.makeText(this, "Image saved to:\n" + fname, Toast.LENGTH_SHORT).show();

                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                    bmOptions.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(fname, bmOptions);

                    int scaleFactor = (int) (Math.min(bmOptions.outWidth, bmOptions.outHeight) /
                            getResources().getDimension(R.dimen.thumbnail_size) );

                    bmOptions.inJustDecodeBounds = false;
                    bmOptions.inSampleSize = scaleFactor;
                    bmOptions.inPurgeable = true;

                    Bitmap thumb = BitmapFactory.decodeFile(fname, bmOptions);
                    String thumbPath = fname.substring( 0, fname.length() - 6) + "thumbX.jpeg";
                    File fnThumb = new File( thumbPath );
                    try {
                        fnThumb.createNewFile();
                        FileOutputStream ostream = new FileOutputStream(fnThumb);
                        thumb.compress(Bitmap.CompressFormat.JPEG, 50, ostream);
                        ostream.close();
                        FragABTests.record.setMeasurement(FragABTests.newMeasurement);
                        FragABTests.record.setDone(GD.doneCode.PASS);
                        ((BaseAdapter) FragABStages.gv.getAdapter()).notifyDataSetChanged();
                    } catch (FileNotFoundException e) {
                        Log.d("myDebug Image", "Couldn't save Thumbnail file");
                    } catch (IOException e) {
                        Log.d("myDebug Image", "Couldn't finish Thumbnail file");
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    // User cancelled the image capture
                } else {
                    // Image capture failed, advise user
                }
                break;
            default:
                // do nothing
        }
    }
    public static void launchIntent(Intent intent, GD.codeIntent code, int extra) {  // starting camera from Fragment doesn't support onActivityResult... so do it from here
        switch (code) {
            case RECORD_PICTURE:
                aep.startActivityForResult(intent, code.ii);
                break;
            case GET_PROJECT_ID:
                progress = GD.editProgress.GETTING_PID;
                indexToProject = extra;
                intent.setClass(aep.getApplicationContext(), ActivityMain.class);
                aep.startActivity(intent);
                break;
            default:
        }
    }
}


