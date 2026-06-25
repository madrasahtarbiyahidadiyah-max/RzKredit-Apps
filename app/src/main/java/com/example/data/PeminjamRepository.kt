package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.regex.Pattern
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale

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
            
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            // 1. Fetch Peminjam Sheet
            val urlPeminjam = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv&sheet=Peminjam"
            val requestPeminjam = Request.Builder().url(urlPeminjam).build()
            
            val peminjamList = client.newCall(requestPeminjam).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Gagal mengunduh sheet Peminjam: HTTP ${response.code}")
                }
                val body = response.body?.string() ?: ""
                parseCsv(body)
            }

            if (peminjamList.isEmpty()) {
                return@withContext Result.failure(Exception("Data 'Peminjam' kosong atau format tidak sesuai di Spreadsheet."))
            }

            // 2. Fetch HistorySetoran Sheet
            val urlHistory = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv&sheet=HistorySetoran"
            val requestHistory = Request.Builder().url(urlHistory).build()
            
            val historyList = try {
                client.newCall(requestHistory).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        parseHistoryCsv(body)
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                // If HistorySetoran sheet doesn't exist yet, or fails, we can fallback to empty list
                emptyList()
            }

            // 3. Clear database and update with fresh data
            peminjamDao.clearAllPeminjam()
            peminjamList.forEach { peminjamDao.insertPeminjam(it) }

            historySetoranDao.clearAllHistory()
            historyList.forEach { historySetoranDao.insertHistory(it) }
            
            var msg = "✔ Sinkronisasi Berhasil! ${peminjamList.size} debitur disinkronkan."
            if (historyList.isNotEmpty()) {
                msg += " Serta ${historyList.size} riwayat setoran diunduh."
            }
            Result.success(msg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseHistoryCsv(csvText: String): List<HistorySetoran> {
        val list = mutableListOf<HistorySetoran>()
        val lines = csvText.split("\n")
        if (lines.size <= 1) return list
        
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            val parts = parseCsvLine(line)
            if (parts.size >= 3) {
                val tanggalStr = parts[0]
                val namaDebitur = parts[1]
                val jumlahSetoran = parts[2].toDoubleOrNull() ?: 0.0
                
                if (namaDebitur.isNotEmpty() && jumlahSetoran > 0) {
                    val tanggalLong = parseSheetDate(tanggalStr)
                    list.add(
                        HistorySetoran(
                            tanggal = tanggalLong,
                            namaDebitur = namaDebitur,
                            jumlahSetoran = jumlahSetoran
                        )
                    )
                }
            }
        }
        return list
    }

    private fun parseSheetDate(dateStr: String): Long {
        val formats = listOf(
            "M/d/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "d/M/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss",
            "M/d/yyyy H:mm:ss",
            "d/M/yyyy H:mm:ss"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                val date = sdf.parse(dateStr.trim())
                if (date != null) return date.time
            } catch (e: Exception) {
                // ignore and try next
            }
        }
        try {
            return dateStr.trim().toLong()
        } catch (e: Exception) {
            // ignore
        }
        return System.currentTimeMillis()
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

    suspend fun simpanPeminjam(nama: String, nominal: Double, tenor: Int): Result<String> {
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

        peminjamDao.insertPeminjam(peminjam)
        return Result.success("✔ Kontrak Sudah Disimpan")
    }

    suspend fun simpanSetoran(nama: String, jumlahSetoran: Double): Result<String> {
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

        peminjamDao.updatePeminjam(updatedPeminjam)

        val history = HistorySetoran(
            namaDebitur = trimmedNama,
            jumlahSetoran = jumlahSetoran
        )
        historySetoranDao.insertHistory(history)

        return Result.success("💰 Terimakasih, Uang Sudah Masuk")
    }

    suspend fun hapusPeminjam(nama: String): Result<String> {
        val count = peminjamDao.deletePeminjam(nama)
        return if (count > 0) {
            historySetoranDao.deleteHistoryByDebitur(nama)
            Result.success("Data debitur '$nama' berhasil dilenyapkan dari lokal.")
        } else {
            Result.failure(Exception("Gagal: Nasabah tidak ditemukan di database lokal."))
        }
    }
}
