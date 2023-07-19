package kadir.nearbybank

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import barikoi.barikoilocation.BarikoiAPI
import barikoi.barikoilocation.NearbyPlace.NearbyPlaceAPI
import barikoi.barikoilocation.NearbyPlace.NearbyPlaceListener
import barikoi.barikoilocation.PlaceModels.NearbyPlace
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices.FusedLocationApi
import com.google.android.material.snackbar.Snackbar
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.markerview.MarkerView
import com.mapbox.mapboxsdk.plugins.markerview.MarkerViewManager


class NearBankActivity : AppCompatActivity() , GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener{

    private val PERMISSIONS_REQUEST_CODE = 512
    private var mGoogleApiClient: GoogleApiClient? = null
    private var mLocationRequest: LocationRequest? = null
    private var mapView: MapView? = null
    private var markerViewManager: MarkerViewManager? = null

    val styleUrl = "https://map.barikoi.com/styles/barikoi-bangla/style.json?key=" + BuildConfig.Api_Key

    val updateMarker: Handler? = object : Handler() {
        override fun handleMessage(msg: Message) {
            var customView: View = LayoutInflater.from(this@NearBankActivity) .inflate(kadir.nearbybank.R.layout.marker_view, null)
           customView.layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            var infoWindow : CardView = customView.findViewById(R.id.infowindow) as CardView
            infoWindow.findViewById<TextView>(R.id.marker_window_title).setText(msg.data.getString("type"))
            infoWindow.findViewById<TextView>(R.id.marker_window_snippet).setText(msg.data.getDouble("lat").toString()+ "\n"+ msg.data.getDouble("lng"))

            customView.setOnClickListener {
                var infoWindow : CardView = it.findViewById<CardView>(kadir.nearbybank.R.id.infowindow) as CardView
                if( infoWindow.isVisible){
                    infoWindow.visibility = View.INVISIBLE
                }
                else infoWindow.visibility = View.VISIBLE
            }
            var markerView = MarkerView(LatLng(msg.data.getDouble("lat"), msg.data.getDouble("lng")), customView)
            markerViewManager?.addMarker(markerView!!)

        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        BarikoiAPI.getINSTANCE(getApplicationContext(), BuildConfig.Api_Key);
        Mapbox.getInstance(this@NearBankActivity, BuildConfig.Api_Key)

        setContentView(R.layout.activity_near_bank)
        mapView = findViewById<MapView>(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        mapView!!.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView!!.onStart()
        mGoogleApiClient = GoogleApiClient.Builder(this@NearBankActivity)
            .addApi(LocationServices.API)
            .addConnectionCallbacks(this@NearBankActivity)
            .addOnConnectionFailedListener(this@NearBankActivity).build()
        if (foregroundPermissionApproved()) {
            mGoogleApiClient?.connect()
        }
        else {
            requestForegroundPermissions()
        }
    }

    override fun onStop() {
        super.onStop()
        mapView!!.onStop()
        mGoogleApiClient?.disconnect();

    }

    override fun onPause() {
        super.onPause()
        mapView!!.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (markerViewManager != null) {
            markerViewManager!!.onDestroy()
        }
        mapView!!.onDestroy()
        mGoogleApiClient?.disconnect()
    }

    fun showPlacesNearBy( locationType: String, geoPosition : LatLng ){
        NearbyPlaceAPI.builder(this@NearBankActivity)
            .setDistance(.5)
            .setLimit(10)
            .setLatLng(geoPosition.latitude, geoPosition.longitude)
            .setType(locationType)
            .build()
            .generateNearbyPlaceListByType(object : NearbyPlaceListener {
                override fun onPlaceListReceived(places: ArrayList<NearbyPlace>) {
                    for (NearbyPlace in places) {
                        var msg :Message = Message()
                        var bundle : Bundle = Bundle()
                        bundle.putDouble("lat", NearbyPlace.latitude.toDouble())
                        bundle.putDouble("lng", NearbyPlace.longitude.toDouble() )
                        bundle.putString("type", NearbyPlace.type)
                     //   bundle.putString("phn", NearbyPlace.phoneNumber )
                        msg.data = bundle
                        updateMarker?.handleMessage(msg)
                    }
                }
                override fun onFailure(message: String) {
                    Toast.makeText(this@NearBankActivity, "Error: $message", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun foregroundPermissionApproved(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    private fun requestForegroundPermissions() {
        val provideRationale = foregroundPermissionApproved()
        if (provideRationale) {
            Snackbar.make(findViewById(kadir.nearbybank.R.id.activity_near_bank), "Location permission ", Snackbar.LENGTH_LONG)
                .setAction("OK") {  ActivityCompat.requestPermissions(
                        this@NearBankActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSIONS_REQUEST_CODE)
                }.show()
        } else {
            ActivityCompat.requestPermissions(this@NearBankActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),PERMISSIONS_REQUEST_CODE )
        }
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> when {
                grantResults.isEmpty() ->
                    Log.d("Permission Message", "User interaction was cancelled.")
                grantResults[0] == PackageManager.PERMISSION_GRANTED ->
                    mGoogleApiClient?.connect()
                else -> {
                    // Permission denied.
                    Snackbar.make(findViewById(R.id.activity_near_bank), "Permission was denied", Snackbar.LENGTH_LONG)
                        .setAction("Settings") {   val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }.show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnected(p0: Bundle?) {
        mLocationRequest = LocationRequest.create()
        mLocationRequest?.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        mLocationRequest?.setInterval(2000)
        mLocationRequest?.setFastestInterval(500)
        mLocationRequest?.setMaxWaitTime(3000)
        FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this@NearBankActivity, Looper.getMainLooper());
    }

    override fun onConnectionSuspended(p0: Int) {
        TODO("Not yet implemented")
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        TODO("Not yet implemented")
    }

    override fun onLocationChanged(location: Location?) {
        mGoogleApiClient?.disconnect() // Will be disconnected once it gets Current Location//
        mapView?.getMapAsync(OnMapReadyCallback { mapboxMap ->
            mapboxMap.setStyle(styleUrl, object : Style.OnStyleLoaded {
                override fun onStyleLoaded(style: Style) {
                    mapboxMap.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(location?.latitude!!, location?.longitude!!))
                        .zoom(15.0)
                        .build()
                    showPlacesNearBy("Bank", LatLng(location?.latitude!!, location?.longitude!!))
                    markerViewManager = MarkerViewManager(mapView, mapboxMap)
                }
            })
        })
        System.out.println("Location:" + location.toString())
    }
}
