package com.example.ecoscale_android

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Data Class untuk Chart Donat/Pie
data class DoughnutData(
    val organik: Double = 0.0,
    val anorganik: Double = 0.0,
    val residu: Double = 0.0
)

// Data Class Baru: Untuk Stacked Bar Chart per Fakultas
data class FacultyWasteData(
    val fakultas: String,
    val organik: Double = 0.0,
    val anorganik: Double = 0.0,
    val residu: Double = 0.0
)

data class WasteTransaction(
    val id: String,
    val date: Date,
    val fakultas: String,
    val jenis: String,
    val berat: Double
)

class DashboardViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val TAG = "DashboardViewModel"

    // --- LIVE DATA UI (LAMA: Overview) ---
    private val _weeklyTotalData = MutableLiveData<List<Double>>(List(7) { 0.0 })
    val weeklyTotalData: LiveData<List<Double>> = _weeklyTotalData

    private val _doughnutData = MutableLiveData<DoughnutData>(DoughnutData())
    val doughnutData: LiveData<DoughnutData> = _doughnutData

    val totalSampahToday: LiveData<Double> = MediatorLiveData<Double>().apply {
        addSource(_doughnutData) { data ->
            value = data.organik + data.anorganik + data.residu
        }
    }

    val currentDate: LiveData<String> = MutableLiveData<String>().apply {
        val localeId = Locale.Builder().setLanguage("id").setRegion("ID").build()
        val dateFormat = SimpleDateFormat("📍' Semarang, Indonesia  •  'EEEE, dd MMMM yyyy", localeId)
        value = dateFormat.format(Date())
    }

    // --- LIVE DATA UI (BARU: Analitik) ---

    // 1. Data Komposisi Per Fakultas (List)
    private val _facultyComposition = MutableLiveData<List<FacultyWasteData>>()
    val facultyComposition: LiveData<List<FacultyWasteData>> = _facultyComposition

    // 2. Data Distribusi Mingguan vs Bulanan
    private val _weeklyDist = MutableLiveData<DoughnutData>()
    val weeklyDist: LiveData<DoughnutData> = _weeklyDist

    private val _monthlyDist = MutableLiveData<DoughnutData>()
    val monthlyDist: LiveData<DoughnutData> = _monthlyDist

    // 3. Persentase Perubahan (Hari ini vs Kemarin)
    // Nilai positif = sampah naik (buruk), negatif = sampah turun (bagus)
    private val _dailyChangePercentage = MutableLiveData<Double>(0.0)
    val dailyChangePercentage: LiveData<Double> = _dailyChangePercentage

    // 4. Fakultas Aktif (Jumlah fakultas yang setor sampah bulan ini)
    private val _fakultasAktif = MutableLiveData<Int>(0)
    val fakultasAktif: LiveData<Int> = _fakultasAktif

    // 5. Status Upload (Untuk Input Manual)
    private val _uploadStatus = MutableLiveData<String?>(null)
    val uploadStatus: LiveData<String?> = _uploadStatus

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // --- LIVE DATA UI (BARU: Laporan) ---
    private val _transactionHistory = MutableLiveData<List<WasteTransaction>>()
    val transactionHistory: LiveData<List<WasteTransaction>> = _transactionHistory

    private val _co2Reduced = MutableLiveData<Double>(0.0)
    val co2Reduced: LiveData<Double> = _co2Reduced




    init {
        setupFirebaseListener()
    }

    // Upload Data Manual
    fun uploadManualData(berat: Double, jenis: String, fakultas: String) {
        _isLoading.value = true

        // Buat object data sesuai struktur Firestore kamu
        val data = hashMapOf(
            "berat" to berat,
            "jenis" to jenis,
            "fakultas" to fakultas,
            "timestamp" to Timestamp.now(), // Waktu server saat ini
            "sumber" to "Manual" // Opsional: penanda bahwa ini bukan dari sensor
        )

        db.collection("sampah")
            .add(data)
            .addOnSuccessListener {
                _isLoading.value = false
                _uploadStatus.value = "Sukses! Data berhasil disimpan."
                // Reset status pesan setelah beberapa detik bisa dihandle di UI
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _uploadStatus.value = "Gagal: ${e.message}"
                Log.e(TAG, "Error adding document", e)
            }
    }

    // Fungsi untuk mereset pesan status agar tidak muncul terus saat rotasi layar
    fun clearUploadStatus() {
        _uploadStatus.value = null
    }
    private fun setupFirebaseListener() {

        // --- 1. Definisi Rentang Waktu ---

        // Hari Ini (Mulai 00:00)
        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calToday.time

        // Kemarin (Mulai 00:00 kemarin)
        val calYesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfYesterday = calYesterday.time

        // Awal Minggu Ini (Senin)
        val calWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfWeek = calWeek.time

        // Awal Bulan Ini (Tanggal 1)
        val calMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = calMonth.time

        // Batas Query (6 Bulan lalu agar aman)
        val calSixMonths = Calendar.getInstance().apply {
            add(Calendar.MONTH, -5)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val sixMonthsAgoTimestamp = Timestamp(calSixMonths.time)

        // --- 2. Query Firestore ---
        val query = db.collection("sampah")
            .whereGreaterThanOrEqualTo("timestamp", sixMonthsAgoTimestamp)

        query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot == null) return@addSnapshotListener

            // --- 3. Variabel Penampung (Temporary) ---

            // Untuk Overview (Hari Ini)
            var tempOrganikToday = 0.0
            var tempAnorganikToday = 0.0
            var tempResiduToday = 0.0
            val tempWeeklyTrend = DoubleArray(7) { 0.0 }

            // Untuk Analitik
            var totalTodayAll = 0.0
            var totalYesterdayAll = 0.0
            val tempFacultyMap = mutableMapOf<String, FacultyWasteData>() // Map Fakultas -> Data
            val tempWeeklyType = mutableMapOf("Organik" to 0.0, "Anorganik" to 0.0, "Residu" to 0.0)
            val tempMonthlyType = mutableMapOf("Organik" to 0.0, "Anorganik" to 0.0, "Residu" to 0.0)

            // --- 4. Loop Data ---
            for (doc in snapshot.documents) {
                val timestamp = (doc.get("timestamp") as? Timestamp)?.toDate()
                val berat = (doc.get("berat") as? Number)?.toDouble() ?: 0.0
                val jenis = doc.getString("jenis") ?: "Lainnya"
                val fakultas = doc.getString("fakultas") ?: "Lainnya" // Pastikan field ini ada di Firestore

                if (timestamp == null || jenis == "Umum") continue

                val time = timestamp.time

                // A. Logika Hari Ini (Overview)
                if (time >= startOfToday.time) {
                    when (jenis) {
                        "Organik" -> tempOrganikToday += berat
                        "Anorganik" -> tempAnorganikToday += berat
                        "Residu" -> tempResiduToday += berat
                    }
                    totalTodayAll += berat
                }

                // B. Logika Kemarin (Analitik - Persentase)
                if (time >= startOfYesterday.time && time < startOfToday.time) {
                    totalYesterdayAll += berat
                }

                // C. Logika Tren Mingguan (Overview - Line Chart)
                if (time >= startOfWeek.time) {
                    val calDoc = Calendar.getInstance().apply { this.time = timestamp }
                    val dayOfWeek = calDoc.get(Calendar.DAY_OF_WEEK)
                    val index = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                    if (index in 0..6) tempWeeklyTrend[index] += berat

                    // Analitik: Distribusi Jenis Minggu Ini
                    tempWeeklyType[jenis] = (tempWeeklyType[jenis] ?: 0.0) + berat
                }

                // D. Logika Bulan Ini (Analitik - Fakultas & Distribusi)
                if (time >= startOfMonth.time) {
                    // Distribusi Jenis Bulan Ini
                    tempMonthlyType[jenis] = (tempMonthlyType[jenis] ?: 0.0) + berat

                    // Komposisi Per Fakultas (Hanya hitung data bulan ini agar relevan)
                    val currentFacData = tempFacultyMap.getOrDefault(fakultas, FacultyWasteData(fakultas))
                    val newFacData = when(jenis) {
                        "Organik" -> currentFacData.copy(organik = currentFacData.organik + berat)
                        "Anorganik" -> currentFacData.copy(anorganik = currentFacData.anorganik + berat)
                        "Residu" -> currentFacData.copy(residu = currentFacData.residu + berat)
                        else -> currentFacData
                    }
                    tempFacultyMap[fakultas] = newFacData
                }
            }

            // --- 5. Update LiveData ke UI ---

            // Overview Update
            _doughnutData.value = DoughnutData(tempOrganikToday, tempAnorganikToday, tempResiduToday)
            _weeklyTotalData.value = tempWeeklyTrend.toList()

            // Analitik Update: Persentase Perubahan
            if (totalYesterdayAll > 0) {
                val change = ((totalTodayAll - totalYesterdayAll) / totalYesterdayAll) * 100
                _dailyChangePercentage.value = change
            } else if (totalTodayAll > 0) {
                _dailyChangePercentage.value = 100.0 // Naik 100% dari 0
            } else {
                _dailyChangePercentage.value = 0.0 // Sama-sama 0
            }

            // Analitik Update: Distribusi
            _weeklyDist.value = DoughnutData(
                tempWeeklyType["Organik"] ?: 0.0,
                tempWeeklyType["Anorganik"] ?: 0.0,
                tempWeeklyType["Residu"] ?: 0.0
            )
            _monthlyDist.value = DoughnutData(
                tempMonthlyType["Organik"] ?: 0.0,
                tempMonthlyType["Anorganik"] ?: 0.0,
                tempMonthlyType["Residu"] ?: 0.0
            )

            // Analitik Update: Fakultas List
            _facultyComposition.value = tempFacultyMap.values.toList()

            // Mengambil jumlah fakultas unik yang menyumbang sampah BULAN INI
            _fakultasAktif.value = tempFacultyMap.size
        }
    }

    // Fungsi untuk memfilter data laporan
    fun fetchFilteredReport(startDate: Long, endDate: Long, selectedFakultas: String?) {
        _isLoading.value = true

        val startTimestamp = Timestamp(Date(startDate))
        // Set end date ke akhir hari tersebut (23:59:59)
        val endCal = Calendar.getInstance().apply { timeInMillis = endDate }
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        val endTimestamp = Timestamp(endCal.time)

        var query = db.collection("sampah")
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThanOrEqualTo("timestamp", endTimestamp)

        // Filter Fakultas jika dipilih (dan bukan "Semua")
        if (!selectedFakultas.isNullOrEmpty() && selectedFakultas != "Semua Fakultas") {
            query = query.whereEqualTo("fakultas", selectedFakultas)
        }

        query.get()
            .addOnSuccessListener { documents ->
                val list = mutableListOf<WasteTransaction>()
                var totalBerat = 0.0

                for (doc in documents) {
                    val timestamp = (doc.get("timestamp") as? Timestamp)?.toDate() ?: Date()
                    val berat = (doc.get("berat") as? Number)?.toDouble() ?: 0.0
                    val jenis = doc.getString("jenis") ?: "-"
                    val fakultas = doc.getString("fakultas") ?: "-"

                    totalBerat += berat
                    list.add(WasteTransaction(doc.id, timestamp, fakultas, jenis, berat))
                }

                // Sort manual descending (terbaru di atas) karena Firestore query index complex
                list.sortByDescending { it.date }

                _transactionHistory.value = list

                // Estimasi: 1kg sampah didaur ulang ~ menghemat 0.5kg emisi CO2 (Contoh logika)
                _co2Reduced.value = totalBerat * 0.5

                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                Log.e(TAG, "Error fetch report", e)
            }
    }
}