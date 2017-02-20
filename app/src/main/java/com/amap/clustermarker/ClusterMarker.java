package com.amap.clustermarker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.amap.api.maps.Projection;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.MarkerOptions;

import java.util.ArrayList;

/**
 * Created by Huison on 2016/12/17.
 */

public class ClusterMarker {

    private static final int boundReferSize = 80;

    private Context context;
    private MarkerOptions markerOptions;
    private ArrayList<MarkerObject> markerObjects;
    private LatLngBounds bounds;

    public ClusterMarker(Context context, MarkerObject markerObject, Projection projection) {
        this.context = context;
        markerOptions = new MarkerOptions();

        // 确定区域
        LatLng markerPosition = new LatLng(markerObject.getLatitude(), markerObject.getLongitude());
        Point point = projection.toScreenLocation(markerPosition);
        Point southwestPoint = new Point(point.x - boundReferSize, point.y + boundReferSize);
        Point northeastPoint = new Point(point.x + boundReferSize, point.y - boundReferSize);

        /** 此处增加异常捕获，主要是因为缩放处理的点并不是屏幕可见点，
         * 所以产生的southwestPoint和southwestPoint有可能是屏幕外点，为负值，
         * 造成projection.fromScreenLocation非法
         */
        try {
            bounds = new LatLngBounds(
                    projection.fromScreenLocation(southwestPoint),
                    projection.fromScreenLocation(northeastPoint));
        } catch (Throwable e) {
            e.printStackTrace();
        }

        markerOptions.anchor(0.5f, 0.5f)
                .position(markerPosition)
                .title(String.valueOf(markerObject.getMarkerId()))
                .zIndex(2);

        markerObjects = new ArrayList<>();
        markerObjects.add(markerObject);
    }

    public void addClusterMarker(MarkerObject markerObject) {
        markerObjects.add(markerObject);
        markerOptions.title(null);
        markerOptions.zIndex(3);// 如果是聚合Marker, 则显示在单个的Marker之上
    }

    /**
     * 设置聚合点的中心位置以及图标
     */
    public void refreshClusterMarker() {
        int size = markerObjects.size();
        if (size == 1) {
            return;
        }

        double totalLat = 0.0;
        double totalLng = 0.0;
        for (MarkerObject markerObject : markerObjects) {
            totalLat += markerObject.getLatitude();
            totalLng += markerObject.getLongitude();
        }

        // 聚集点的平均位置为中心位置
        markerOptions.position(new LatLng(totalLat / size, totalLng / size));
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(createMarkerNumBitmap(size)));
    }

    private Bitmap createMarkerNumBitmap(int num) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_cluster_marker, null);
        ((TextView) view.findViewById(R.id.tv_cluster_count)).setText(String.valueOf(num));

        return View2Bitmap.getBitmapByView(view);
    }

    public LatLngBounds getBounds() {
        return bounds;
    }

    public MarkerOptions getMarkerOptions() {
        return markerOptions;
    }

    public ArrayList<MarkerObject> getMarkerObjects() {
        return markerObjects;
    }

    public boolean isClusterMarker() {
        return markerObjects.size() > 1;
    }
}
