package ca.nbenner.bizprocribbing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;

public class TimeWindow extends View {
    // <editor-fold desc="Constants"
    private float mTimeThickness, mTimeLinePad, mThumbThickness, lengthOfMinutes;
    private static int maxNumOfProjects = MainActivity.maxNumOfProjects;
    private long pointer_down;
    private String[] timeLineTitle = new String[maxNumOfProjects + 1];
    private Rect[] rTime = new Rect[maxNumOfProjects + 1];
    private Rect[] rLabel = new Rect[maxNumOfProjects + 1];
    public TimeSheet mTimeSheet;
    private Paint mLabelTextPaint, mTimeOnPaint, mTimeOffPaint, mThumbPaint, mLunchOnPaint, mSelectedThumbPaint;
    private int mFontSize, mMaxLabelWidth, mLabelTextSize, mLabelTextColor,
            mSelectedThumbColour, mTimeOnColour, mLunchOnColour, mThumbColour, mTimeOffColour;
    public int quantized, pointer;
    public int whichThumb = -1, whichLabel = -1;
    boolean[][] whosActive = new boolean[maxNumOfProjects + 1][(maxNumOfProjects + 1) * 2];
    boolean[] thumbSelected = new boolean[(maxNumOfProjects + 1) * 2];  // guaranteed to initialize as all FALSE
    boolean DEBUG = true;
    String tag = "TimeWindow";
    private int[] timeWayPoints = new int[(maxNumOfProjects + 1) * 2];
    public static FragmentManager fm;
    ArrayList<Integer> used, viewed;
// </editor-fold>

    //  Constructors
    public TimeWindow(Context context, AttributeSet attrs) {
        super(context, attrs);

        Activity activity = (Activity) context;
        fm = activity.getFragmentManager();

        init(context, attrs, 0);
    }

    //  Initialization Methods
    private void init(Context context, AttributeSet attrs, int defStyle) {

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.TimeWindow, defStyle, defStyle);

        try {
            quantized = a.getInteger(R.styleable.TimeWindow_quantized, quantized);
            mTimeOnColour = a.getColor(R.styleable.TimeWindow_timeOnColour, mTimeOnColour);
            mTimeOffColour = a.getColor(R.styleable.TimeWindow_timeOffColour, mTimeOffColour);
            mLunchOnColour = a.getColor(R.styleable.TimeWindow_lunchOnColour, mLunchOnColour);
            mThumbColour = a.getColor(R.styleable.TimeWindow_thumbColour, mThumbColour);
            mSelectedThumbColour = a.getColor(R.styleable.TimeWindow_selectedThumbColour, mSelectedThumbColour);
            mLabelTextColor = a.getColor(R.styleable.TimeWindow_labelTextColor, mLabelTextColor);
            mTimeLinePad = a.getDimension(R.styleable.TimeWindow_mTimeLinePad, mTimeLinePad);
            mTimeThickness = a.getDimension(R.styleable.TimeWindow_timeThickness, mTimeThickness);
            mThumbThickness = a.getDimension(R.styleable.TimeWindow_thumbThickness, mThumbThickness);
            mLabelTextSize = a.getDimensionPixelSize(R.styleable.TimeWindow_labelTextSize, mLabelTextSize);
        } finally {
            a.recycle();
        }

        initPaints();
    }
    private void initPaints() {
        mLabelTextPaint = new Paint();
        mLabelTextPaint.setAntiAlias(true);
        mLabelTextPaint.setTextSize(mLabelTextSize);
        mLabelTextPaint.setColor(mLabelTextColor);
        mFontSize = (int) Math.abs(mLabelTextPaint.getFontMetrics().top);
        mMaxLabelWidth = (int) mLabelTextPaint.measureText("00:00");

        mTimeOnPaint = new Paint();
        mTimeOnPaint.setStrokeWidth(mTimeThickness);
        mTimeOnPaint.setColor(mTimeOnColour);
        mTimeOnPaint.setStyle(Paint.Style.STROKE);

        mTimeOffPaint = new Paint();
        mTimeOffPaint.setStrokeWidth(mTimeThickness);
        mTimeOffPaint.setColor(mTimeOffColour);
        mTimeOffPaint.setStyle(Paint.Style.STROKE);

        mLunchOnPaint = new Paint();
        mLunchOnPaint.setStrokeWidth(mTimeThickness);
        mLunchOnPaint.setColor(mLunchOnColour);
        mLunchOnPaint.setStyle(Paint.Style.STROKE);

        mThumbPaint = new Paint();
        mThumbPaint.setStrokeWidth(mThumbThickness);
        mThumbPaint.setColor(mThumbColour);
        mThumbPaint.setStyle(Paint.Style.STROKE);
        mThumbPaint.setAntiAlias(true);

        mSelectedThumbPaint = new Paint();
        mSelectedThumbPaint.setStrokeWidth(mThumbThickness);
        mSelectedThumbPaint.setColor(mSelectedThumbColour);
        mSelectedThumbPaint.setStyle(Paint.Style.STROKE);
        mSelectedThumbPaint.setAntiAlias(true);
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        lengthOfMinutes = (w - getPaddingLeft() - getPaddingRight()) / 1440f; // 1440 minutes in 24 hours

        //  Each time line (for lunch and each project) will consist of:
        //      Padding
        //      Labels  == Size of font (careful with descenders)
        //      Padding
        //      Time Bar == Thickness of the line
        //      Padding
        int eachTimeLineHeight = (int) (mTimeLinePad * 3 + mFontSize + mTimeThickness);
        double separation = Math.min(2 * eachTimeLineHeight, (h - getPaddingTop() - getPaddingBottom()
                - 4 * eachTimeLineHeight) / 3.0);

        for (int i = 0; i <= maxNumOfProjects; i++) {
            rTime[i] = new Rect(getPaddingLeft(), (int) (i * separation + i * eachTimeLineHeight),
                    w - getPaddingRight(), (int) (i * separation + (i + 1) * eachTimeLineHeight));
            rLabel[i] = new Rect(
                    (int) (rTime[i].centerX() - mLabelTextPaint.measureText("Project 00")), rTime[i].top,
                    (int) (rTime[i].centerX() + mLabelTextPaint.measureText(", 00 hours")), rTime[i].centerY());

        }
    }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
    }

    //  Drawing Methods
    @Override protected void onDraw(Canvas canvas) {
        boolean claimed;
        int[] allottedTime = new int[maxNumOfProjects + 1];

        super.onDraw(canvas);
        mTimeSheet = ProjectEdit.selectedTimeSheet;
        // Create array of start and end times and titles for each project (displayed in middle of timeline bar)
        for (int i = 0; i <= maxNumOfProjects; i++) {
            int p = mTimeSheet.getProjectID(i);
            if (i == 0) {
                timeLineTitle[i] = "Lunch";
            } else if (p == -1) {
                timeLineTitle[i] = "Press to select another project";
                } else {
                    int j = Projects.findProjectIndex(MainActivity.allProjects, p);
                    timeLineTitle[i] = (j == -1) ? "Project " + p + " was deleted" : MainActivity.allProjects.get(j).getAddress();
            }
            timeWayPoints[i * 2 + 0] = mTimeSheet.getStartTime(i);
            timeWayPoints[i * 2 + 1] = mTimeSheet.getEndTime(i);
        }

        Arrays.sort(timeWayPoints);

        // Create truth table array for which projects are active when
        for (int i = 0; i < timeWayPoints.length; i++) {
            for (int j = 0; j <= maxNumOfProjects; j++) {
                whosActive[j][i] = mTimeSheet.getStartTime(j) < timeWayPoints[i] &&
                        mTimeSheet.getEndTime(j) >= timeWayPoints[i];
            }
            timeWayPoints[i] = (int) (Math.round(timeWayPoints[i] / quantized) * quantized * lengthOfMinutes);
        }

        // Draw colour coded timeline bars for each project
        int lastTime = 0;
        for (int i = 0; i < timeWayPoints.length; i++) {
            canvas.drawLine(lastTime, rTime[0].bottom - mTimeThickness / 2 - mTimeLinePad,
                    timeWayPoints[i], rTime[0].bottom - mTimeThickness / 2 - mTimeLinePad,
                    whosActive[0][i] ? mLunchOnPaint : mTimeOffPaint);
            claimed = whosActive[0][i];
            allottedTime[0] += claimed ? timeWayPoints[i] - lastTime : 0;
            for (int k = maxNumOfProjects; k > 0; k--) {
                allottedTime[k] += whosActive[k][i] && !claimed ? timeWayPoints[i] - lastTime : 0;
                canvas.drawLine(lastTime, rTime[k].bottom - mTimeThickness / 2 - mTimeLinePad,
                        timeWayPoints[i], rTime[k].bottom - mTimeThickness / 2 - mTimeLinePad,
                        whosActive[k][i] && !claimed ? mTimeOnPaint : mTimeOffPaint);
                claimed = claimed || whosActive[k][i];
            }
            lastTime = timeWayPoints[i];
        }

        // Draw labels for each timeline (also includes the end of the timeline which goes out to midnight)
        for (int i = 0; i <= maxNumOfProjects; i++) {
            // end of timeline (i.e. up to midnight)
            canvas.drawLine(lastTime, rTime[i].bottom - mTimeThickness / 2 - mTimeLinePad,
                    1440 * lengthOfMinutes, rTime[i].bottom - mTimeThickness / 2 - mTimeLinePad,
                    mTimeOffPaint);

            // labels
            if ( (mTimeSheet.getProjectID(i) > 0) || (i == 0) )
                timeLineTitle[i] += ", " + formatTime(Math.round(allottedTime[i] / lengthOfMinutes / quantized) * quantized);
            canvas.drawText(formatTime(mTimeSheet.getStartTime(i)), rTime[i].left + 10,
                    (int) (rTime[i].top + mTimeLinePad + mFontSize), mLabelTextPaint);
            canvas.drawText(timeLineTitle[i], rTime[i].centerX() - mLabelTextPaint.measureText(timeLineTitle[i]) / 2,
                    (int) (rTime[i].top + mTimeLinePad + mFontSize), mLabelTextPaint);
            canvas.drawText(formatTime(mTimeSheet.getEndTime(i)), rTime[i].right - 10 - mLabelTextPaint.measureText("00:00"),
                    (int) (rTime[i].top + mTimeLinePad + mFontSize), mLabelTextPaint);

            // thumbs
            canvas.drawLine(mTimeSheet.getStartTime(i) * lengthOfMinutes, rTime[i].bottom,
                    mTimeSheet.getStartTime(i) * lengthOfMinutes, rTime[i].top + mTimeLinePad + mFontSize,
                    thumbSelected[i * 2 + 0] ? mSelectedThumbPaint : mThumbPaint);
            canvas.drawLine(mTimeSheet.getEndTime(i) * lengthOfMinutes, rTime[i].bottom,
                    mTimeSheet.getEndTime(i) * lengthOfMinutes, rTime[i].top + mTimeLinePad + mFontSize,
                    thumbSelected[i * 2 + 1] ? mSelectedThumbPaint : mThumbPaint);
        }
    }
    private static String formatTime(float timeIn) {
        String output;

        if (timeIn < 0) {
            output = "";
        } else {
            int hours = (int) timeIn / 60;
            int minutes = (int) timeIn % 60;
            output = String.format("%2d:%02d", hours, minutes);
        }
        return output;
    }
    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) invalidate();
    }

    //  Touch Methods
    @Override public boolean onTouchEvent(MotionEvent event) {

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                if ( whichLabel > -1                            &&       // measured on ACTION_DOWN
                     event.getPointerId(0) == pointer           &&
                     event.getEventTime() - pointer_down > 400    ) {

                        DialogFragment cp = ChangeProject.newInstance(GC.mUsedList.GetList(0), mTimeSheet, whichLabel);
                        cp.show(TimeWindow.fm, "ListOfProjects");
                }
                return true;

            case MotionEvent.ACTION_DOWN:
                pointer = event.getPointerId(0);
                pointer_down = event.getEventTime();

                whichLabel = hitTest(event.getX(), event.getY(), GC.HIT_LABEL);
                if (whichLabel != -1)  return true;  //  hit a label

                whichThumb = hitTest(event.getX(), event.getY(), GC.HIT_THUMB);
                if (whichThumb == -1)  break;

                for (int i = maxNumOfProjects * 2 + 1; i >= 0; i--)
                    thumbSelected[i] = (i == whichThumb);
                    // falls through to ACTION_MOVE to calculate new thumb position

            case MotionEvent.ACTION_MOVE:
                if (whichThumb > -1) {
                    int j = whichThumb / 2;
                    int newTime = Math.round(event.getX() / lengthOfMinutes / quantized) * quantized;
                    int st = (whichThumb % 2 == 0) ? newTime : mTimeSheet.getStartTime(j);
                    int et = (whichThumb % 2 == 1) ? newTime : mTimeSheet.getEndTime(j);
                    mTimeSheet.setTime(j, Math.min(st, et), Math.max(st, et));
                    invalidate();
                }
                return true;

            default:
        }

        return super.onTouchEvent(event);
    }
    private int hitTest(float x, float y, int type) {

        for (int i = 0; i <= maxNumOfProjects; i++) {
            if ( type == GC.HIT_LABEL                  &&
                 i > 0                                 &&
                 rLabel[i].contains((int) x, (int) y))    {

                if (i == 1      ||
                    mTimeSheet.getProjectID(i-1) > 0) {

                    if (DEBUG) Log.i(tag, "Found touch in Label Rect " + i);
                    return i;
                }
            }

            if (type == GC.HIT_THUMB                   &&
                rTime[i].contains((int) x, (int) y))      {

                if (mTimeSheet.getProjectID( i) == -1) return -1;               // No project selected yet
                if (mTimeSheet.getEndTime(   i) == -1) return i * 2 + 1;        // Choose End Time first if it hasn't been assigned yet
                if (mTimeSheet.getStartTime( i) == -1) return i * 2;            // Choose Start Time if it hasn't been assigned yet

                float distanceToStartThumb = Math.abs(x - mTimeSheet.getStartTime(i) * lengthOfMinutes);
                float distanceToEndThumb = Math.abs(x - mTimeSheet.getEndTime(i) * lengthOfMinutes);

                if (DEBUG) Log.i(tag, "Found touch in Time Line Rect " + i);
                return distanceToStartThumb < distanceToEndThumb ? i * 2 : i * 2 + 1;
            }

        }
        return -1;
    }

    public static class ChangeProject extends DialogFragment {
        static ArrayList<Integer> used;
        static int indexToLabel;
        static TimeSheet mTimeSheet;

        public static ChangeProject newInstance(ArrayList<Integer> u, TimeSheet t, int i) {
            ChangeProject frag = new ChangeProject();
            used = u;
            mTimeSheet = t;
            indexToLabel = i;

            return frag;
        }

        public ChangeProject() {
            super();
        }

        @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            ArrayList<CharSequence> items = new ArrayList<CharSequence>();
            int i = 0;
            while (i < 6) {
                if (used.size() >= i+1) {
                    for (Projects p : MainActivity.allProjects) {
                        if (p.getId() == used.get(i)) {
                            items.add("#" + p.getId() + ", " + p.getAddress());
                            break;
                        }
                    }
                }
                i++;
            }
            items.add("Delete");
            items.add("Select from map");

            CharSequence[] itemsArray = new CharSequence[1];
            itemsArray = items.toArray(itemsArray);
            builder.setTitle("Select a Project")
                    .setItems(itemsArray, new ProjectListOnClick(items.size(), indexToLabel));
            return builder.create();
        }

        private static class ProjectListOnClick implements DialogInterface.OnClickListener {
            private int listSize, indexToLabel;

            public ProjectListOnClick(int listSize, int indexToLabel) {
                this.listSize = listSize;
                this.indexToLabel = indexToLabel;
            }

            public void onClick(DialogInterface dialog, int which) {
                if (which == listSize-2) {          // DELETE
                    int i = indexToLabel;
                    while (i <= maxNumOfProjects) {
                        if (i < maxNumOfProjects)
                            mTimeSheet.setProjectTime(i,
                                    mTimeSheet.getProjectID(i + 1),
                                    mTimeSheet.getStartTime(i + 1),
                                    mTimeSheet.getEndTime(i + 1));
                        else
                            mTimeSheet.setProjectTime(i, -1, -1, -1);
                        i++;
                    }
                } else if (which == listSize-1) {   // SELECT FROM MAP
                } else {                            // Enter Selection
                    mTimeSheet.setProject(indexToLabel, used.get(which));
                }
            }
        }
    }
}