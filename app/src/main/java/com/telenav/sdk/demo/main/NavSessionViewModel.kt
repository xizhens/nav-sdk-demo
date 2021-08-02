/*
 * Copyright © 2021 Telenav, Inc. All rights reserved. Telenav® is a registered trademark
 * of Telenav, Inc.,Sunnyvale, California in the United States and may be registered in
 * other countries. Other names may be trademarks of their respective owners.
 */

package com.telenav.sdk.demo.main

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telenav.map.api.controllers.Camera
import com.telenav.sdk.demo.provider.DemoLocationProvider
import com.telenav.sdk.demo.util.AndroidThreadUtils
import com.telenav.sdk.demo.util.SingleLiveEvent
import com.telenav.sdk.drivesession.DriveSession
import com.telenav.sdk.drivesession.NavigationSession
import com.telenav.sdk.drivesession.listener.*
import com.telenav.sdk.drivesession.model.*
import com.telenav.sdk.drivesession.model.adas.AdasMessage
import com.telenav.sdk.drivesession.model.alert.ExitInfo
import com.telenav.sdk.examples.R
import com.telenav.sdk.map.direction.model.Action
import com.telenav.sdk.map.direction.model.LaneInfo
import com.telenav.sdk.map.direction.model.Route
import com.telenav.sdk.ui.ImageItems
import com.telenav.sdk.ui.turn.TnTurnListItem
import com.telenav.sdk.ui.turn.TnTurnListRecyclerViewAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

class NavSessionViewModel(private val turnListAdapter: TnTurnListRecyclerViewAdapter, val context: SecondFragment) : ViewModel(),
    NavigationEventListener, PositionEventListener, AlertEventListener, ADASEventListener,
    AudioInstructionEventListener,TextToSpeech.OnInitListener {
    val timeToArrival = MutableLiveData<String>()
    val showNavigationDetails = MutableLiveData<Boolean>(false)
    val totalDistanceRemaining = MutableLiveData<String>()
    val tripTimeRemaining = MutableLiveData<String>()
    val distanceRemainingToNextTurn = MutableLiveData<String>()
    val turnDirectionDrawable = MutableLiveData<Int>()
    val nextTurnStreetName = MutableLiveData<String>()
    private val turnListVisibility = MutableLiveData<Boolean>(false)

    val currentStreetName = MutableLiveData<String>()
    val compassDirectionLiveData = MutableLiveData<String>()
    val speedLimitLiveData = MutableLiveData<String>()
    val vehicleLocation = MutableLiveData<Location>()
    val junctionBitmap = MutableLiveData<Bitmap?>()
    val navigationOn = MutableLiveData<Boolean>(false)

    val currentFollowVehicleMode = MutableLiveData<Camera.FollowVehicleMode?>(null)
    var lastFollowVehicleMode : Camera.FollowVehicleMode? = null

    val candidateRoadsLiveData = MutableLiveData<List<CandidateRoadInfo>?>()
    var toast = SingleLiveEvent<String>()
    val betterRouteLiveData = MutableLiveData<Route>()
    val lanePatternCustomImages = MutableLiveData<List<ImageItems>>(listOf())
    val laneInfo = MutableLiveData<List<LaneInfo>>(listOf())
    val alongRouteTrafficLiveData = MutableLiveData<AlongRouteTraffic>()
    val traveledDistance = MutableLiveData<Double>()
    var roadCalibrator : RoadCalibrator? = null

    private val TAG = "NavSessionViewModel"
    private var currentStepIndex = 0
    private var navigationSession: NavigationSession? = null
    private var locationProvider : DemoLocationProvider
    private var driveSession: DriveSession? = DriveSession.Factory.createInstance()
    private val tts: TextToSpeech

    init {
        driveSession?.enableAlert(true)
        driveSession?.enableADAS(true)
        locationProvider = DemoLocationProvider.Factory.createProvider(context.requireContext(), DemoLocationProvider.ProviderType.SIMULATION)
        driveSession?.injectLocationProvider(locationProvider)
        tts  = TextToSpeech(context.context,this)
        driveSession?.eventHub?.let {
            it.addNavigationEventListener(this)
            it.addPositionEventListener(this)
            it.addAlertEventListener(this)
            it.addADASEventListener(this)
            it.addAudioInstructionEventListener(this)
        }
        locationProvider.start()
    }

    override fun onCleared() {
        super.onCleared()
        locationProvider.stop()
    }

    fun setLocationProvider(type : DemoLocationProvider.ProviderType){
        locationProvider = DemoLocationProvider.Factory.createProvider(context.requireContext(), type)
        driveSession?.injectLocationProvider(locationProvider)
        locationProvider.start()
    }

    fun shutdown() {
        if (driveSession != null) {
            driveSession?.injectLocationProvider(null)
            driveSession?.eventHub!!.removeNavigationEventListener(this)
            driveSession?.eventHub!!.removePositionEventListener(this)
            driveSession?.eventHub!!.removeAlertEventListener(this)
            driveSession?.eventHub!!.removeADASEventListener(this)
            driveSession?.eventHub!!.removeAudioInstructionEventListener(this)

            driveSession?.dispose()
            driveSession = null
        }
    }

    override fun onAlongRouteTrafficUpdated(alongRouteTraffic: AlongRouteTraffic) {
        var logString = String.format("along route traffic updated. route distance: %d; distance collected: %d; flows: %d",
            alongRouteTraffic.totalRouteDistance,
            alongRouteTraffic.alongRouteTrafficCollectDistance,
            alongRouteTraffic.alongRouteTrafficFlow?.size)
        Log.i("Navigation", logString)
        alongRouteTrafficLiveData.postValue(alongRouteTraffic)
    }

    override fun onJunctionViewUpdated(junctionViewInfo: JunctionViewInfo) {
        if (junctionViewInfo.isAvailable()) {
            val jvImg = junctionViewInfo.getImageData()
            val dayNightMode = junctionViewInfo.getDayNightMode()
            val junctionImageData = junctionViewInfo.getImageData()
            if (junctionImageData != null) {
                val junctionImage =
                    BitmapFactory.decodeByteArray(junctionImageData, 0, junctionImageData.size)
                junctionBitmap.postValue(junctionImage)
            }
        } else {
            junctionBitmap.postValue(null)
        }
    }

    override fun onNavigationEventUpdated(navEvent: NavigationEvent) {
        navEvent.travelEstToDestination?.let {
            if (!it.arrivalToStop.isNullOrEmpty()) {
                timeToArrival.postValue(it.arrivalToStop)
            }

        }

        navEvent.travelEstToDestination?.let {
            totalDistanceRemaining.postValue(getMilesOrFeet(it.distanceToStop))
            tripTimeRemaining.postValue(getTimeRemainingToArrival(it.timeToStop))
        }
        updateTurnListItem(navEvent)
        distanceRemainingToNextTurn.postValue(getMilesOrFeet(navEvent.distanceToTurn))
        navEvent.currentManeuver?.let {
            it.turnAction?.let { it->
                turnDirectionDrawable.postValue(getTurnDrawable(it))
            }

            it.streetName?.let { it ->
                nextTurnStreetName.postValue(it)
            }

            it.laneInfo?.let { laneInfoList ->
                laneInfo.postValue(laneInfoList)
            } ?: laneInfo.postValue(listOf())
        }

        traveledDistance.postValue(navEvent.traveledDistance)
    }

    override fun onNavigationStopReached(stopIndex: Int, stopLocation: Int) {
        if (stopIndex == -1) {// -1 means reach destination
            stopNavigation()
        }
        Log.i("Navigation", "stop or destination reached: $stopIndex")
    }

    override fun onBetterRouteDetected(betterRouteCandidate : BetterRouteCandidate) {
        //  this demonstrate accept or reject the better route candidate:
        betterRouteCandidate.accept(true)
    }

    override fun onNavigationRouteUpdated(
        route: Route,
        reason: NavigationEventListener.RouteUpdateReason?
    ) {
        Log.i("navigation", "current route updated. unique id: " + route.getID())

        //  destroy the route as soon as possible once no more needed:
//        route.dispose()
        betterRouteLiveData.postValue(route)
    }

    override fun onStreetUpdated(curStreetInfo: StreetInfo, drivingOffRoad: Boolean) {
        curStreetInfo?.streetName?.let {
            currentStreetName.postValue(it)
            Log.i("navigation", "current street: $it")
        }
        curStreetInfo.speedLimit?.let {

            var speedLimit = "invalid"
            var speed = 33.0;
            if (it.speedLimit <= -1 ){
                speed = 1.38; //5km/h
            }else if(it.speedLimit > -1 && it.speedLimit < 0x7FFFFFFF) {
                if (it.speedLimitUnit == SpeedLimitInfo.SpeedLimitUnit.KILOMETERS_PER_HOUR) {
                    speed =  it.speedLimit / 3.6 - 1.0
                    speedLimit = it.speedLimit.toString() + "km/h"

                } else {
                    speed =  1.609344 * it.speedLimit / 3.6
                    speedLimit = it.speedLimit.toString() + "mph"
                }
            }else if (it.speedLimit >= 0x7FFFFFFF){
                speed = 41.67; // 150 km/h
                speedLimit = "unlimited"

            }
            speedLimitLiveData.postValue(speedLimit)
            navigationSession?.setDemonstrateSpeed(speed.toDouble())
        }
    }

    override fun onCandidateRoadDetected(roadCalibrator: RoadCalibrator) {
        this.roadCalibrator = roadCalibrator
        candidateRoadsLiveData.postValue(roadCalibrator.getCandidateRoads())
    }

    override fun onMMFeedbackUpdated(feedback: MMFeedbackInfo) {
        Log.d(TAG, "onMMFeedbackUpdated: ")
    }

    override fun onLocationUpdated(vehicleLocationIn: Location) {
        vehicleLocationIn?.let {
            vehicleLocation.postValue(it)
            compassDirectionLiveData.postValue(getCompassDirection(it.bearing))
            var logString = String.format(
                "got vehicle location: [%.6f, %.6f, %.1f, %d]",
                vehicleLocationIn.latitude,
                vehicleLocationIn.longitude,
                vehicleLocationIn.speed,
                (vehicleLocationIn.bearing).toInt()
            )
            Log.v(TAG, logString)
        }
    }

    override fun onAlertEventUpdated(alertEvent: AlertEvent) {
        //  assert(alertEvent != null)
        alertEvent.aheadHighwayInfoItems?.let {
            Log.v("alert event", "HW EXITs: + ${it.size}, detail:")

            var index = 0
            for (item in it) {
                var roadNumber = String()
                var exitLabel = String()

                item.highwayName?.routeNumbers?.let {
                    roadNumber =
                        item.highwayName?.routeNumbers!![0].orthography?.content.toString();
                }

                exitLabel = getExitLabel(item.exitInfo)

                val locationStr = "location: [%.6f, %.6f]; distance: %d".format(
                    item.location?.lat,
                    item.location?.lon,
                    item.distanceToVehicle
                )
                val detailHighwayExit =
                    "${index + 1}; RN: ${roadNumber}; EXIT label: ${exitLabel}, " + locationStr;
                Log.v("HW Exit detail", detailHighwayExit)

                index++
            }
        }

        alertEvent.aheadAlertItems?.let {
            for (item in it) {
                Log.v(TAG, "aheadAlertItems: ${item.type} and ${item.speedLimitInfo} ${item.distanceToVehicle}" )
            }
        }
    }

    override fun onADASEventUpdated(adasMessageList: MutableList<AdasMessage>) {
        adasMessageList?.let {
            Log.v("ADAS message", "received ADAS message. groups: ${it.size}")
        }
    }

    override fun onAudioInstructionUpdated(audioInstruction: AudioInstruction) {
        tts.speak(audioInstruction.audioOrthographyString,TextToSpeech.QUEUE_FLUSH,null,"")
        Log.i("Audio Instruction", "audio instruction: ${audioInstruction?.audioOrthographyString}");

    }

    //public methods
    fun startNavigation(route: Route): Boolean {
        Log.d("MapLogsForTestData", "MapLogsForTestData >>>> startNavigation $route")
        stopNavigation()
        navigationOn.postValue(true)
        navigationSession = driveSession?.startNavigation(route, true, 40.0)
        showNavigationDetails.postValue(true)

        val turnList = navigationSession!!.maneuverList?.let { getTurnListItem(it) }
        turnListAdapter.setupData(turnList)
        return true
    }
    fun stopTTS(){
        tts.stop()
    }
    fun stopNavigation() {
        navigationSession?.stopNavigation()
        navigationOn.postValue(false)
        showNavigationDetails.postValue(false)
    }
    fun updateNavigationSpeed(speed: Double){
        navigationSession?.setDemonstrateSpeed(speed)
    }

    fun handleNavTurnList() {
        navigationSession?.let { navSession ->
            turnListVisibility.value?.let {
                if (it) {
                    turnListVisibility.postValue(false)

                } else {
                    val turnList = navSession.maneuverList?.let { it1 -> getTurnListItem(it1) }
                    turnListAdapter.setupData(turnList)
                    turnListVisibility.postValue(turnList?.isNotEmpty())

                }
            }
        }
    }

    fun enablePrefetchData(enabled : Boolean){
        driveSession?.settings?.enablePrefetch(enabled)
    }

    /**
     * Change to next follow mode
     */
    fun changeFollowVehicleMode() {
        currentFollowVehicleMode.postValue(getNextFollowVehicleModel(currentFollowVehicleMode.value))
    }

    /**
     * Enable camera follow vehicle. Use last mode if possible.
     */
    fun enableCameraFollow() {
        val mode = if (lastFollowVehicleMode != null) {
            lastFollowVehicleMode
        } else {
            SecondViewModel.DEFAULT_FOLLOW_VEHICLE_MODE
        }
        currentFollowVehicleMode.postValue(mode)
    }

    /**
     * Disable camera follow vehicle
     */
    fun disableCameraFollow() {
        lastFollowVehicleMode = currentFollowVehicleMode.value
        currentFollowVehicleMode.postValue(null)
    }

    /**
     * Show all cached candidate roads
     */
    fun showAllCandidateRoads(){
        roadCalibrator?.let{
            candidateRoadsLiveData.postValue(it.getCandidateRoads())
        }
    }

    fun selectCandidateRoad(road : CandidateRoadInfo){
        if (road.uuid != null) {
            viewModelScope.launch(Dispatchers.Unconfined){
                val result = roadCalibrator?.setRoad(road.uuid!!)
                if (result != null && result){
                    toast.postValue("Selected edged id: ${road.uuid}")
                }else{
                    toast.postValue("Select fail")
                }
            }
        }
    }

    /**
     * this method can change the location of vehicle
     */
    fun setCurrentLocation(location: Location){
        locationProvider.setLocation(location)
    }

    /**
     * this method get current GPS(faked) position
     */
    fun getCurrentLocation() : Location {
        return locationProvider.lastKnownLocation
    }

    //private methods
    private fun updateTurnListItem(navEvent: NavigationEvent) {
        if (currentStepIndex != navEvent.stepIndex) {
            currentStepIndex = navEvent.stepIndex
            AndroidThreadUtils.runOnUiThread(Runnable {
                navigationSession?.let { navSession ->
                    val turnList = navSession.maneuverList?.let { getTurnListItem(it) }
                    turnListAdapter.setupData(turnList)
                    turnListVisibility.postValue(turnList?.isNotEmpty())
                }
            })
        }
    }

    private fun getTimeRemainingToArrival(timeToStop: Int): String {
        return if (timeToStop > 60) {
            "${(timeToStop / 60)} min"
        } else {
            " 1 min"
        }
    }

    private fun getMilesOrFeet(distanceToStop: Double): String {
        val miles = distanceToStop * 0.00062137
        return if (miles > 0.1) {
            "${BigDecimal(miles).setScale(1, RoundingMode.HALF_EVEN)} mi"
        } else {
            "${BigDecimal(distanceToStop * 3.2808).setScale(0, RoundingMode.HALF_EVEN)} ft"
        }
    }

    private fun getTurnDrawable(@Action turnType: Int): Int {
        return when (turnType) {
            Action.TURN_RIGHT -> R.drawable.ic_turn_right_white
            Action.TURN_LEFT -> R.drawable.ic_turn_left_white
            Action.CONTINUE -> R.drawable.ic_continue_straight
            Action.TURN_SLIGHT_LEFT -> R.drawable.ic_turn_slight_left_white
            Action.TURN_SLIGHT_RIGHT -> R.drawable.ic_turn_slight_right
            Action.STOP_RIGHT -> R.drawable.ic_stop_right
            Action.STOP_LEFT -> R.drawable.ic_stop_left
            else -> R.drawable.ic_continue_straight
        }
    }

    private fun getTurnListItem(maneuverList: List<ManeuverInfo>): List<TnTurnListItem> =
        maneuverList
            .filter { it.stepIndex > currentStepIndex }
            .map {
                TnTurnListItem(
                    it.streetName,
                    getMilesOrFeet(it.lengthMeters),
                    getTurnDrawable(it.turnAction)
                )
            }

    private fun getExitLabel(exitInfo: ExitInfo?): String {
        exitInfo?.let {
            it.exitNumber?.let {
                return it
            }
        }

        return String()
    }

    private fun getCompassDirection(degree: Float): String {
        return when {
            degree >= 347.5 || degree < 22.5 -> "N"
            degree >= 22.5 && degree < 67.5 -> "NE"
            degree >= 67.5 && degree < 112.5 -> "E"
            degree >= 112.5 && degree < 157.5 -> "SE"
            degree >= 157.5 && degree < 202.5 -> "S"
            degree >= 202.5 && degree < 247.5 -> "SW"
            degree >= 247.5 && degree < 302.5 -> "W"
            degree >= 302.5 && degree < 347.5 -> "NW"
            else -> "--"
        }
    }

    private fun getNextFollowVehicleModel(currentMode: Camera.FollowVehicleMode?): Camera.FollowVehicleMode {
        if (currentMode == null) {
            return Camera.FollowVehicleMode.values().first()
        }

        val values = Camera.FollowVehicleMode.values()
        return values[(currentMode.ordinal+1) % values.size]
    }

    override fun onInit(status: Int) {
        if(status == TextToSpeech.SUCCESS){
            val result = tts?.setLanguage(Locale.US)

            if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "not supported")
            }

        }else{
            Log.e("TTS", "initialization failed")
        }
    }

}


