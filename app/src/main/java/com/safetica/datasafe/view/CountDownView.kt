package com.safetica.datasafe.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.safetica.datasafe.R
import kotlinx.android.synthetic.main.timer_layout.view.*

class CountDownView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    init {
        val inflater = context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.timer_layout, this, true)
    }

    fun setTimerDigits(value: Long) {
        counterDigits.text = value.toString()
    }
}