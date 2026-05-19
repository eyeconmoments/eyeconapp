package com.eyecon.widget

import android.content.Context

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("eyecon_widget", Context.MODE_PRIVATE)

    var employeeId: Int
        get() = p.getInt("employee_id", -1)
        set(v) = p.edit().putInt("employee_id", v).apply()

    var employeeName: String
        get() = p.getString("employee_name", "") ?: ""
        set(v) = p.edit().putString("employee_name", v).apply()
}
