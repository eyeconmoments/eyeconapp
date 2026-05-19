package com.eyecon.widget

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*

class ClockInOutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra("action") ?: "CLOCK_IN"
        val prefs  = Prefs(this)

        if (prefs.employeeId < 0) {
            startActivity(android.content.Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        if (action == "CLOCK_OUT") {
            showClockOut(prefs)
        } else {
            showClockIn(prefs)
        }
    }

    // ── Clock Out ────────────────────────────────────────────────────────────

    private fun showClockOut(prefs: Prefs) {
        setContentView(R.layout.activity_clock_out)
        val tvMsg = findViewById<TextView>(R.id.tv_co_message)
        tvMsg.text = "Clocking out ${prefs.employeeName}…"

        Thread {
            val entryId = SupabaseApi.getMyOpenEntry(prefs.employeeId)
            val ok = entryId?.let { SupabaseApi.clockOut(it) } ?: false
            runOnUiThread {
                tvMsg.text = when {
                    entryId == null -> "You're not currently clocked in."
                    ok              -> "✓ Clocked out — see you next time!"
                    else            -> "Failed — check your connection."
                }
                ClockWidget.refreshAll(this)
                window.decorView.postDelayed({ finish() }, 2000)
            }
        }.start()
    }

    // ── Clock In ─────────────────────────────────────────────────────────────

    private var jobs: List<SupabaseApi.Job> = emptyList()

    private fun showClockIn(prefs: Prefs) {
        setContentView(R.layout.activity_clock_in)

        val tvGreet  = findViewById<TextView>(R.id.tv_ci_greeting)
        val tvAlert  = findViewById<TextView>(R.id.tv_ci_alert)
        val spinner  = findViewById<Spinner>(R.id.spinner_job)
        val btnIn    = findViewById<Button>(R.id.btn_confirm_in)
        val btnSkip  = findViewById<Button>(R.id.btn_no_job)
        val tvStatus = findViewById<TextView>(R.id.tv_ci_status)

        tvGreet.text = "Clock in, ${prefs.employeeName}"
        tvAlert.visibility = View.GONE

        // Check for existing open entry
        Thread {
            val existing = SupabaseApi.getMyOpenEntry(prefs.employeeId)
            if (existing != null) {
                runOnUiThread {
                    tvAlert.text = "⚠ You're already clocked in. Clock out first."
                    tvAlert.visibility = View.VISIBLE
                    btnIn.isEnabled  = false
                    btnSkip.isEnabled = false
                }
            }

            // Load jobs in parallel
            val loaded = SupabaseApi.getJobs()
            runOnUiThread {
                jobs = loaded
                val names = listOf("No specific job") + jobs.map { it.name }
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            }
        }.start()

        btnIn.setOnClickListener {
            val job = if (spinner.selectedItemPosition > 0) jobs[spinner.selectedItemPosition - 1] else null
            doClockIn(prefs.employeeId, job?.id, tvStatus, btnIn, btnSkip)
        }
        btnSkip.setOnClickListener {
            doClockIn(prefs.employeeId, null, tvStatus, btnIn, btnSkip)
        }
    }

    private fun doClockIn(empId: Int, jobId: Int?, tvStatus: TextView, vararg btns: Button) {
        tvStatus.text = "Clocking in…"
        btns.forEach { it.isEnabled = false }
        Thread {
            val ok = SupabaseApi.clockIn(empId, jobId)
            runOnUiThread {
                tvStatus.text = if (ok) "✓ Clocked in!" else "Failed — check your connection."
                ClockWidget.refreshAll(this)
                window.decorView.postDelayed({ finish() }, 1800)
            }
        }.start()
    }
}
