// DashboardViewModel.kt
package com.example.ecoscale_android

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp // <-- Penting: Impor Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar // <-- Penting: Impor Kalender
import java.util.Date
import java.util.Locale

// Tipe data ini tetap sama
data class DoughnutData(
    val organik: Double = 0.0,
    val anorganik: Double = 0.0,
    val residu: Double = 0.0
)

class DashboardViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val TAG = "DashboardViewModel"

    // --- LiveData untuk UI (Ini tetap sama) ---
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

    init {
        setupFirebaseListener() // Panggil fungsi listener yang baru
    }

    // ===================================================================
    // INI ADALAH FUNGSI YANG DIUBAH TOTAL
    // ===================================================================
    private fun setupFirebaseListener() {

        // --- 1. Tentukan Periode Waktu (Sama seperti di firebaseService.js) ---

        // Dapatkan "startOfToday" (00:00:00 hari ini)
        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday: Date = calToday.time

        // Dapatkan "startOfWeek" (00:00:00 hari Senin minggu ini)
        // Ini meniru logika di firebaseService.js
        val calWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY // Tetapkan Senin sebagai hari pertama
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfWeek: Date = calWeek.time

        // Dapatkan "sixMonthsAgo" (Sama seperti di firebaseService.js)
        val calSixMonths = Calendar.getInstance().apply {
            add(Calendar.MONTH, -5) // 5 bulan lalu + bulan ini = 6 bulan
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sixMonthsAgoTimestamp = Timestamp(calSixMonths.time)

        Log.d(TAG, "Mendengarkan data dari: $sixMonthsAgoTimestamp")

        // --- 2. Buat Kueri (Query) (Sama seperti di firebaseService.js) ---
        // Kita kueri koleksi "sampah", bukan "summary"
        val query = db.collection("sampah")
            .whereGreaterThanOrEqualTo("timestamp", sixMonthsAgoTimestamp)

        // --- 3. Tambahkan Listener pada Kueri ---
        query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot == null) {
                Log.w(TAG, "Snapshot null")
                return@addSnapshotListener
            }

            // --- 4. Agregasi Data (Logika Inti dari firebaseService.js) ---

            // Variabel sementara untuk menghitung
            var tempOrganikToday = 0.0
            var tempAnorganikToday = 0.0
            var tempResiduToday = 0.0
            val tempWeeklyData = DoubleArray(7) { 0.0 } // Array [0.0, 0.0, ... (7x)]

            Log.d(TAG, "Menerima ${snapshot.size()} dokumen. Memulai agregasi...")

            // Loop untuk setiap dokumen, sama seperti querySnapshot.forEach di JS
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue // Ambil data

                // Dapatkan data field dengan aman
                val timestamp = (doc.get("timestamp") as? Timestamp)?.toDate()
                val berat = (doc.get("berat") as? Number)?.toDouble() ?: 0.0
                val jenis = doc.getString("jenis")

                // Lewati jika timestamp tidak ada
                if (timestamp == null || jenis == "Umum") {
                    continue
                }

                // 4a. Logika Total Harian (Sama seperti di firebaseService.js)
                if (timestamp.time >= startOfToday.time) {
                    when (jenis) {
                        "Organik" -> tempOrganikToday += berat
                        "Anorganik" -> tempAnorganikToday += berat
                        "Residu" -> tempResiduToday += berat
                    }
                }

                // 4b. Logika Tren Mingguan (Sama seperti di firebaseService.js)
                if (timestamp.time >= startOfWeek.time) {
                    val calDoc = Calendar.getInstance().apply { time = timestamp }
                    val dayOfWeek = calDoc.get(Calendar.DAY_OF_WEEK) // Minggu=1, Senin=2, ...

                    // Konversi ke indeks (Senin=0, ..., Minggu=6)
                    // Mirip: const index = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
                    val index = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

                    if (index in 0..6) {
                        tempWeeklyData[index] += berat
                    }
                }
            }

            // --- 5. Update LiveData (Setelah loop selesai) ---
            Log.d(TAG, "Agregasi selesai. Mengirim data ke UI...")
            _doughnutData.value = DoughnutData(
                organik = tempOrganikToday,
                anorganik = tempAnorganikToday,
                residu = tempResiduToday
            )

            _weeklyTotalData.value = tempWeeklyData.toList() // Konversi Array ke List
        }
    }
}