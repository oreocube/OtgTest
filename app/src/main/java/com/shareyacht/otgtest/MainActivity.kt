package com.shareyacht.otgtest

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.view.View
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.shareyacht.otgtest.databinding.ActivityMainBinding

private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mContext: Context
    private lateinit var mHandler: ActivityHandler

    private lateinit var mListener: SerialListener
    private lateinit var mSerialConnector: SerialConnector
    private lateinit var mBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBinding = ActivityMainBinding.inflate(layoutInflater)

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
                    mBinding.textSerial.append(arg2)
                }
                Constants.MSG_DEVICE_COUNT -> {
                    mBinding.textSerial.append("$arg0 device(S) fount \n")
                }
                Constants.MSG_READ_DATA_COUNT -> {
                    mBinding.textSerial.append("$arg0 buffer received \n")
                }
                Constants.MSG_READ_DATA -> {
                    if (arg3 != null) {
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
                    mBinding.textSerial.append(msg.obj as String)
                }
                Constants.MSG_DEVICE_COUNT -> {
                    mBinding.textSerial.append("${msg.arg1} device(s) found \n")
                }
                Constants.MSG_READ_DATA_COUNT -> {
                    mBinding.textSerial.append("${msg.obj}\n")
                }
                Constants.MSG_READ_DATA -> {
                    if (msg.obj != null) {
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


}

