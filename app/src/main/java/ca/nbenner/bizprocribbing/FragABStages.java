package ca.nbenner.bizprocribbing;

import android.app.Fragment;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class FragABStages extends Fragment {
    // <editor-fold desc="Constants"
    public static ArrayList<AsBuilt>  records;
    private Toast               toast;
    private int[]               recordsMap = new int[GC.shortStatusList.size() * GC.cells];
    private int                 titleColumn = 1;
    public  static GridView     gv;
    public  static File         dir;
    public final static String STATE_ASBUILT_RECORDS = "asbuiltRecords";
    // </editor-fold>

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View    v   = inflater.inflate(R.layout.fragment_as_built_stages, container, false);
        Context c   = v.getContext();

        if (savedInstanceState != null)
            records = savedInstanceState.getParcelableArrayList(STATE_ASBUILT_RECORDS);
        else if (ActivityEditProject.requestCode == GD.codeIntent.EDIT_RQ_NEW)
            records = GD.projectToEdit.createAsBuiltRecords();
        else
            records = new ABRecordList().readAsBuiltFromDB(GD.projectToEdit.getId());

        organizeRecords();
        dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "BizPro_Cribbing/p" + GD.projectToEdit.getId());

        gv = (GridView) v.findViewById(R.id.list_of_stages);
        gv.setAdapter(new ImageAdapter(c));

        toast = Toast.makeText(c, "", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP|Gravity.CENTER_VERTICAL, 0, 100);

        gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long index) {  // if you click on cell in Gridview, show in the view of tests
                if (recordsMap[position] > -1)
                    FragABTests.setTest( recordsMap[position] );  // convert to 0 based indexing
            }
        });

        gv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long index) {  // pops up a Toast message about selected test
                if ( index == -1 )
                    toast.setText("no record");
                else
                    toast.setText("Test: " + GD.abTests[ records.get((int) index).getTest() ]
                                                                [ ABTestList.abDesc.TITLE.ii ]         );
                toast.show();

                return true;
            }
        });

        return v;
    }
    @Override public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save FragABStages information
        savedInstanceState.putParcelableArrayList(STATE_ASBUILT_RECORDS, records);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }
    private void organizeRecords() {            // Creates map between position of cell in Gridview and test record
        int row, col;
        int[] testCounter = new int[ GC.shortStatusList.size() ];
        String stage;

        for (int ii = 0; ii < recordsMap.length; ii++)
            recordsMap[ii] = -1;

        for (int ii = 0; ii < records.size(); ii++) {
            stage =  GD.abTests[ records.get(ii).getTest() ][ ABTestList.abDesc.STAGE.ii ];
            row = GC.shortStatusList.indexOf( stage );
            col = testCounter[row]++ + titleColumn + 1;
            recordsMap[row*GC.cells + col] = ii;
        }
    }

    public class ImageAdapter extends BaseAdapter {
        private Context mContext;
        private AsBuilt record;

        public ImageAdapter(Context c) {
            mContext    = c;
        }

        public int    getCount() {
            return GC.shortStatusList.size() * GC.cells;
        }
        public Object getItem(int position) {
            return null;
        }
        public long   getItemId(int position) {
            return (long) recordsMap[position];
        }

        // create a new ImageView for each item referenced by the Adapter
        public View   getView(int position, View convertView, ViewGroup parent) {
            View cellView;
            int cellSize = 70;
            int due      = GC.statusList.indexOf(ActivityEditProject.selectedProject.getStatus());
            int col      = position % GC.cells;
            int stage    = position / GC.cells;


            if (col == titleColumn) {                   // Title
                cellView = new TextView(mContext);
                ((TextView) cellView).setTextAppearance(mContext, stage <= due ? R.style.AsBuiltDue : R.style.AsBuiltNotDue);
                ((TextView) cellView).setLayoutParams(new GridView.LayoutParams(2 * cellSize, cellSize));
                ((TextView) cellView).setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                ((TextView) cellView).setText(GC.shortStatusList.get(stage));

            } else if (recordsMap[position] == -1) {    // no measurement at this position, therefore blank
                cellView = convertView == null ? new TextView(mContext) : convertView;
                cellView.setVisibility(View.INVISIBLE);

            } else {                                    // show icon representative of measurement type
                record = records.get(recordsMap[position]);
                cellView = new ImageView(mContext);
                String type = GD.abTests[record.getTest()][ABTestList.abDesc.UNITS.ii];
                if (type.equals("jpeg"))
                    if (record.getDone() == GD.doneCode.NOT_DONE) {

                        ((ImageView) cellView).setImageResource(stage <= due ?
                                                R.drawable.ab_frame_due : R.drawable.ab_frame_not_due);

                    } else {

                        String testName = dir.toString() + "/test" + record.getTest() + "_" + record.getMeasurement() + "thumb";
                        if      ( new File( testName +  ".jpeg" ).exists() )
                            ((ImageView) cellView).setImageURI( Uri.fromFile(new File( testName +  ".jpeg")) );
                        else if ( new File( testName + "X.jpeg" ).exists() )
                            ((ImageView) cellView).setImageURI( Uri.fromFile(new File( testName + "X.jpeg")) );
                        else
                            ((ImageView) cellView).setImageResource(R.drawable.ab_sample_photo);

                    }
                else  {
                    switch (record.getDone()) {
                        case NOT_DONE:      ((ImageView) cellView).setImageResource( R.drawable.ab_result_todo ); break;
                        case DONE_NO_EVAL:  ((ImageView) cellView).setImageResource( R.drawable.ab_result_pass ); break;
                        case FAIL:          ((ImageView) cellView).setImageResource( R.drawable.ab_result_fail ); break;
                        case PASS:          ((ImageView) cellView).setImageResource( R.drawable.ab_result_pass ); break;
                    }
                }

                ((ImageView) cellView).setLayoutParams(new GridView.LayoutParams(cellSize, cellSize));
                ((ImageView) cellView).setScaleType(ImageView.ScaleType.CENTER_CROP);
                ((ImageView) cellView).setPadding(6, 6, 6, 6);
            }

            return cellView;
        }

    }
}
