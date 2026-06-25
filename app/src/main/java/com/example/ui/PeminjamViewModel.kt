package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HistorySetoran
import com.example.data.Peminjam
import com.example.data.PeminjamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardStats(
    val totalNasabah: Int = 0,
    val totalUangDihutang: Double = 0.0,
    val totalUangMasuk: Double = 0.0,
    val totalPiutang: Double = 0.0
)

class PeminjamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PeminjamRepository
    private val sharedPrefs = application.getSharedPreferences("RzKreditPrefs", android.content.Context.MODE_PRIVATE)

    var spreadsheetUrl by mutableStateOf(
        let {
            val saved = sharedPrefs.getString("spreadsheet_url", "")
            if (saved.isNullOrBlank() || !saved.startsWith("http")) {
                val defaultUrl = "https://docs.google.com/spreadsheets/d/1fEwG17Ii7GLqDIvY2X5D8TQ5p5wq8-0qHJrWX6vnVVU/edit?usp=sharing"
                sharedPrefs.edit().putString("spreadsheet_url", defaultUrl).apply()
                defaultUrl
            } else {
                saved
            }
        }
    )

    var isSyncing by mutableStateOf(false)
        private set

    var showSyncSettings by mutableStateOf(false)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = PeminjamRepository(db.peminjamDao(), db.historySetoranDao())
    }

    fun saveSyncPrefs(sheetUrl: String) {
        spreadsheetUrl = sheetUrl.trim()
        sharedPrefs.edit().apply {
            putString("spreadsheet_url", spreadsheetUrl)
            apply()
        }
        showToast("💾 Konfigurasi Cloud berhasil disimpan.")
    }

    fun syncDataWithSpreadsheet() {
        if (spreadsheetUrl.trim().isEmpty()) {
            showToast("⚠️ Link Google Spreadsheet tidak boleh kosong!")
            return
        }
        viewModelScope.launch {
            isSyncing = true
            val result = repository.syncWithSpreadsheet(spreadsheetUrl)
            isSyncing = false
            result.onSuccess { msg ->
                showToast(msg)
            }.onFailure { err ->
                showToast("❌ Gagal Sinkronisasi: ${err.message}")
            }
        }
    }

    // Flow lists
    val peminjamList: StateFlow<List<Peminjam>> = repository.allPeminjam
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyList: StateFlow<List<HistorySetoran>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Aggregated Stats
    val stats: StateFlow<DashboardStats> = peminjamList.map { list ->
        DashboardStats(
            totalNasabah = list.size,
            totalUangDihutang = list.sumOf { it.nominal },
            totalUangMasuk = list.sumOf { it.terbayar },
            totalPiutang = list.sumOf { it.sisaTagihan }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    // LOGIN STATE
    var isLoggedIn by mutableStateOf(false)
        private set
    var usernameInput by mutableStateOf("")
    var passwordInput by mutableStateOf("")
    var isPasswordVisible by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)

    // REGISTRATION STATE
    var regNama by mutableStateOf("")
    var regNominal by mutableStateOf(1000000.0)
    var regTenor by mutableStateOf(12)

    // SETORAN STATE
    var setorNama by mutableStateOf("")
    var setorJumlah by mutableStateOf("")

    // DELETE AUTHORIZATION STATE
    var showDeleteConfirm by mutableStateOf(false)
    var deleteTargetNama by mutableStateOf("")
    var deleteSandiInput by mutableStateOf("")
    var deleteError by mutableStateOf<String?>(null)

    // STRUK PREVIEW STATE
    var showStrukPreview by mutableStateOf(false)
    var strukTargetPeminjam by mutableStateOf<Peminjam?>(null)

    // TOAST / NOTIFICATION STATE
    var toastMessage by mutableStateOf<String?>(null)

    fun login() {
        val u = usernameInput.trim().lowercase()
        val p = passwordInput.trim()
        if (u == "rzkarim" && (p == "rzkarim123" || p == "admin" || p == "123")) {
            isLoggedIn = true
            loginError = null
            showToast("🚪 Berhasil masuk ke dashboard.")
        } else {
            loginError = "❌ Username atau Sandi Salah!"
        }
    }

    fun logout() {
        isLoggedIn = false
        usernameInput = ""
        passwordInput = ""
        loginError = null
        showToast("🚪 Berhasil keluar dari sistem.")
    }

    fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
    }

    fun showToast(message: String) {
        toastMessage = message
    }

    fun clearToast() {
        toastMessage = null
    }

    fun simpanPeminjamBaru() {
        if (regNama.trim().isEmpty()) {
            showToast("⚠️ Nama debitur wajib diisi!")
            return
        }
        viewModelScope.launch {
            val result = repository.simpanPeminjam(regNama, regNominal, regTenor)
            result.onSuccess { msg ->
                showToast(msg)
                regNama = ""
                regNominal = 1000000.0
                regTenor = 12
            }.onFailure { err ->
                showToast("❌ Gagal: ${err.message}")
            }
        }
    }

    fun otomatisIsiAngsuran(nama: String) {
        setorNama = nama
        if (nama.isEmpty()) {
            setorJumlah = ""
            return
        }
        viewModelScope.launch {
            val list = peminjamList.value
            val target = list.find { it.nama.equals(nama, ignoreCase = true) }
            if (target != null) {
                val rp = repository.hitungAngsuran(target.nominal)
                setorJumlah = rp.toInt().toString()
            }
        }
    }

    fun simpanSetoranTunai() {
        if (setorNama.isEmpty()) {
            showToast("⚠️ Pilih debitur terlebih dahulu!")
            return
        }
        val nominalSetor = setorJumlah.toDoubleOrNull()
        if (nominalSetor == null || nominalSetor <= 0.0) {
            showToast("⚠️ Jumlah setoran tidak valid!")
            return
        }
        viewModelScope.launch {
            val result = repository.simpanSetoran(setorNama, nominalSetor)
            result.onSuccess { msg ->
                showToast(msg)
                setorNama = ""
                setorJumlah = ""
            }.onFailure { err ->
                showToast("❌ Gagal: ${err.message}")
            }
        }
    }

    fun requestHapusDebitur(nama: String) {
        deleteTargetNama = nama
        deleteSandiInput = ""
        deleteError = null
        showDeleteConfirm = true
    }

    fun tutupDeleteConfirm() {
        showDeleteConfirm = false
        deleteTargetNama = ""
        deleteSandiInput = ""
        deleteError = null
    }

    fun eksekusiHapusDebitur() {
        if (deleteSandiInput == "rzkarim123") {
            val target = deleteTargetNama
            viewModelScope.launch {
                val result = repository.hapusPeminjam(target)
                result.onSuccess { msg ->
                    showToast(msg)
                    tutupDeleteConfirm()
                }.onFailure { err ->
                    deleteError = "❌ ${err.message}"
                }
            }
        } else {
            deleteError = "❌ Sandi salah! Hak akses ditolak."
        }
    }

    fun cetakStruk(peminjam: Peminjam) {
        strukTargetPeminjam = peminjam
        showStrukPreview = true
    }

    fun tutupStrukPreview() {
        showStrukPreview = false
        strukTargetPeminjam = null
    }

    fun hitungAngsuranLokal(nominal: Double): Double {
        return repository.hitungAngsuran(nominal)
    }
}
