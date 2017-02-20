package com.amap.clustermarker;

import android.graphics.Point;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.widget.TextView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.Projection;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Huison on 2016/12/17.
 */

public class MainActivity extends FragmentActivity implements AMap.OnCameraChangeListener, View.OnClickListener, AMap.OnMarkerClickListener, AMap.InfoWindowAdapter {

    private MapView mapView;

    private AMap aMap;

    private float preZoom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mapView = (MapView) findViewById(R.id.map_view);

        // 必须实现
        mapView.onCreate(savedInstanceState);

        aMap = mapView.getMap();

        aMap.setOnCameraChangeListener(this);
        aMap.setOnMarkerClickListener(this);
        aMap.setInfoWindowAdapter(this);

        initMarkerObject(100);

        aMap.moveCamera(CameraUpdateFactory.zoomTo(17));

        findViewById(R.id.btn_refresh).setOnClickListener(this);
    }

    /**
     * 存放所有Marker的数据源，缩放聚合操作根据该数据进行聚合处理
     */
    private SparseArray<MarkerObject> markerObjectMap = new SparseArray<>();

    /**
     * 生成随机Marker
     *
     * @param count
     */
    private void initMarkerObject(int count) {
        double baseLat = 39.908436f;
        double baseLng = 116.395162f;

        markerObjectMap.clear();
        Random random = new Random();
        for (int index = 0; index < count; index++) {
            double lat = baseLat + random.nextDouble() * 0.1;
            double lng = baseLng + random.nextDouble() * 0.1;
            MarkerObject markerObject = new MarkerObject(lat, lng, index);
            markerObjectMap.put(index, markerObject);
        }

        handler.sendEmptyMessage(0);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_refresh:
                refreshData();
                break;
        }
    }

    /**
     * 刷新Marker
     */
    private void refreshData() {
        initMarkerObject(50);

        // 移除刷新后地图上无效的Marker
        for (int index = 0, size = singleMarkerMap.size(); index < size; index++) {
            // 去除地图上单个的Marker
            Marker marker = singleMarkerMap.valueAt(index);
            // 若该Marker不包含在数据源，说明该Marker无效，则从地图上移除
            if (markerObjectMap.get((Integer) marker.getObject()) == null) {
                marker.remove();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {

    }

    @Override
    public void onCameraChangeFinish(CameraPosition cameraPosition) {
        float curZoom = cameraPosition.zoom;

        // 缩放级别发生变化时才进行聚合处理
        if (Math.abs(curZoom - preZoom) > 0) {
            preZoom = curZoom;
            handler.sendEmptyMessage(0);
        }
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    refreshClusterMarkers();
                    break;
            }
        }
    };

    /**
     * 存放地图上显示的单个Marker
     */
    private SparseArray<Marker> singleMarkerMap = new SparseArray<>();

    /**
     * 存放地图上显示的聚合Marker
     */
    private ArrayList<Marker> clusterMarkerList = new ArrayList<>();

    /**
     * 存放聚合操作后生成的聚合类，其中包含单个的Marker和聚合的Marker信息
     */
    private ArrayList<ClusterMarker> clusters = new ArrayList<>();

    /**
     * 聚合刷新
     */
    private void refreshClusterMarkers() {
        long beginTime = System.currentTimeMillis();

        clusters.clear();
        Projection projection = aMap.getProjection();

        // 聚合操作由此开始
        for (int index = 0, size = markerObjectMap.size(); index < size; index++) {
            MarkerObject markerObject = markerObjectMap.valueAt(index);

            if (clusters.isEmpty()) {
                // 聚合类列表为空时，则直接加进聚合列表
                clusters.add(new ClusterMarker(this, markerObject, projection));
            } else {
                // 聚合类列表不为空时
                boolean isSameInBounds = false;
                // 遍历聚合类列表
                for (ClusterMarker cluster : clusters) {
                    try {
                        LatLng markerPosition = new LatLng(markerObject.getLatitude(), markerObject.getLongitude());
                        // 当Marker属于聚合类制定区域范围，则将他归并到改聚合类
                        if (cluster.getBounds().contains(markerPosition)) {
                            cluster.addClusterMarker(markerObject);
                            isSameInBounds = true;
                            break;
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
                // 如果Marker不属于聚合类指定区域范围，则自己划定区域范围，另起炉灶，等待归并其他Marker。
                if (!isSameInBounds) {
                    clusters.add(new ClusterMarker(this, markerObject, projection));
                }
            }
        }

        // 删除上一次缩放时产生的聚合Marker
        for (Marker clusterMarker : clusterMarkerList) {
            clusterMarker.remove();
        }
        clusterMarkerList.clear();

        for (ClusterMarker cluster : clusters) {
            // 如果该聚合类是聚合Marker(其区域包含不止 1 个单个的Maker)
            if (cluster.isClusterMarker()) {
                // 聚合
                cluster.refreshClusterMarker();
                // 添加聚合Marker到地图上
                Marker clusterMarker = aMap.addMarker(cluster.getMarkerOptions());
                clusterMarkerList.add(clusterMarker);
                // 从地图上移除被聚合的单个Marker
                for (MarkerObject markerObject : cluster.getMarkerObjects()) {
                    Marker marker = singleMarkerMap.get(markerObject.getMarkerId());
                    if (marker != null) {
                        marker.remove();
                        singleMarkerMap.delete(markerObject.getMarkerId());
                    }
                }
            } else {
                // 如果该聚合类只有 1 个单个的Marker
                int markerId = cluster.getMarkerObjects().get(0).getMarkerId();
                // 如果其不在地图上，则将其添加到地图上（该步骤主要是放大地图时，聚合的Marker要分解成多个单个的Marker）
                if (singleMarkerMap.get(markerId) == null) {
                    Marker singleMarker = aMap.addMarker(cluster.getMarkerOptions());
                    // 为单个的Marker设置id，主要是区分聚合的Marker，单个的Marker才允许点击，并且可以将Marker的点击数据存放到object里
                    singleMarker.setObject(markerId);
                    singleMarkerMap.put(markerId, singleMarker);
                }
            }
        }

        long endTime = System.currentTimeMillis();
        Log.d("MainActivity", "total time = " + (endTime - beginTime));
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (marker.getObject() == null) {
            marker.hideInfoWindow();
        } else {
            marker.showInfoWindow();
            jumpAnim(marker);
        }
        return true;
    }

    /**
     * marker 跳动动画，取自高德地图demo
     */
    private void jumpAnim(final Marker marker) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        Projection projection = aMap.getProjection();
        final LatLng markerLatLng = marker.getPosition();
        Point markerPoint = projection.toScreenLocation(markerLatLng);
        markerPoint.offset(0, -50);
        final LatLng startLatLng = projection.fromScreenLocation(markerPoint);
        final long duration = 1500;
        final Interpolator interpolator = new BounceInterpolator();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);
                double lng = t * markerLatLng.longitude + (1 - t) * startLatLng.longitude;
                double lat = t * markerLatLng.latitude + (1 - t) * startLatLng.latitude;
                marker.setPosition(new LatLng(lat, lng));
                if (t < 1.0) {
                    handler.postDelayed(this, 16);
                } else {
                    if (marker.isInfoWindowShown()) {
                        marker.hideInfoWindow();
                    }
                }
            }
        });
    }

    /**
     * 自动义InfoWindow
     *
     * @param marker
     * @return
     */
    @Override
    public View getInfoWindow(Marker marker) {

        View infoWindow = LayoutInflater.from(this).inflate(R.layout.layout_info_window, null);
        if (customInfoWindow(marker, infoWindow)) {
            return infoWindow;
        } else {
            return null;
        }
    }

    @Override
    public View getInfoContents(Marker marker) {
        View infoContent = LayoutInflater.from(this).inflate(R.layout.layout_info_window, null);
        if (customInfoWindow(marker, infoContent)) {
            return infoContent;
        } else {
            return null;
        }
    }

    private boolean customInfoWindow(Marker marker, View view) {
        String title = marker.getTitle();
        TextView snippetView = ((TextView) view.findViewById(R.id.info_window_snippet));
        if (!TextUtils.isEmpty(title)) {
            snippetView.setText(title);
            return true;
        } else {
            return false;
        }
    }
}
