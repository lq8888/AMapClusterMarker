package com.amap.clustermarker;

import android.graphics.Bitmap;
import android.view.View;

/**
 * Created by Huison on 2016/12/17.
 */

public class View2Bitmap {

    public static Bitmap getBitmapByView(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.buildDrawingCache();
        return view.getDrawingCache();
    }
}
