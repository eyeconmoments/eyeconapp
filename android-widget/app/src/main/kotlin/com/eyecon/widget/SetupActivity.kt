package com.eyecon.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        // Default result = cancelled (so Android removes the widget if user backs out)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        val prefs   = Prefs(this)
        val etPin   = findViewById<EditText>(R.id.et_pin)
        val btnSave = findViewById<Button>(R.id.btn_save_pin)
        val tvStatus = findViewById<TextView>(R.id.tv_setup_status)

        // Pre-fill if already set up
        if (prefs.employeeId >= 0) {
            tvStatus.text = "Currently set up as: ${prefs.employeeName}\nEnter a new PIN to switch."
        }

        btnSave.setOnClickListener {
            val pin = etPin.text.toString().trim()
            if (pin.isEmpty()) { tvStatus.text = "Please enter your PIN."; return@setOnClickListener }

            tvStatus.text = "Looking up…"
            btnSave.isEnabled = false

            Thread {
                val emp = SupabaseApi.findEmployeeByPin(pin)
                runOnUiThread {
                    if (emp == null) {
                        tvStatus.text = "PIN not recognised. Try again."
                        btnSave.isEnabled = true
                    } else {
                        prefs.employeeId   = emp.id
                        prefs.employeeName = emp.name
                        tvStatus.text      = "✓ Saved — welcome, ${emp.name}!"

                        // Trigger initial widget render if launched from widget add flow
                        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            ClockWidget.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        }
                        window.decorView.postDelayed({ finish() }, 1500)
                    }
                }
            }.start()
        }
    }
}
