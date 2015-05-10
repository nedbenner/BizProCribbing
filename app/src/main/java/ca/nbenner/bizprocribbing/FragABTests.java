package ca.nbenner.bizprocribbing;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class FragABTests extends Fragment implements EditTextImeBackListener {
    // <editor-fold desc="Constants"
    private static View             v;
    private static InputMethodManager imm;
    public  static AsBuilt          record = null;
    private static TextView         testTitle, testDesc, thresh;
    private static LinearLayout     testEntry;
    private static EditTextBackEvent result;
    private static RadioGroup       radioButton;
    private static ImageView        background;
    private static Button           cameraBtn;
    private static CharSequence     units;
    private static RadioButton      radioTrue, radioFalse, radioNotDone;
    private static String           testThresh1, testThresh2;
    private static GD.resultType    resultType;
    public  static Uri              testPictURI;
    public  static int              newMeasurement = 0;
    // </editor-fold>

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.fragment_as_built_tests, container, false);

        testTitle    = (TextView)           v.findViewById(R.id.test_title);
        testDesc     = (TextView)           v.findViewById(R.id.test_description);
        testEntry    = (LinearLayout)       v.findViewById(R.id.test_entry);
        result       = (EditTextBackEvent)  v.findViewById(R.id.test_result);
        radioButton  = (RadioGroup)         v.findViewById(R.id.test_radio_buttons);
        cameraBtn    = (Button)             v.findViewById(R.id.test_camera_button);
        radioTrue    = (RadioButton)        v.findViewById(R.id.test_radio_true);
        radioFalse   = (RadioButton)        v.findViewById(R.id.test_radio_false);
        radioNotDone = (RadioButton)        v.findViewById(R.id.test_radio_not_done);
        thresh       = (TextView)           v.findViewById(R.id.test_threshold);
        background   = (ImageView)          v.findViewById(R.id.test_background);

        imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        result.setOnEditTextImeBackListener(this);
        result.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE)
                    saveRecord();
                return false;
            }
        });

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                takePicture();
            }
        });

        return v;
    }
    public static void setTest(int recordIndex) {
        saveRecord();

        record = FragABStages.records.get( recordIndex );
        int test = record.getTest();
        units = GD.abTests[ test ][ ABTestList.abDesc.UNITS.ii ];
        testTitle.setText( GD.abTests[ test ][ ABTestList.abDesc.TITLE.ii ] );
        testDesc.setText(  GD.abTests[ test ][ ABTestList.abDesc.DESCRIPTION.ii ] );

        cameraBtn.setVisibility(View.GONE);
        testEntry.setVisibility(View.GONE);
        radioButton.setVisibility(View.GONE);
        testThresh1 = GD.abTests[test][ABTestList.abDesc.THRESH_A.ii];
        testThresh2 = GD.abTests[test][ABTestList.abDesc.THRESH_B.ii];
        background.setImageDrawable(null);

        if        ( units.equals("jpeg") ) {            // Displays picture
            cameraBtn.setVisibility(View.VISIBLE);
            thresh.setText("Picture required");
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            String testName = FragABStages.dir.toString() + "/test" + record.getTest() + "_" +
                    record.getMeasurement();
            if      ( new File( testName +  ".jpeg" ).exists() )
                background.setImageDrawable(Drawable.createFromPath(testName + ".jpeg"));
            else if ( new File( testName + "X.jpeg" ).exists() )
                background.setImageDrawable(Drawable.createFromPath(testName + "X.jpeg"));
            else if ( new File( testName + "thumb.jpeg" ).exists() )
                background.setImageDrawable( Drawable.createFromPath(testName + "thumb.jpeg") );
            else if ( new File( testName + "thumbX.jpeg" ).exists() )
                background.setImageDrawable( Drawable.createFromPath(testName + "thumbX.jpeg") );

        } else if ( units.equals("flag") ) {            // Displays T/F radio buttons
            radioButton.setVisibility(View.VISIBLE);
            radioTrue.setChecked(record.getMeasurement() == 1);
            radioFalse.setChecked(record.getMeasurement() == 0 && (record.getDone() != GD.doneCode.NOT_DONE));
            radioNotDone.setChecked(record.getDone() == GD.doneCode.NOT_DONE);
            thresh.setText(testThresh1.equals("None") ? "Not Defined" : "Result must be " + testThresh1);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

        } else {                                        // Displays field for entering value
            testEntry.setVisibility(View.VISIBLE);
            ((EditText) v.findViewById(R.id.test_result)).setText(
                    (record.getDone() == GD.doneCode.NOT_DONE) ? "" : String.valueOf(record.getMeasurement()) );
            ((TextView) v.findViewById(R.id.test_units)).setText( units );
            if (testThresh1.equals("None") && testThresh2.equals("None")) {
                resultType = GD.resultType.NO_TEST;
                thresh.setText("Not Defined");
            } else if (!testThresh1.equals("None") && !testThresh2.equals("None")) {
                if (GD.abTests[test][ABTestList.abDesc.OPERATOR.ii].equals("And")) {
                    resultType = GD.resultType.BETWEEN;
                    thresh.setText("Between " + testThresh1 + " and " + testThresh2);
                }
                else {
                    resultType = GD.resultType.OUTSIDE;
                    thresh.setText("Result < " + testThresh2 + " or Result > " + testThresh1);
                }
            } else if (testThresh1.equals("None")) {
                resultType = GD.resultType.LESS_THAN;
                thresh.setText("Result < " + testThresh2);
            } else {
                resultType = GD.resultType.GREATER_THAN;
                thresh.setText("Result > " + testThresh1);
            }

        }
    }
    public static void saveRecord() {
        if (record != null) {
            if (units.equals("jpeg")) {
                // do nothing

            } else if (units.equals("flag")) {
                if ( radioNotDone.isChecked() ) {
                    record.setDone(GD.doneCode.NOT_DONE);
                } else {
                    record.setMeasurement(radioTrue.isChecked() ? 1 : 0);
                    if (testThresh1.equals("None"))
                        record.setDone(GD.doneCode.DONE_NO_EVAL);
                    else if (testThresh1.equals("TRUE") == radioTrue.isChecked())
                        record.setDone(GD.doneCode.PASS);
                    else
                        record.setDone(GD.doneCode.FAIL);
                }

            } else {
                String inputValue = result.getText().toString();
                if (inputValue.isEmpty())
                    record.setDone(GD.doneCode.NOT_DONE);
                else {
                    int r = Integer.valueOf(inputValue);
                    record.setMeasurement( r );
                    switch (resultType) {
                        case NO_TEST:
                            record.setDone(GD.doneCode.DONE_NO_EVAL);
                            break;
                        case BETWEEN:
                            if (r > Integer.valueOf(testThresh1) && r < Integer.valueOf(testThresh2))
                                record.setDone(GD.doneCode.PASS);
                            else
                                record.setDone(GD.doneCode.FAIL);
                            break;
                        case OUTSIDE:
                            if (r > Integer.valueOf(testThresh1) || r < Integer.valueOf(testThresh2))
                                record.setDone(GD.doneCode.PASS);
                            else
                                record.setDone(GD.doneCode.FAIL);
                            break;
                        case LESS_THAN:
                            if (r < Integer.valueOf(testThresh2))
                                record.setDone(GD.doneCode.PASS);
                            else
                                record.setDone(GD.doneCode.FAIL);
                            break;
                        case GREATER_THAN:
                            if (r > Integer.valueOf(testThresh1))
                                record.setDone(GD.doneCode.PASS);
                            else
                                record.setDone(GD.doneCode.FAIL);
                            break;
                    }

                }
            }
        }

        if (FragABStages.gv != null) {
            ((BaseAdapter) FragABStages.gv.getAdapter()).notifyDataSetChanged();
//            new Handler().post(new Runnable() {
//                @Override public void run() {
//                    FragABStages.gv.setSelection(position);
//                }
//            });
        }
    }
    public void onImeBack(EditTextBackEvent ctrl, String text) {
        saveRecord();
    }
    public void takePicture() {
        // filename structure:
        //   <directory>
        //   /test
        //   <test #>
        //   _
        //   <picture #, 0 is none, incrementing by one otherwise>
        //   <thumb>  ==> if it is the thumbnail version (otherwise removed)
        //   <X>      ==> to indicate that it has not been saved (otherwise removed)
        //   .jpeg

        if ( !isExternalStorageWritable() ) {
            Toast.makeText(getActivity(), "Can't access memory", Toast.LENGTH_SHORT).show();

        } else {

            // Get the directory for the user's public pictures directory.
            FragABStages.dir.mkdirs();

            String testName = FragABStages.dir.toString() + "/test" + record.getTest() + "_";
            if ( new File( testName + record.getMeasurement() + "X.jpeg" ).exists() )
                newMeasurement = record.getMeasurement();
            else
                newMeasurement = record.getMeasurement() + 1;

            testName = testName + newMeasurement + "X.jpeg";
            testPictURI = Uri.fromFile(new File(testName)); // create a file to save the image

            // create Intent to take a picture and return control to the calling application
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, testPictURI); // set the image file name

            // start the image capture Intent
            ActivityEditProject.launchIntent(intent, GD.codeIntent.RECORD_PICTURE, 0);

        }
    }
    public boolean isExternalStorageWritable() {    /* Checks if external storage is available for read and write */
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}


