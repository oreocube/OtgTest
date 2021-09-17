package com.shareyacht.otgtest

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Message
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.util.*

private const val TAG = "로그"
class SerialConnector(c: Context, l: MainActivity.SerialListener, h: Handler) {
    private val tag = "SerialConnector"

    private var mContext: Context = c
    private var mListener: MainActivity.SerialListener = l
    private var mHandler: Handler = h

    private var mSerialThread: SerialMonitorThread? = null

    private var mDriver: UsbSerialDriver? = null
    private var mPort: UsbSerialPort? = null

    val TARGET_VENDOR_ID = 9025 // Arduino

    val TARGET_VENDOR_ID2 = 1659 // PL2303

    val TARGET_VENDOR_ID3 = 1027 // FT232R

    val TARGET_VENDOR_ID4 = 6790 // CH340G

    val TARGET_VENDOR_ID5 = 4292 // CP210x

    val BAUD_RATE = 115200

    // 연결 초기화
    fun initialize() {
        Log.d(TAG, "SerialConnector - initialize() called")
        // UsbManager로 부터 현재 기기에 연결된 모든 기기를 가져온다.
        val manager = mContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            // 사용가능한 기기가 없는 경우
            mListener.onReceive(
                Constants.MSG_SERIAL_ERROR,
                0,
                0,
                "Error: There is no available device. \n",
                null
            )
            return
        }
        mDriver = availableDrivers[0]
        if (mDriver == null) {
            mListener.onReceive(
                Constants.MSG_SERIAL_ERROR,
                0,
                0,
                "Error: Driver is Null \n",
                null
            )
            return
        }

        // Report to UI
        val sb = StringBuilder()
        // 기기의 정보를 UI에 표시한다.
        val device = mDriver!!.device
        sb.append(" DName : ").append(device.deviceName).append("\n")
            .append(" DID : ").append(device.deviceId).append("\n")
            .append(" VID : ").append(device.vendorId).append("\n")
            .append(" PID : ").append(device.productId).append("\n")
            .append(" IF Count : ").append(device.interfaceCount).append("\n")
        mListener.onReceive(Constants.MSG_DEVICE_INFO, 0, 0, sb.toString(), null)

        // 기기와 연결한다.
        val connection = manager.openDevice(device)
        if (connection == null) {
            mListener.onReceive(
                Constants.MSG_SERIAL_ERROR,
                0,
                0,
                "Error: Cannot connect to device. \n",
                null
            )
            return
        }

        // Read some data! Most have just one port (port 0).
        mPort = mDriver!!.ports[0]
        if (mPort == null) {
            mListener.onReceive(
                Constants.MSG_SERIAL_ERROR,
                0,
                0,
                "Error: Cannot get port. \n",
                null
            )
            return
        }
        try {
            // 포트를 연다.
            mPort!!.open(connection)
            mPort!!.setParameters(9600, 0, 0, UsbSerialPort.PARITY_NONE) // baudrate:9600, dataBits:8, stopBits:1, parity:N

        } catch (e: IOException) {
            // Deal with error.
            mListener.onReceive(
                Constants.MSG_SERIAL_ERROR,
                0,
                0,
                "Error: Cannot open port \n$e\n",
                null
            )
        }

        // Everything is fine. Start serial monitoring thread.
        startThread()
    } // End of initialize()

    // 연결을 종료한다.
    fun finalize() {
        try {
            mDriver = null
            stopThread()
            mPort!!.close()
            mPort = null
        } catch (ex: Exception) {
            mListener.onReceive(
                Constants.MSG_SERIAL_ERROR, 0, 0,
                "Error: Cannot finalize serial connector \n$ex\n", null
            )
        }
    }


    /*****************************************************
     * public methods
     */
    // send string to remote
    // 다른 디바이스에 무언가를 보낸다.
    fun sendCommand(cmd: String?) {
        if (mPort != null && cmd != null) {
            try {
                mPort!!.write(cmd.toByteArray(), cmd.length) // Send to remote device
            } catch (e: IOException) {
                mListener.onReceive(
                    Constants.MSG_SERIAL_ERROR,
                    0,
                    0,
                    "Failed in sending command. : IO Exception \n",
                    null
                )
            }
        }
    }


    /*****************************************************
     * private methods
     */
    // start thread
    private fun startThread() {
        Log.d(tag, "Start serial monitoring thread")
        mListener.onReceive(
            Constants.MSG_SERIAL_ERROR,
            0,
            0,
            "Start serial monitoring thread \n",
            null
        )
        if (mSerialThread == null) {
            mSerialThread = SerialMonitorThread()
            mSerialThread!!.start()
        }
    }

    // stop thread
    private fun stopThread() {
        if (mSerialThread != null && mSerialThread!!.isAlive) mSerialThread!!.interrupt()
        if (mSerialThread != null) {
            mSerialThread!!.setKillSign(true)
            mSerialThread = null
        }
    }


    /*****************************************************
     * Sub classes, Handler, Listener
     */
    inner class SerialMonitorThread : Thread() {
        // Thread status
        private var mKillSign = false
        private val mCmd = SerialCommand()
        private fun initializeThread() {
            // This code will be executed only once.
        }

        private fun finalizeThread() {}

        // stop this thread
        fun setKillSign(isTrue: Boolean) {
            mKillSign = isTrue
        }

        /**
         * Main loop - 계속해서 루프를 돌면서 다른 기기로부터 들어오는 정보를 읽는다.
         */
        override fun run() {
            val buffer = ByteArray(128)
            while (!interrupted()) {
                if (mPort != null) {
                    Arrays.fill(buffer, 0x00.toByte())
                    try {
                        // Read received buffer
                        val numBytesRead: Int = mPort!!.read(buffer, 1000)
                        if (numBytesRead > 0) {
                            Log.d(tag, "run : read bytes = $numBytesRead")

                            // Print message length
                            val msg: Message = mHandler.obtainMessage(
                                Constants.MSG_READ_DATA_COUNT, numBytesRead, 0,
                                String(buffer)
                            )
                            mHandler.sendMessage(msg)

                            // Extract data from buffer
                            for (i in 0 until numBytesRead) {
                                val c = buffer[i].toInt().toChar()
                                if (c == 'z') {
                                    // This is end signal. Send collected result to UI
                                    if (mCmd.mStringBuffer != null && mCmd.mStringBuffer.length < 20) {
                                        val msg1: Message = mHandler.obtainMessage(
                                            Constants.MSG_READ_DATA,
                                            0,
                                            0,
                                            mCmd.toString()
                                        )
                                        mHandler.sendMessage(msg1)
                                    }
                                } else {
                                    mCmd.addChar(c)
                                }
                            }
                        } // End of if(numBytesRead > 0)
                    } catch (e: IOException) {
                        Log.d(tag, "IOException - mDriver.read")
                        val msg: Message = mHandler.obtainMessage(
                            Constants.MSG_SERIAL_ERROR, 0, 0,
                            "Error # run: $e\n"
                        )
                        mHandler.sendMessage(msg)
                        mKillSign = true
                    }
                }
                try {
                    sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    break
                }
                if (mKillSign) break
            } // End of while() loop

            // Finalize
            finalizeThread()
        } // End of run()
    } // End of SerialMonitorThread


}