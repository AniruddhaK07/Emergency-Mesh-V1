package com.bitchat.emergency.queue

import android.content.Context
import com.bitchat.emergency.model.EmergencyType
import com.bitchat.emergency.model.IncidentReport
import com.bitchat.emergency.model.Severity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ReportQueue(context: Context) {
    private val queueFile = File(context.filesDir, "report_queue.json")
    private val queue = mutableListOf<IncidentReport>()

    init {
        loadFromDisk()
    }

    fun enqueue(report: IncidentReport) {
        queue.add(report)
        saveToDisk()
    }

    fun dequeue(): IncidentReport? {
        // Reload from disk to pick up reports enqueued by other components
        // (e.g., ReportActivity writes to the same file but holds a separate
        // ReportQueue instance). The file is the source of truth.
        loadFromDisk()
        if (queue.isEmpty()) return null
        val report = queue.removeAt(0)
        saveToDisk()
        return report
    }

    fun peek(): List<IncidentReport> {
        return queue.toList()
    }

    fun size(): Int {
        return queue.size
    }

    private fun loadFromDisk() {
        if (!queueFile.exists()) return
        queue.clear()
        try {
            val jsonText = queueFile.readText()
            val array = JSONArray(jsonText)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val report = IncidentReport(
                    emergencyType = EmergencyType.valueOf(obj.getString("emergencyType")),
                    casualtyCount = obj.getInt("casualtyCount"),
                    severity = Severity.valueOf(obj.getString("severity")),
                    notes = obj.getString("notes"),
                    timestamp = obj.getLong("timestamp"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    corroborationCount = obj.optInt("corroborationCount", 1)
                )
                queue.add(report)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveToDisk() {
        try {
            val array = JSONArray()
            for (report in queue) {
                val obj = JSONObject()
                obj.put("emergencyType", report.emergencyType.name)
                obj.put("casualtyCount", report.casualtyCount)
                obj.put("severity", report.severity.name)
                obj.put("notes", report.notes)
                obj.put("timestamp", report.timestamp)
                obj.put("latitude", report.latitude)
                obj.put("longitude", report.longitude)
                obj.put("corroborationCount", report.corroborationCount)
                array.put(obj)
            }
            queueFile.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
