/*
 * Copyright (C) 2017  Iddo Hoeve
 *
 * Squore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.doubleyellow.scoreboard.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.doubleyellow.android.view.ViewUtil;
import com.doubleyellow.scoreboard.prefs.Preferences;
import com.doubleyellow.util.Direction;
import androidx.percentlayout.widget.PercentRelativeLayout;
import com.doubleyellow.android.view.SimpleGestureListener;
import com.doubleyellow.view.DrawArrows;

/** Toast that matches more with look and feel of the Squore app */
public class SBToast extends Toast {

    private final String TAG = SBToast.class.getSimpleName();

    private ViewGroup percentRelativeLayout = null;
    private int bgColor = Color.BLACK;

    private class OnToastSwipeLister implements SimpleGestureListener.SwipeListener {
        @Override public boolean onSwipe(View v, Direction direction, float maxD, float percentageOfView) {
            SBToast.this.cancel();
            if ( percentRelativeLayout instanceof DrawArrows ) {
                ((DrawArrows)percentRelativeLayout).hideArrows();
            }
            return true;
        }
    }

    private int[] iRelatedGuiElements = null;
    public SBToast(Context context, String sMessage, int iGravityF, int bgColor, int txtColor, View activity, int[] iRelatedGuiElements, int iTextSize) {
        super(context);

        View rootView = activity.findViewById(android.R.id.content);
        PercentRelativeLayout percentRelativeLayout = ViewUtil.getFirstView(rootView, PercentRelativeLayout.class);

        this.percentRelativeLayout = percentRelativeLayout;
        this.iRelatedGuiElements   = iRelatedGuiElements;
        this.bgColor               = bgColor;

        LinearLayout ll = new LinearLayout(context);
        ll.setOnTouchListener(new SimpleGestureListener(new OnToastSwipeLister(), null, null, null));

        TextView tv = new TextView(context);
        tv.setTextColor(txtColor);
        tv.setTextSize(Preferences.TEXTSIZE_UNIT, iTextSize);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setText(sMessage);

        ll.addView(tv);
        super.setView(ll);

        // nicely rounded corners
        //float[] innerRadii = {4, 4, 4, 4, 4, 4, 4, 4};
        float fRad = (float) (5 * iTextSize) / 6;
        float[] outerRadii = {fRad, fRad, fRad, fRad, fRad, fRad, fRad, fRad};
        //RectF rectf = new RectF();
        //rectf.set(4, 4, 4, 4);
        //rectf.set(0, 0, 0, 0); // ensure entire rect is filled
        //rectf.set(2, 2, 2, 2); // ??
        RoundRectShape roundRectShape = new RoundRectShape(outerRadii, null, null);

        ShapeDrawable drawable = new ShapeDrawable(roundRectShape);
        drawable.setPadding(15, 15, 15, 15); // the amount of space around the message we want to display
        Paint paint = drawable.getPaint();
        paint.setColor(this.bgColor);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        //ll.setBackgroundDrawable(drawable);
        ll.setBackground(drawable);

        // Position you toast here toast position is 10 dp from border
        super.setGravity(iGravityF, 10, 10);
    }

    boolean bHideArrowsWhenCancelled = true;

    @Override public void cancel() {
        if ( keepAliveTimer != null ) {
          //Log.w(TAG, "Cancelling keep alive timer");
            this.keepAliveTimer.cancel();
            this.keepAliveTimer = null;
        }
        if ( bHideArrowsWhenCancelled && (percentRelativeLayout instanceof DrawArrows) ) {
            ((DrawArrows) percentRelativeLayout).hideArrows();
        }

        super.cancel();
    }

    /** call this one from a 'normal' thread */
    public void show(int iSecs) {
        this.show(iSecs, false, true);
    }

    /** call this one from a background thread (like DemoThread) */
    public void show(int iSecs, boolean bDrawArrows, boolean bHideWhenCancelled) {
        this.bHideArrowsWhenCancelled = bHideWhenCancelled;

        if ( bDrawArrows && (percentRelativeLayout instanceof DrawArrows)) {
            ((DrawArrows) percentRelativeLayout).hideArrows();
        }

        if ( bReanimateByCallingShow == false ) {
            if ( iSecs > 4 ) {
                super.setDuration(LENGTH_LONG); // LENGTH_LONG internally means 7 seconds, SHORT is 4 seconds
            } else {
                super.setDuration(LENGTH_SHORT); // LENGTH_LONG internally means 7 seconds, SHORT is 4 seconds
            }
            super.show();
        } else {
            // KeepAliveTimer will call show()
        }

        // this will trigger the show as well
        this.keepAliveTimer = new KeepAliveTimer(1000 * iSecs, 1000);
        this.keepAliveTimer.start();

        if ( bDrawArrows && (percentRelativeLayout instanceof DrawArrows) ) {
            ((DrawArrows) percentRelativeLayout).drawArrow(iRelatedGuiElements, this.bgColor);
        }
    }

    public boolean isShowing() {
        return (this.keepAliveTimer != null);
    }

    private static final boolean bReanimateByCallingShow = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 /* O=26, O_MR1=27, p=28 */;
    private KeepAliveTimer keepAliveTimer = null;

    private class KeepAliveTimer extends CountDownTimer {

        private KeepAliveTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
            //Log.w(TAG, "Started re-generating toast " + millisInFuture);
        }

        @Override public void onTick(long l) {
            if ( bReanimateByCallingShow ) {
              //Log.w(TAG, "Re-animate toast (ms left:" + l + ")");
                SBToast.this.show(); // in android 9, repeatedly calling this method makes the app crash
            } else {
              //Log.w(TAG, "*** NOT Re-animate toast (ms left:" + l + ")");
            }
        }

        @Override public void onFinish() {
            SBToast.this.cancel();
        }
    }
}
