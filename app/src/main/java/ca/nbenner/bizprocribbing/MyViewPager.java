package ca.nbenner.bizprocribbing;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;


public class MyViewPager extends ViewPager {

    public MyViewPager() {
        this(null, null);
    }

    public MyViewPager(Context context) {
        this(context, null);
    }

    public MyViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public boolean onInterceptTouchEvent (MotionEvent ev) {
        if (getCurrentItem() == 0)
           return super.onInterceptTouchEvent(ev);
        return false;
    }

}
