package ca.nbenner.bizprocribbing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class ListProject extends Activity
                         implements View.OnClickListener,
                                    View.OnLongClickListener {

    // <editor-fold desc="Constants"
    public static ArrayList<AsBuilt> records;
    public final int MY_EVENT_HANDLER = 7;
    private Toast toast;
    private int[]               recordsMap = new int[GC.shortStatusList.size() * GC.cells];
    private int                 titleColumn = 1;
    public  static GridView gv;
    public  static File dir;
    public final static String STATE_ASBUILT_RECORDS = "asbuiltRecords";
    ListView alv, dlv;
    public int highlighted = -1;
    public ArrayList<Integer> index;
    public int sortOrder = 0;  // according to GD.listColumns enum; positive is Ascending and negative is Descending
    public final String STATE_INDEX = "index";
    public final String STATE_SORT = "sort";
    View clickSource;

    // </editor-fold>

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_list_project);

        View v = findViewById(R.id.titleAddresses);
        v.setTag(GD.listColumns.ADDRESSES);
        v.setOnClickListener(new ResortData());

        View row = findViewById(R.id.titleData);

        v = row.findViewById(R.id.col_lat);
        v.setTag(GD.listColumns.LATITUDE);
        v.setOnClickListener(new ResortData());

        v = row.findViewById(R.id.col_long);
        v.setTag(GD.listColumns.LONGITUDE);
        v.setOnClickListener(new ResortData());

        v = row.findViewById(R.id.col_status);
        v.setTag(GD.listColumns.STATUS);
        v.setOnClickListener(new ResortData());

        v = row.findViewById(R.id.col_customer);
        v.setTag(GD.listColumns.CUSTOMER);
        v.setOnClickListener(new ResortData());

        v = row.findViewById(R.id.col_change);
        v.setTag(GD.listColumns.CHANGE);
        v.setOnClickListener(new ResortData());

        if (savedInstanceState != null) {
            index = savedInstanceState.getIntegerArrayList(STATE_INDEX);
            sortOrder = savedInstanceState.getInt(STATE_SORT);
        }

    }
    @Override protected void onResume() {
        super.onResume();

        index = new ArrayList<Integer>();
        for (int ii = 0; ii < ActivityMain.allProjects.size(); ii++ )
            index.add( ii );

        alv = (ListView) findViewById(R.id.list_addresses);
        dlv = (ListView) findViewById(R.id.list_project_data);

        alv.setAdapter(new AddressAdapter(this, R.layout.row_address));
        dlv.setAdapter(new DataAdapter(this, R.layout.row_data));

        alv.setChoiceMode(ListView.CHOICE_MODE_NONE);
        dlv.setChoiceMode(ListView.CHOICE_MODE_NONE);

        alv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (event.getSource() != MY_EVENT_HANDLER) {
                    clickSource = v;
                    MotionEvent e = MotionEvent.obtain(event);
                    e.setSource(MY_EVENT_HANDLER);
                    dlv.dispatchTouchEvent(e);
                }

                return false;
            }
        });

        dlv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getSource() != MY_EVENT_HANDLER) {
                    clickSource = v;
                    MotionEvent e = MotionEvent.obtain(event);
                    e.setSource(MY_EVENT_HANDLER);
                    alv.dispatchTouchEvent(e);
                }

                return false;
            }
        });

        alv.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                View topChild = view.getChildAt(0);             // when returning from EditProject, this view may not be available yet
                if (view == clickSource && topChild != null)
                    dlv.setSelectionFromTop(firstVisibleItem, topChild.getTop());
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}
        });

        dlv.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                View topChild = view.getChildAt(0);             // when returning from EditProject, this view may not be available yet
                if (view == clickSource && topChild != null)
                    alv.setSelectionFromTop(firstVisibleItem, topChild.getTop());
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}
        });

    }
    @Override public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putIntegerArrayList(STATE_INDEX, index);
        savedInstanceState.putInt(STATE_SORT, sortOrder);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override public void onClick(View v) {
        Integer position = (Integer) v.getTag();
        if (highlighted != position) {
            highlighted = position;
            ((ArrayAdapter) alv.getAdapter()).notifyDataSetChanged();
            ((ArrayAdapter) dlv.getAdapter()).notifyDataSetChanged();
        }
    }
    @Override public boolean onLongClick(View v) {
        Integer ii = (Integer) v.getTag();
        GD.projectToEdit = ActivityMain.allProjects.get( index.get(ii) ).copy();
        Intent intent = new Intent(ListProject.this, ActivityEditProject.class);
        intent.putExtra("Request", GD.codeIntent.EDIT_RQ_CURRENT.ii);
        startActivity(intent);

        return true;
    }
    public class ResortData implements View.OnClickListener {
        public ResortData() {}
        @Override public void onClick(View v) {
            Method m;

            GD.listColumns title = (GD.listColumns) v.getTag();
            try {
                switch (title) {
                    case ADDRESSES:
                        m = Project.class.getMethod("getAddress");    break;
                    case LATITUDE:
                        m = Project.class.getMethod("getLatitude");   break;
                    case LONGITUDE:
                        m = Project.class.getMethod("getLongitude");  break;
                    case STATUS:
                        m = Project.class.getMethod("getStatus");     break;
                    case CUSTOMER:
                        m = Project.class.getMethod("getCustomer");   break;
                    case CHANGE:
                        m = Project.class.getMethod("getTimestamp");  break;
                    default:
                        return;
                }
            } catch (NoSuchMethodException e) {
                return;
            }

            sortOrder = Math.abs( sortOrder ) == title.ii ? -sortOrder : title.ii;
            int length = index.size();
            ArrayList<Integer> newIndex = new ArrayList<Integer>();

            try {
                if (m.getReturnType() == String.class) {
                    ArrayList<String> originalListS = new ArrayList<String>();
                    for (int ii : index)
                        originalListS.add( (String) m.invoke(ActivityMain.allProjects.get(ii)) );

                    String[] sortListS = new String[ length ];
                    originalListS.toArray( sortListS );
                    Arrays.sort(sortListS);

                    for (int i = 0; i < length; i++) {
                        int j = originalListS.indexOf( sortListS[ sortOrder < 0 ? length-1 - i : i ] );
                        newIndex.add( index.get(j) );
                        originalListS.set( j, "//" );                 // set it to an unexpected (or better yet, illegal) character
                    }
                } else if (m.getReturnType() == double.class) {
                    ArrayList<Double> originalListD = new ArrayList<Double>();
                    for (int ii : index)
                        originalListD.add( (Double) m.invoke(ActivityMain.allProjects.get(ii)) );

                    Double[] sortListD = new Double[ length ];
                    originalListD.toArray( sortListD );
                    Arrays.sort(sortListD);

                    for (int i = 0; i < length; i++) {
                        int j = originalListD.indexOf( sortListD[ sortOrder < 0 ? length-1 - i : i ] );
                        newIndex.add( index.get(j) );
                        originalListD.set( j, -989012338464.25 );                 // set it to an unexpected (or better yet, illegal) character
                    }
                } else {
                    ArrayList<Long> originalListL = new ArrayList<Long>();
                    for (int ii : index)
                        originalListL.add( (Long) m.invoke(ActivityMain.allProjects.get(ii)) );

                    Long[] sortListL = new Long[ length ];
                    originalListL.toArray( sortListL );
                    Arrays.sort(sortListL);

                    for (int i = 0; i < length; i++) {
                        int j = originalListL.indexOf( sortListL[ sortOrder < 0 ? length-1 - i : i ] );
                        newIndex.add( index.get(j) );
                        originalListL.set( j, -989012338464L );                 // set it to an unexpected (or better yet, illegal) character
                    }
                }
            } catch (IllegalAccessException e) {
                Log.e("myDebug", "Error in accessing method of Project" + e.toString());
            } catch (InvocationTargetException e) {
                Log.e("myDebug", "Error in targeting method of Project" + e.toString());
            }
            index = newIndex;

            ((ArrayAdapter) alv.getAdapter()).notifyDataSetChanged();
            ((ArrayAdapter) dlv.getAdapter()).notifyDataSetChanged();
        }
    }

    public class AddressAdapter extends ArrayAdapter<Integer>  {

        public AddressAdapter(Context context, int resource) {
            super(context, resource, index);
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {

            TextView v = (TextView) convertView;

            if (v == null)
                v =  (TextView) LayoutInflater.from(getContext()).inflate(R.layout.row_address, null);

            Project p = ActivityMain.allProjects.get( index.get(position) );
            if (p != null) {
                v.setText(p.getAddress());
                v.setBackgroundColor(position == highlighted ? 0xff50ffe0 : 0xffffe040);
                v.setOnLongClickListener(ListProject.this);
                v.setOnClickListener(ListProject.this);
                v.setTag(position);
            }

            return v;

        }

    }
    public class DataAdapter extends ArrayAdapter<Integer> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.CANADA);

        public DataAdapter(Context context, int resource) {
            super(context, resource, index);
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {

            View v = convertView;

            if (v == null)
                v =  LayoutInflater.from(getContext()).inflate(R.layout.row_data, null);

            Project p = ActivityMain.allProjects.get( index.get(position) );
            if (p != null) {

                TableLayout tl = (TableLayout) v.findViewById(R.id.list_project_data);
                tl.setBackgroundColor(position == highlighted ? 0xff50ffe0 : 0xffffe040);
                tl.setOnLongClickListener(ListProject.this);
                tl.setOnClickListener(ListProject.this);
                tl.setTag(position);

                ((TextView) tl.findViewById(R.id.col_change)  ).setText(              sdf.format(p.getTimestamp() ));
                ((TextView) tl.findViewById(R.id.col_customer)).setText(                         p.getCustomer()   );
                ((TextView) tl.findViewById(R.id.col_lat)     ).setText(String.format("% 10.5f", p.getLatitude()  ));
                ((TextView) tl.findViewById(R.id.col_long)    ).setText(String.format("% 10.5f", p.getLongitude() ));
                ((TextView) tl.findViewById(R.id.col_status)  ).setText(                         p.getStatus()     );
            }

            return v;

        }
    }

  }