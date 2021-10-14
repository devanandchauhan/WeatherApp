package com.devanand.tapmaanapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.devanand.tapmaanapp.models.Weather
import com.devanand.tapmaanapp.models.WeatherResponse
import com.devanand.tapmaanapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog?=null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()
        /*
            Check Wheather location is On or Not
         */
        if(!isLocationEnabled()){
            Toast.makeText(this,"Your location is turned off. Please turn it on.",Toast.LENGTH_SHORT).show()

            //Opens Location Settings Page
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            //Toast.makeText(this,"Your location is already turned On",Toast.LENGTH_SHORT).show()
            Dexter.withActivity(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ).withListener(object :MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){

                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,"You have denied location permission. Please enabled them.",Toast.LENGTH_SHORT).show()
                        }

                    }


                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                       showRationalDialogForPermission()
                    }
                }).onSameThread().check()

        }
    }

    private fun getLocationWeatherDetails(latitude:Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            //Toast.makeText(this,"You have connected to the internet.",Toast.LENGTH_SHORT).show()

            val retrofit:Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object :Callback<WeatherResponse>{
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>?) {
                    if(response!!.isSuccessful){

                        hideProgressDialog()
                        val weatherList: WeatherResponse? = response.body()

                        val weatherResponseJSONString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJSONString)
                        editor.apply()
                        setupUI()
                        //setupUI(weatherList!!)
                        Log.i("Response Result","$weatherList")

                    }else{
                        val responseCode = response.code()
                        when(responseCode){
                           400 ->{
                               Log.e("Error 400","Bad Connection")
                           }
                           404 ->{
                               Log.e("Error 404","Not Found")
                           } else ->{
                               Log.e("Error","Generic Error")
                           }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error",t!!.message.toString())
                    hideProgressDialog()
                }

            })


        }else{
            Toast.makeText(this,"No internet connection.",Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions.")
            .setPositiveButton(
                "GO TO SETTINGS"
            ){_,_->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                   startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){ dialog,_->dialog.dismiss()}.show()

    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,
            mLocationCallback,
            Looper.getMainLooper())

    }

    private val mLocationCallback = object :LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult?) {
            val mLastLocation: Location = locationResult!!.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Location","$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Location","$longitude")

            getLocationWeatherDetails(latitude,longitude)
        }
    }

    /*
        Location Service Activation
     */
    private fun isLocationEnabled():Boolean{

        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return  locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                 || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if(mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for(i in weatherList.weather.indices){
                Log.i("Weather Name",weatherList.weather.toString())

                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())

                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text = unixTime(weatherList.sys.sunset)

                tv_pressure.text = weatherList.main.pressure.toString() + " N/m2(Pa)"
                //tv_max.text = weatherList.main.temp_max.toString() + " max"
                //tv_min.text = weatherList.main.temp_min.toString() + " min"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country
                tv_humidity.text = weatherList.main.humidity.toString() + " %"

                when(weatherList.weather[i].icon){
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)

                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }
            }
        }


    }

    private fun getUnit(value: String): String {
        var value ="°C"

        if("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }

        return value
    }

    private fun unixTime(timex : Long) : String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestLocationData()
                true
            }else -> super.onOptionsItemSelected(item)
        }

    }

}