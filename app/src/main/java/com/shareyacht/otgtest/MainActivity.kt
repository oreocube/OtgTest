package com.shareyacht.otgtest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.shareyacht.otgtest.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
private const val TAG = "로그"

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mContext: Context
    private lateinit var mHandler: ActivityHandler

    private lateinit var mListener: SerialListener
    private lateinit var mSerialConnector: SerialConnector
    private lateinit var mBinding: ActivityMainBinding

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBinding = ActivityMainBinding.inflate(layoutInflater)



        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = manager.deviceList
        printToast(deviceList.toString())




        // 앱 실행 시 위치 권한 얻기
        checkLocationPermission()

        mContext = applicationContext

        mBinding.apply {
            textSerial.movementMethod = ScrollingMovementMethod()
            textInfo.movementMethod = ScrollingMovementMethod()
            buttonSend1.setOnClickListener(this@MainActivity)
            buttonSend2.setOnClickListener(this@MainActivity)
            buttonSend3.setOnClickListener(this@MainActivity)
            buttonSend4.setOnClickListener(this@MainActivity)
        }

        mListener = SerialListener()
        mHandler = ActivityHandler()

        mSerialConnector = SerialConnector(mContext, mListener, mHandler)
        mSerialConnector.initialize()


    }

    override fun onDestroy() {
        super.onDestroy()
        mSerialConnector.finalize()
    }

    private fun checkLocationPermission() {
        Log.d(TAG, "MainActivity - checkLocationPermission() called")
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                // 권한이 허용되어 있는 경우
                Log.d(TAG, "퍼미션 허용되어 있음")

                initFusedLocationProviderClient()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.d(TAG, "ACCESS_FINE_LOCATION 퍼미션 필요")
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ), 1000
                )
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                Log.d(TAG, "ACCESS_COARSE_LOCATION 퍼미션 필요")
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ), 1000
                )
            }
            else -> {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ), 1000
                )
            }
        }
    }

    private fun initFusedLocationProviderClient() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            interval = TimeUnit.SECONDS.toMillis(2)
            fastestInterval = TimeUnit.SECONDS.toMillis(1)
            maxWaitTime = TimeUnit.SECONDS.toMillis(3)

            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val now = System.currentTimeMillis()
                val date = Date(now)
                val sdf = SimpleDateFormat("MM/dd hh:mm:ss", Locale.KOREA)
                currentLocation = locationResult.lastLocation

                val data = "Date : ${sdf.format(date)} \n lat : ${currentLocation!!.latitude}, lon : ${currentLocation!!.longitude}"

                printToast(data)
                Log.d(TAG, data)

                // 아두이노로 전송
                mSerialConnector.sendCommand(data)
            }
        }

        try {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

    }

    override fun onPause() {
        super.onPause()
        printToast("onPause() called")
        Log.d(TAG, "MainActivity - onPause() called")
        // 앱 종료 시 위치 추적 종료
        if (this::fusedLocationProviderClient.isInitialized) { // fusedLocationProviderClient 객체가 초기화되어 있을 때만
            stopLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        printToast("removeLocationUpdates() called")
        Log.d(TAG, "MainActivity - stopLocationUpdates() called")
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_send1 -> mSerialConnector.sendCommand("b1")
            R.id.button_send2 -> mSerialConnector.sendCommand("b2")
            R.id.button_send3 -> mSerialConnector.sendCommand("b3")
            R.id.button_send4 -> mSerialConnector.sendCommand("b4")
        }
    }

    inner class SerialListener {
        fun onReceive(msg: Int, arg0: Int, arg1: Int, arg2: String, arg3: Any?) {
            when (msg) {
                Constants.MSG_DEVICE_INFO -> {
                    printToast(arg2)
                    mBinding.textSerial.append(arg2)
                }
                Constants.MSG_DEVICE_COUNT -> {
                    printToast("$arg0 device(S) fount")
                    mBinding.textSerial.append("$arg0 device(S) fount \n")
                }
                Constants.MSG_READ_DATA_COUNT -> {
                    printToast("$arg0 buffer received")
                    mBinding.textSerial.append("$arg0 buffer received \n")
                }
                Constants.MSG_READ_DATA -> {
                    if (arg3 != null) {
                        printToast(arg3.toString())
                        mBinding.apply {
                            textInfo.text = arg3 as String
                            textSerial.append(arg3)
                            textSerial.append("\n")
                        }
                    }
                }
                Constants.MSG_SERIAL_ERROR -> {
                    mBinding.textSerial.append(arg2)
                }
                Constants.MSG_FATAL_ERROR_FINISH_APP -> {
                    finish()
                }
            }
        }
    }

    @SuppressLint("HandlerLeak")
    inner class ActivityHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                Constants.MSG_DEVICE_INFO -> {
                    printToast(msg.obj.toString())
                    mBinding.textSerial.append(msg.obj as String)
                }
                Constants.MSG_DEVICE_COUNT -> {
                    printToast("${msg.arg1} device(s) found")
                    mBinding.textSerial.append("${msg.arg1} device(s) found \n")
                }
                Constants.MSG_READ_DATA_COUNT -> {
                    printToast(msg.obj.toString())
                    mBinding.textSerial.append("${msg.obj}\n")
                }
                Constants.MSG_READ_DATA -> {
                    if (msg.obj != null) {
                        printToast(msg.obj.toString())

                        mBinding.apply {
                            textInfo.text = msg.obj as String
                            textSerial.append(msg.obj as String)
                            textSerial.append("\n")
                        }
                    }
                }
                Constants.MSG_SERIAL_ERROR -> {
                    mBinding.textSerial.append(msg.obj as String)
                }

            }
        }
    }

    fun printToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
