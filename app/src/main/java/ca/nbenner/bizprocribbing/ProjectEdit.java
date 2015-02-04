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
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class ProjectEdit extends FragmentActivity implements
        ActionBar.TabListener,
        DialogInterface.OnClickListener {
    // <editor-fold desc="Constants"
    int projectIndex, requestCode, timeIndex = -1;
    ViewPager mViewPager;
    ProjectPagerAdapter mProjectPagerAdapter;
    public static Projects selectedProject;
    public static List<TimeSheet> listOfTimeSheets;
    public static TimeSheet selectedTimeSheet;
    ActionBar actionBar;
    static TimeWindow tw = null;
    // </editor-fold>

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int daysAgoTS = 0;

        setContentView(R.layout.project_window);
        setupActionBar(daysAgoTS, true);
        findViewById(R.id.saveButton).setOnClickListener(new ExecuteSaveOnClickListener());
        findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        Intent intent = getIntent();
        requestCode = intent.getIntExtra("Request", 0);
        projectIndex = intent.getIntExtra("Index", 0);

        selectedProject = MainActivity.projectToEdit;
        String dayEntered = TimeSheet.whatDay(0);
        int[] temp = {0, 12*60, 12*60+30, selectedProject.getId(), -1, -1};

        selectedTimeSheet = new TimeSheet( 0, dayEntered, GC.myEmployeeNumber, temp );             // set empty timesheet as default
        for ( int i = MainActivity.myTime.size()-1; i >= 0; i-- ) {                         // look through all timesheets to see
            if (MainActivity.myTime.get(i).getDate().equals( dayEntered )) {                // if there is one for today
                selectedTimeSheet = TimeSheet.newInstance(MainActivity.myTime.get(i));      //    replace default with existing timesheet
                if (selectedTimeSheet.getProjectID(1) == -1)
                    selectedTimeSheet.setProject(1, selectedProject.getId());
                timeIndex = i;
                break;
            }
        }
        listOfTimeSheets = new ArrayList<TimeSheet>();
        listOfTimeSheets.add( selectedTimeSheet );

        switch (requestCode) {
            case GC.EDIT_RQ_NEW:
            case GC.EDIT_RQ_SHIFTED:
                actionBar.setSelectedNavigationItem(0);
                mViewPager.setCurrentItem(0);
                break;
            case GC.EDIT_RQ_CURRENT:
            default:
                actionBar.setSelectedNavigationItem(2);
                mViewPager.setCurrentItem(1);
        }
    }

    public void setupActionBar(int daysAgo, boolean initialSetup) {
        if (initialSetup) {
            actionBar = getActionBar();
            actionBar.setHomeButtonEnabled(false);
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            actionBar.setDisplayOptions(0);  // Don't show Title or Icon (saves space up top)

            mProjectPagerAdapter = new ProjectPagerAdapter(getFragmentManager());
            mViewPager = (ViewPager) findViewById(R.id.project_pager);
            mViewPager.setAdapter(mProjectPagerAdapter);
            mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    actionBar.getTabAt(position==0 ? 0:2).select();
                    if (position > 0) hideKeyboard();
                }
            });

            actionBar.addTab(actionBar.newTab()
                    .setText("EDIT")
                    .setTabListener(this));

            actionBar.addTab(actionBar.newTab()
                    .setText("NEW")
                    .setTabListener(this));

        }

        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, -daysAgo);
        String title = (new SimpleDateFormat("MMM d", Locale.CANADA)).format(t.getTime());

        actionBar.addTab(actionBar.newTab()
                .setText(title)
                .setTabListener(this));

    }

    @Override public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {  }
    @Override public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {  }
    @Override public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        if (mViewPager.getChildCount() == 2) {      // verifies that ViewPager has been set up
            switch (tab.getPosition()) {
                case 0:
                    mViewPager.setCurrentItem(0);
                    break;
                case 1:
                    mViewPager.setCurrentItem(1);
                    if (actionBar.getTabCount() < 7) {
                        DialogFragment chooser = new Chooser();
                        chooser.show(getFragmentManager(), "Tab");
                    }
                    break;
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                    mViewPager.setCurrentItem(1);
                    selectedTimeSheet = listOfTimeSheets.get( tab.getPosition() - 2);
                    if (tw != null) tw.invalidate();
                    hideKeyboard();
                    break;
                default:
            }
        }
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//        imm.showSoftInput(findViewById(R.id.projectWindow), imm.HIDE_NOT_ALWAYS);
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
                default:
                    return new TimeFragment();
            }
        }
        @Override public int getCount() {
            return 2;
        }

    }
    public static class EditFragment extends Fragment {
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            View v = inflater.inflate(R.layout.edit_fragment, container, false);

            String[] statusList = getResources().getStringArray(R.array.statusList);
            Spinner spinner = (Spinner) v.findViewById(R.id.status);
            ArrayAdapter<CharSequence> adaptStatus = ArrayAdapter.createFromResource(
                    getActivity(), R.array.statusList, R.layout.my_spinner);
            spinner.setAdapter(adaptStatus);
            for (int i = statusList.length - 1; i >= 0; i--) {
                if (statusList[i].compareTo(selectedProject.getStatus()) == 0)
                    spinner.setSelection(i);
            }

            ((TextView) v.findViewById(R.id.projectId)).setText(String.valueOf(selectedProject.getId()) );
            ((TextView) v.findViewById(R.id.address)).setText(selectedProject.getAddress());
            ((TextView) v.findViewById(R.id.lat)).setText(String.format("% 10.5f", selectedProject.getLatitude()));
            ((TextView) v.findViewById(R.id.lng)).setText(String.format("% 10.5f", selectedProject.getLongitude()));
            ((TextView) v.findViewById(R.id.customer)).setText(selectedProject.getCustomer());

            return v;
        }

    }
    public static class Chooser extends DialogFragment {

        @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
            ArrayList<CharSequence>     items       = new ArrayList<CharSequence>();
            CharSequence[]              itemsArray  = new CharSequence[1];
            AlertDialog.Builder         builder     = new AlertDialog.Builder(getActivity());

            Calendar t = Calendar.getInstance();

            for (int i = 0; i < 31; i++) {
                String nextDate = (new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA)).format(t.getTime());
                float hours = -1;
                for (TimeSheet ts : MainActivity.myTime)
                    if (ts.getDate().equals(nextDate)) {
                        hours = ts.billableTime()/60f;
                        break;
                    }
                if (hours == -1)
                    items.add( nextDate + ", no timesheet");
                else
                    items.add( nextDate + String.format(", %.2f hours", hours) );
                t.add(Calendar.DAY_OF_MONTH, -1);
            }

            itemsArray = items.toArray(itemsArray);
            builder.setTitle("Select a Date")
                   .setItems(itemsArray, (DialogInterface.OnClickListener) getActivity());
            return builder.create();
        }

    }
    public static class TimeFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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

    private class ExecuteSaveOnClickListener implements View.OnClickListener {
        public void onClick(View buttonView) {

            for ( TimeSheet ts : listOfTimeSheets ) {
                boolean saveTS = true;
                Iterator itr = MainActivity.myTime.iterator();
                while (itr.hasNext()) {
                    TimeSheet original = (TimeSheet) itr.next();
                    if (ts.getDate().equals(original.getDate())) {
                        if (ts.equals(original))
                            saveTS = false;
                        itr.remove();
                        break;
                    }
                }
                MainActivity.myTime.add( ts );
                if (saveTS) {
                    new TimeSheetList(ProjectEdit.this).Update( ts );
                    GC.mUsedList.Update( ts.getProjectList(), true);
                }
            }


            View v = (View) (buttonView.getParent()).getParent();

            Projects editP = new Projects(         selectedProject.getRecord(),
                ((TextView)                    v.findViewById(R.id.customer)).getText().toString(),
                                                   selectedProject.getId(),
                Double.parseDouble(((TextView) v.findViewById(R.id.lat     )).getText().toString()),
                Double.parseDouble(((TextView) v.findViewById(R.id.lng     )).getText().toString()),
                ((TextView)                    v.findViewById(R.id.address )).getText().toString(),
                (String) ((Spinner)            v.findViewById(R.id.status  )).getSelectedItem() );

            selectedProject.removeMarker();                                            // so that we can compare with editP (won't affect actual Project data)
            if ( ((editP == selectedProject ) && (requestCode == GC.EDIT_RQ_NEW)) ||   //      no edits but NEW
                  (editP != selectedProject ) )   {                                    // or   there are edits
                new ProjectList(ProjectEdit.this).Update(editP);
                Iterator itr = MainActivity.allProjects.iterator();
                while (itr.hasNext()) {
                    Projects p = (Projects) itr.next();
                    if (p.getId() == editP.getId()) {
                        p.removeMarker();
                        itr.remove();
                    }
                }
                editP.setMarker();
                MainActivity.allProjects.add(editP);
            }
            finish();
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        boolean found = false;

        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, -which);
        String selectedDate = (new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA)).format(t.getTime());

        //  Check to see if the requested timesheet is already being modified.
        int tabCount = 2;
        for (TimeSheet ts : listOfTimeSheets) {
            if (selectedDate.equals(ts.getDate())) {
                selectedTimeSheet = ts;
                actionBar.setSelectedNavigationItem(tabCount);
                found = true;
            }
            tabCount++;
        }

        //  Check to see if a timesheet for that date already exists
        if (!found) {  // i.e. not already opened
            for (TimeSheet ts : MainActivity.myTime) {
                if (selectedDate.equals(ts.getDate())) {
                    selectedTimeSheet = ts;
                    listOfTimeSheets.add(ts);
                    setupActionBar(which, false);
                    actionBar.setSelectedNavigationItem(actionBar.getTabCount() - 1);
                    found = true;
                }
            }
        }

        //  Else, create new timesheet for editing
        if (!found) {
            String dayEntered = TimeSheet.whatDay(which);
            int[] temp = {0, 12 * 60, 12 * 60 + 30, selectedProject.getId(), -1, -1};
            selectedTimeSheet = new TimeSheet(0, dayEntered, GC.myEmployeeNumber, temp);
            listOfTimeSheets.add(selectedTimeSheet);
            setupActionBar(which, false);
            actionBar.setSelectedNavigationItem( actionBar.getTabCount()-1 );
        }

        if (tw != null) tw.invalidate();
        return;
    }
}


