package com.example.ecoscale_android

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.ecoscale_android.databinding.ActivityMainBinding

// (Pastikan kamu membuat file-file Fragment ini,
// walaupun isinya masih kosong untuk saat ini)
// import com.example.ecoscale_android.FakultasFragment
// import com.example.ecoscale_android.AnalitikFragment
// import com.example.ecoscale_android.LaporanFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Kita tetap membutuhkan ViewModel di Activity
    // untuk meng-update data di Header dan Kartu KPI
    // yang merupakan bagian dari layout Activity ini.
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Muat fragment default (Overview) saat aplikasi pertama dibuka
        if (savedInstanceState == null) {
            loadFragment(OverviewFragment())
        }

        // Setup listener untuk Bottom Navigation
        setupBottomNavigation()

        // Panggil fungsi untuk meng-observe data header statis
        observeHeaderData()
    }

    /**
     * Mengatur listener untuk BottomNavigationView.
     * Akan mengganti fragment di 'fragment_container'
     * berdasarkan tab yang dipilih.
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_overview -> OverviewFragment()

                // TODO: Ganti komentar ini setelah kamu membuat Fragment-nya
                R.id.nav_fakultas -> OverviewFragment() // Ganti dengan FakultasFragment()
                R.id.nav_analitik -> OverviewFragment() // Ganti dengan AnalitikFragment()
                R.id.nav_laporan -> OverviewFragment() // Ganti dengan LaporanFragment()

                else -> OverviewFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    /**
     * Meng-observe LiveData dari ViewModel untuk meng-update
     * UI statis yang ada di MainActivity (Header dan Kartu KPI).
     */
    private fun observeHeaderData() {

        // 1. Update Tanggal (di header)
        viewModel.currentDate.observe(this) { dateString ->
            binding.tvCurrentDate.text = dateString
        }

        // 2. Update Kartu KPI Total Sampah (di header)
        viewModel.totalSampahToday.observe(this) { total ->
            val formattedTotal = "%.1f kg".format(total)
            binding.tvTotalSampahCard.text = formattedTotal
        }

        // 3. (Opsional) Jika 'Fakultas Aktif' juga dinamis,
        //    tambahkan observer-nya di sini.
        // viewModel.fakultasAktif.observe(this) { count ->
        //     binding.tvFakultasAktif.text = count.toString()
        // }
    }

    /**
     * Fungsi helper untuk mengganti Fragment yang tampil
     * di dalam R.id.fragment_container.
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}