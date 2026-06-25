package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeminjamDao {
    @Query("SELECT * FROM peminjam ORDER BY nama ASC")
    fun getAllPeminjamFlow(): Flow<List<Peminjam>>

    @Query("SELECT * FROM peminjam ORDER BY nama ASC")
    suspend fun getAllPeminjam(): List<Peminjam>

    @Query("SELECT * FROM peminjam WHERE LOWER(TRIM(nama)) = LOWER(TRIM(:nama)) LIMIT 1")
    suspend fun getPeminjamByNama(nama: String): Peminjam?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeminjam(peminjam: Peminjam)

    @Update
    suspend fun updatePeminjam(peminjam: Peminjam)

    @Query("DELETE FROM peminjam WHERE LOWER(TRIM(nama)) = LOWER(TRIM(:nama))")
    suspend fun deletePeminjam(nama: String): Int

    @Query("DELETE FROM peminjam")
    suspend fun clearAllPeminjam()
}

@Dao
interface HistorySetoranDao {
    @Query("SELECT * FROM history_setoran ORDER BY tanggal DESC")
    fun getAllHistoryFlow(): Flow<List<HistorySetoran>>

    @Insert
    suspend fun insertHistory(history: HistorySetoran)

    @Query("DELETE FROM history_setoran WHERE LOWER(TRIM(namaDebitur)) = LOWER(TRIM(:namaDebitur))")
    suspend fun deleteHistoryByDebitur(namaDebitur: String)
}
