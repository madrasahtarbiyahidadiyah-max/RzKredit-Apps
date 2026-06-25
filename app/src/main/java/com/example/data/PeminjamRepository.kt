package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.regex.Pattern

class PeminjamRepository(
    private val peminjamDao: PeminjamDao,
    private val historySetoranDao: HistorySetoranDao
) {
    val allPeminjam: Flow<List<Peminjam>> = peminjamDao.getAllPeminjamFlow()
    val allHistory: Flow<List<HistorySetoran>> = historySetoranDao.getAllHistoryFlow()

    fun hitungAngsuran(nominal: Double): Double {
        return when (nominal.toInt()) {
            1000000 -> 110000.0
            1500000 -> 170000.0
            2000000 -> 220000.0
            2500000 -> 270000.0
            3000000 -> 320000.0
            3500000 -> 370000.0
            4000000 -> 420000.0
            4500000 -> 470000.0
            5000000 -> 530000.0
            else -> 0.0
        }
    }

    suspend fun syncWithSpreadsheet(spreadsheetUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sheetId = extractSpreadsheetId(spreadsheetUrl)
                ?: return@withContext Result.failure(Exception("Link Spreadsheet tidak valid. Pastikan link memiliki format Google Sheets."))
            
            val csvUrl = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv&sheet=Peminjam"
            
            val request = Request.Builder()
                .url(csvUrl)
                .build()
                
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Gagal mengunduh data: HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Respon kosong dari server"))
                
                val list = parseCsv(body)
                if (list.isEmpty()) {
                    return@withContext Result.failure(Exception("Format data Spreadsheet tidak valid, atau data kosong."))
                }
                
                peminjamDao.clearAllPeminjam()
                list.forEach { peminjamDao.insertPeminjam(it) }
                
                Result.success("✔ Sinkronisasi Berhasil! ${list.size} debitur disinkronkan.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun postToAppsScript(webAppUrl: String, jsonPayload: String): Result<String> = withContext(Dispatchers.IO) {
        if (webAppUrl.trim().isEmpty()) return@withContext Result.success("Offline Mode")
        try {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonPayload.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(webAppUrl)
                .post(requestBody)
                .build()
            
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
                
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 302 || response.code == 307) {
                    Result.success(bodyStr)
                } else {
                    Result.failure(Exception("Google Cloud Error: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractSpreadsheetId(url: String): String? {
        val pattern = Pattern.compile("/d/([a-zA-Z0-9-_]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun parseCsv(csvText: String): List<Peminjam> {
        val list = mutableListOf<Peminjam>()
        val lines = csvText.split("\n")
        if (lines.size <= 1) return list
        
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            val parts = parseCsvLine(line)
            if (parts.size >= 8) {
                val nama = parts[0]
                val nominal = parts[1].toDoubleOrNull() ?: 0.0
                val tenor = parts[2].toIntOrNull() ?: 12
                val angsuran = parts[3].toDoubleOrNull() ?: 0.0
                val totalTagihan = parts[4].toDoubleOrNull() ?: 0.0
                val terbayar = parts[5].toDoubleOrNull() ?: 0.0
                val sisaTagihan = parts[6].toDoubleOrNull() ?: 0.0
                val sisaTenor = parts[7].toIntOrNull() ?: 12
                
                list.add(
                    Peminjam(
                        nama = nama,
                        nominal = nominal,
                        tenor = tenor,
                        angsuran = angsuran,
                        totalTagihan = totalTagihan,
                        terbayar = terbayar,
                        sisaTagihan = sisaTagihan,
                        sisaTenor = sisaTenor
                    )
                )
            }
        }
        return list
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    suspend fun simpanPeminjam(nama: String, nominal: Double, tenor: Int, webAppUrl: String = ""): Result<String> {
        val trimmedNama = nama.trim()
        if (trimmedNama.isEmpty()) return Result.failure(Exception("Nama debitur wajib diisi!"))
        
        val existing = peminjamDao.getPeminjamByNama(trimmedNama)
        if (existing != null) {
            return Result.failure(Exception("Debitur dengan nama '$trimmedNama' sudah terdaftar."))
        }

        val angsuran = hitungAngsuran(nominal)
        val totalTagihan = angsuran * tenor

        val peminjam = Peminjam(
            nama = trimmedNama,
            nominal = nominal,
            tenor = tenor,
            angsuran = angsuran,
            totalTagihan = totalTagihan,
            terbayar = 0.0,
            sisaTagihan = totalTagihan,
            sisaTenor = tenor
        )

        // Cloud sync check
        if (webAppUrl.trim().isNotEmpty()) {
            val payload = """
                {
                    "action": "add",
                    "nama": "$trimmedNama",
                    "nominal": $nominal,
                    "tenor": $tenor,
                    "angsuran": $angsuran,
                    "totalTagihan": $totalTagihan,
                    "terbayar": 0.0,
                    "sisaTagihan": $totalTagihan,
                    "sisaTenor": $tenor
                }
            """.trimIndent()
            val syncRes = postToAppsScript(webAppUrl, payload)
            if (syncRes.isFailure) {
                return Result.failure(Exception("Gagal Sinkronisasi Cloud: ${syncRes.exceptionOrNull()?.message}"))
            }
        }

        peminjamDao.insertPeminjam(peminjam)
        return Result.success("✔ Kontrak Sudah Disimpan & Sinkron ke Spreadsheet")
    }

    suspend fun simpanSetoran(nama: String, jumlahSetoran: Double, webAppUrl: String = ""): Result<String> {
        val trimmedNama = nama.trim()
        val peminjam = peminjamDao.getPeminjamByNama(trimmedNama)
            ?: return Result.failure(Exception("Nasabah tidak ditemukan."))

        val newTerbayar = peminjam.terbayar + jumlahSetoran
        val newSisaTagihan = (peminjam.totalTagihan - newTerbayar).coerceAtLeast(0.0)

        var newSisaTenor = peminjam.sisaTenor
        if (jumlahSetoran >= peminjam.angsuran && peminjam.sisaTenor > 0) {
            newSisaTenor -= 1
        }

        val updatedPeminjam = peminjam.copy(
            terbayar = newTerbayar,
            sisaTagihan = newSisaTagihan,
            sisaTenor = newSisaTenor
        )

        // Cloud sync check
        if (webAppUrl.trim().isNotEmpty()) {
            val payload = """
                {
                    "action": "setoran",
                    "nama": "$trimmedNama",
                    "terbayar": $newTerbayar,
                    "sisaTagihan": $newSisaTagihan,
                    "sisaTenor": $newSisaTenor
                }
            """.trimIndent()
            val syncRes = postToAppsScript(webAppUrl, payload)
            if (syncRes.isFailure) {
                return Result.failure(Exception("Gagal Sinkronisasi Cloud: ${syncRes.exceptionOrNull()?.message}"))
            }
        }

        peminjamDao.updatePeminjam(updatedPeminjam)

        val history = HistorySetoran(
            namaDebitur = trimmedNama,
            jumlahSetoran = jumlahSetoran
        )
        historySetoranDao.insertHistory(history)

        return Result.success("💰 Terimakasih, Uang Sudah Masuk & Sinkron ke Spreadsheet")
    }

    suspend fun hapusPeminjam(nama: String, webAppUrl: String = ""): Result<String> {
        // Cloud sync check
        if (webAppUrl.trim().isNotEmpty()) {
            val payload = """
                {
                    "action": "delete",
                    "nama": "$nama"
                }
            """.trimIndent()
            val syncRes = postToAppsScript(webAppUrl, payload)
            if (syncRes.isFailure) {
                return Result.failure(Exception("Gagal Sinkronisasi Cloud: ${syncRes.exceptionOrNull()?.message}"))
            }
        }

        val count = peminjamDao.deletePeminjam(nama)
        return if (count > 0) {
            historySetoranDao.deleteHistoryByDebitur(nama)
            Result.success("Data debitur '$nama' berhasil dilenyapkan dari lokal & cloud.")
        } else {
            Result.failure(Exception("Gagal: Nasabah tidak ditemukan di database lokal."))
        }
    }
}
