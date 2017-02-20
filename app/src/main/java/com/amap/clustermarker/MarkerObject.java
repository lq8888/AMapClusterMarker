package com.amap.clustermarker;

/**
 * Created by Huison on 2016/12/17.
 */

public class MarkerObject {

    private double latitude;
    private double longitude;
    private int markerId;

    public MarkerObject(double latitude, double longitude, int markerId) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.markerId = markerId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getMarkerId() {
        return markerId;
    }
}
