package com.eyecon.widget

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

object SupabaseApi {
    private const val BASE = "https://wgqamqzlfnjcqyprphkw.supabase.co/rest/v1"
    private const val KEY  = "sb_publishable_lWHxlKp0imCmSFHs3KF78w_2KFrEJBE"
    private val JSON_TYPE  = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ClockedInEntry(
        val entryId: Long,
        val employeeId: Int,
        val employeeName: String,
        val jobName: String?,
        val progressPercent: Int?
    )

    data class Job(val id: Int, val name: String)

    data class Employee(val id: Int, val name: String)

    private fun req(url: String) = Request.Builder()
        .url(url)
        .header("apikey", KEY)
        .header("Authorization", "Bearer $KEY")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")

    fun getOpenEntries(): List<ClockedInEntry> {
        return try {
            val entryRes = http.newCall(
                req("$BASE/time_entries?clock_out=is.null&select=id,employee_id,job_id,progress_percent").get().build()
            ).execute()
            if (!entryRes.isSuccessful) return emptyList()
            val entries = JSONArray(entryRes.body!!.string())

            val empMap = mutableMapOf<Int, String>()
            val empRes = http.newCall(req("$BASE/employees?select=id,name").get().build()).execute()
            if (empRes.isSuccessful) {
                val arr = JSONArray(empRes.body!!.string())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    empMap[o.getInt("id")] = o.getString("name")
                }
            }

            val jobMap = mutableMapOf<Int, String>()
            val jobRes = http.newCall(req("$BASE/jobs?archived=not.is.true&select=id,job_name").get().build()).execute()
            if (jobRes.isSuccessful) {
                val arr = JSONArray(jobRes.body!!.string())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    jobMap[o.getInt("id")] = o.getString("job_name")
                }
            }

            val result = mutableListOf<ClockedInEntry>()
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                val empId = e.getInt("employee_id")
                val jobId = if (e.isNull("job_id")) null else e.getInt("job_id")
                val pct = if (e.isNull("progress_percent")) null else e.getInt("progress_percent")
                result.add(ClockedInEntry(e.getLong("id"), empId, empMap[empId] ?: "Unknown", jobId?.let { jobMap[it] }, pct))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getMyOpenEntry(employeeId: Int): Long? {
        return try {
            val res = http.newCall(
                req("$BASE/time_entries?employee_id=eq.$employeeId&clock_out=is.null&select=id&limit=1").get().build()
            ).execute()
            if (!res.isSuccessful) return null
            val arr = JSONArray(res.body!!.string())
            if (arr.length() > 0) arr.getJSONObject(0).getLong("id") else null
        } catch (e: Exception) {
            null
        }
    }

    fun clockIn(employeeId: Int, jobId: Int?): Boolean {
        return try {
            val json = JSONObject()
            json.put("id", System.currentTimeMillis())
            json.put("employee_id", employeeId)
            if (jobId != null) json.put("job_id", jobId)
            json.put("clock_in", Instant.now().toString())
            val body = json.toString().toRequestBody(JSON_TYPE)
            val res = http.newCall(
                req("$BASE/time_entries").post(body).header("Prefer", "return=minimal").build()
            ).execute()
            res.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    fun clockOut(entryId: Long): Boolean {
        return try {
            val json = JSONObject()
            json.put("clock_out", Instant.now().toString())
            val body = json.toString().toRequestBody(JSON_TYPE)
            val res = http.newCall(
                req("$BASE/time_entries?id=eq.$entryId").patch(body).build()
            ).execute()
            res.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /** Returns null on network/auth error, empty list if there are genuinely no jobs. */
    fun getJobs(): List<Job>? {
        return try {
            val res = http.newCall(
                req("$BASE/jobs?select=id,job_name&order=job_name.asc").get().build()
            ).execute()
            if (!res.isSuccessful) return null   // surface API errors to the caller
            val arr = JSONArray(res.body!!.string())
            val result = mutableListOf<Job>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(Job(o.getInt("id"), o.getString("job_name")))
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    fun findEmployeeByPin(pin: String): Employee? {
        return try {
            val res = http.newCall(
                req("$BASE/employees?pin=eq.$pin&select=id,name&limit=1").get().build()
            ).execute()
            if (!res.isSuccessful) return null
            val arr = JSONArray(res.body!!.string())
            if (arr.length() == 0) return null
            val o = arr.getJSONObject(0)
            Employee(o.getInt("id"), o.getString("name"))
        } catch (e: Exception) {
            null
        }
    }
}
