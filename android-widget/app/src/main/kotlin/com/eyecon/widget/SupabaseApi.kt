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
        val jobName: String?
    )

    data class Job(val id: Int, val name: String)

    data class Employee(val id: Int, val name: String)

    private fun req(url: String) = Request.Builder()
        .url(url)
        .header("apikey", KEY)
        .header("Authorization", "Bearer $KEY")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")

    /** Returns all employees currently clocked in (clock_out IS NULL). */
    fun getOpenEntries(): List<ClockedInEntry> = try {
        val entries = http.newCall(req("$BASE/time_entries?clock_out=is.null&select=id,employee_id,job_id").get().build())
            .execute().use { res -> if (!res.isSuccessful) return emptyList(); JSONArray(res.body!!.string()) }

        val empMap = buildMap<Int, String> {
            http.newCall(req("$BASE/employees?select=id,name").get().build()).execute()
                .use { res -> if (res.isSuccessful) JSONArray(res.body!!.string()).let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let { put(it.getInt("id"), it.getString("name")) }
                }}
        }
        val jobMap = buildMap<Int, String> {
            http.newCall(req("$BASE/jobs?archived=is.false&select=id,job_name").get().build()).execute()
                .use { res -> if (res.isSuccessful) JSONArray(res.body!!.string()).let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let { put(it.getInt("id"), it.getString("job_name")) }
                }}
        }

        buildList {
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                val empId = e.getInt("employee_id")
                val jobId = if (e.isNull("job_id")) null else e.getInt("job_id")
                add(ClockedInEntry(e.getLong("id"), empId, empMap[empId] ?: "Unknown", jobId?.let { jobMap[it] }))
            }
        }
    } catch (_: Exception) { emptyList() }

    /** Finds the open time_entry ID for this employee, or null if not clocked in. */
    fun getMyOpenEntry(employeeId: Int): Long? = try {
        http.newCall(req("$BASE/time_entries?employee_id=eq.$employeeId&clock_out=is.null&select=id&limit=1").get().build())
            .execute().use { res ->
                if (!res.isSuccessful) null
                else JSONArray(res.body!!.string()).let { if (it.length() > 0) it.getJSONObject(0).getLong("id") else null }
            }
    } catch (_: Exception) { null }

    fun clockIn(employeeId: Int, jobId: Int?): Boolean = try {
        val body = JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("employee_id", employeeId)
            if (jobId != null) put("job_id", jobId)
            put("clock_in", Instant.now().toString())
        }.toString().toRequestBody(JSON_TYPE)

        http.newCall(req("$BASE/time_entries").post(body).header("Prefer", "return=minimal").build())
            .execute().use { it.isSuccessful }
    } catch (_: Exception) { false }

    fun clockOut(entryId: Long): Boolean = try {
        val body = JSONObject().apply { put("clock_out", Instant.now().toString()) }
            .toString().toRequestBody(JSON_TYPE)

        http.newCall(req("$BASE/time_entries?id=eq.$entryId").patch(body).build())
            .execute().use { it.isSuccessful }
    } catch (_: Exception) { false }

    fun getJobs(): List<Job> = try {
        http.newCall(req("$BASE/jobs?archived=is.false&select=id,job_name&order=job_name.asc").get().build())
            .execute().use { res ->
                if (!res.isSuccessful) emptyList()
                else JSONArray(res.body!!.string()).let { arr ->
                    buildList { for (i in 0 until arr.length()) arr.getJSONObject(i).let { add(Job(it.getInt("id"), it.getString("job_name"))) } }
                }
            }
    } catch (_: Exception) { emptyList() }

    fun findEmployeeByPin(pin: String): Employee? = try {
        http.newCall(req("$BASE/employees?pin=eq.$pin&select=id,name&limit=1").get().build())
            .execute().use { res ->
                if (!res.isSuccessful) null
                else JSONArray(res.body!!.string()).let { if (it.length() > 0) it.getJSONObject(0).let { e -> Employee(e.getInt("id"), e.getString("name")) } else null }
            }
    } catch (_: Exception) { null }
}
