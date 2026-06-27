package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peminjam")
data class Peminjam(
    @PrimaryKey val nama: String,
    val nominal: Double,      // Plafon Pinjaman
    val tenor: Int,           // Tenor Kontrak (12 atau 24)
    val angsuran: Double,     // Angsuran Per Bulan
    val totalTagihan: Double, // Total Tagihan Awal
    val terbayar: Double,     // Terbayar
    val sisaTagihan: Double,  // Sisa Tagihan
    val sisaTenor: Int,       // Sisa Tenor
    val rowOrder: Int = 0     // Row Order in Google Sheet (newer rows have higher order)
)

@Entity(tableName = "history_setoran")
data class HistorySetoran(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tanggal: Long = System.currentTimeMillis(),
    val namaDebitur: String,
    val jumlahSetoran: Double
)
