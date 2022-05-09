package com.telenav.mapboxnavigationview;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mapbox.maps.MapboxMap;
import com.telenav.map.api.MapSubView;
import com.telenav.map.api.MapView;
import com.telenav.map.api.MapViewReadyListener;
import com.telenav.map.api.controllers.AnnotationsController;
import com.telenav.map.api.controllers.CameraController;
import com.telenav.map.api.controllers.FeaturesController;
import com.telenav.map.api.controllers.LayoutController;
import com.telenav.map.api.controllers.RoutesController;
import com.telenav.map.api.controllers.SearchController;
import com.telenav.map.api.controllers.ShapesController;
import com.telenav.map.api.controllers.ThemeController;
import com.telenav.map.api.controllers.VehicleController;
import com.telenav.map.api.diagnosis.MapDiagnosis;
import com.telenav.map.api.touch.GestureType;
import com.telenav.map.api.touch.listeners.AnnotationTouchListener;
import com.telenav.map.api.touch.listeners.MapElementTouchListener;
import com.telenav.map.api.touch.listeners.POITouchListener;
import com.telenav.map.api.touch.listeners.RouteTouchListener;
import com.telenav.map.api.touch.listeners.TouchListener;
import com.telenav.map.api.touch.listeners.ViewTouchListener;

import java.util.Set;

public class MapboxMapView extends LinearLayout implements MapView {

    private MapboxMap mapcore;
    private com.mapbox.maps.MapView mapView;

    public MapboxMapView(Context context) {
        super(context);
    }

    public MapboxMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MapboxMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MapboxMapView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void initialize(Bundle bundle, @Nullable MapViewReadyListener<MapView> mapViewReadyListener) {

    }

    @Override
    public void initialize(Bundle bundle, float v, @Nullable MapViewReadyListener<MapView> mapViewReadyListener) {

    }

    @Nullable
    @Override
    public MapSubView createSubView(double v, double v1, int i, int i1, @Nullable MapViewReadyListener<MapSubView> mapViewReadyListener) {
        return null;
    }

    @Override
    public boolean isRenderEngineInitialized(int i) {
        return false;
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void setFPS(int i) {

    }

    @Override
    public void setActiveGestures(@Nullable Set<GestureType> set) {

    }

    @Override
    public void setOnTouchListener(@NonNull TouchListener touchListener) {

    }

    @Override
    public void setOnViewTouchListener(@Nullable ViewTouchListener viewTouchListener) {

    }

    @Override
    public void setOnAnnotationTouchListener(@Nullable AnnotationTouchListener annotationTouchListener) {

    }

    @Override
    public void setOnRouteTouchListener(@Nullable RouteTouchListener routeTouchListener) {

    }

    @Override
    public void setOnPOITouchListener(@Nullable POITouchListener poiTouchListener) {

    }

    @Override
    public void setOnMapElementTouchListener(@Nullable MapElementTouchListener mapElementTouchListener) {

    }

    @Override
    public void addMapViewListener(@NonNull MapViewListener mapViewListener) {

    }

    @Override
    public void removeMapViewListener(@NonNull MapViewListener mapViewListener) {

    }

    @Override
    public VehicleController vehicleController() {
        return null;
    }

    @Override
    public RoutesController routesController() {
        return null;
    }

    @Override
    public AnnotationsController annotationsController() {
        return null;
    }

    @Override
    public CameraController cameraController() {
        return null;
    }

    @Override
    public FeaturesController featuresController() {
        return null;
    }

    @Override
    public ShapesController shapesController() {
        return null;
    }

    @Override
    public ThemeController themeController() {
        return null;
    }

    @Override
    public LayoutController layoutController() {
        return null;
    }

    @Override
    public SearchController searchController() {
        return null;
    }

    @Override
    public MapDiagnosis mapDiagnosis() {
        return null;
    }

    @Override
    public void generateSnapshot(@NonNull SnapshotReadyCallback snapshotReadyCallback) {

    }

    @Override
    public void generateOffscreenSnapshot(int i, int i1, @Nullable MapViewReadyListener<MapSubView> mapViewReadyListener, @NonNull SnapshotReadyCallback snapshotReadyCallback) {

    }
}
