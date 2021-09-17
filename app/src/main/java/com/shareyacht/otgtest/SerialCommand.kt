package com.shareyacht.otgtest

import java.lang.StringBuilder

class SerialCommand {

    var mStringBuffer: StringBuilder

    init {
        mStringBuffer = StringBuilder()
    }

    private fun initialize() {
        mStringBuffer = StringBuilder()
    }

    fun addChar(c: Char) {
        if (c < 0x00.toChar()) return

        if (c == 'a') {
            initialize()
        } else {
            mStringBuffer.append(c)
        }
    }

    fun toStr(): String {
        return if (mStringBuffer.isNotEmpty()) mStringBuffer.toString()
        else "No data"
    }
}