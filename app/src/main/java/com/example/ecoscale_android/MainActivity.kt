package com.example.ecoscale_android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ecoscale_android.databinding.ActivityMainBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.example.ecoscale_android.R

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DashboardViewModel by viewModels()

    // Warna dari colors.xml
    private val colorOrganik by lazy { ContextCompat.getColor(this, R.color.eco_organik) }
    private val colorAnorganik by lazy { ContextCompat.getColor(this, R.color.eco_anorganik) }
    private val colorResidu by lazy { ContextCompat.getColor(this, R.color.eco_residu) }
    private val colorLineChart by lazy { ContextCompat.getColor(this, R.color.eco_line_chart) }
    private val colorNoData by lazy { ContextCompat.getColor(this, R.color.eco_no_data_gray) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCharts()
        observeViewModel()
    }

    // Menyiapkan tampilan statis grafik (pengganti initWeeklyTrendChart & initTypeDistributionChart)
    private fun setupCharts() {
        setupLineChart()
        setupDoughnutChart()
    }

    private fun setupLineChart() {
        binding.weeklyTrendChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)

            // Sumbu X (Label Hari)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.valueFormatter = object : ValueFormatter() {
                private val days = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
                override fun getFormattedValue(value: Float): String {
                    return days.getOrNull(value.toInt()) ?: ""
                }
            }

            // Sumbu Y
            axisLeft.setDrawGridLines(true)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
        }
    }

    private fun setupDoughnutChart() {
        binding.typeDistributionChart.apply {
            // Ini adalah pengganti 'cutout = 80%' di JS
            isDrawHoleEnabled = true
            holeRadius = 80f
            transparentCircleRadius = 80f // Pastikan tidak ada bayangan

            description.isEnabled = false
            legend.isEnabled = false // Kita atur manual di logic update
            setUsePercentValues(false)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(12f)

            // Pengganti plugin 'noDataDoughnutText'
            setNoDataText("Memuat data...")
        }
    }

    // Ini adalah inti dari aplikasi, menghubungkan data ke UI
    private fun observeViewModel() {
        // 1. Update Tanggal
        viewModel.currentDate.observe(this) { dateString ->
            binding.tvCurrentDate.text = dateString
        }

        // 2. Update Total Sampah (di semua tempat)
        viewModel.totalSampahToday.observe(this) { total ->
            val formattedTotal = "%.1f kg".format(total)
            binding.tvTotalSampah.text = formattedTotal
            binding.tvTotalSampahCard.text = formattedTotal // Update kartu di atas juga
        }

        // 3. Update Grafik Donat & Rinciannya
        viewModel.doughnutData.observe(this) { data ->
            // Update Teks Rincian
            binding.tvTotalOrganik.text = "%.1f kg".format(data.organik)
            binding.tvTotalAnorganik.text = "%.1f kg".format(data.anorganik)
            binding.tvTotalResidu.text = "%.1f kg".format(data.residu)

            // Update Grafik Donat (logika dari updateDashboardSpecificUI)
            updateDoughnutChartData(data)
        }

        // 4. Update Grafik Tren Mingguan
        viewModel.weeklyTotalData.observe(this) { weeklyData ->
            updateLineChartData(weeklyData)
        }
    }

    // --- Fungsi Update Grafik (dipanggil oleh Observer) ---

    private fun updateDoughnutChartData(data: DoughnutData) {
        val hasActualData = data.organik > 0 || data.anorganik > 0 || data.residu > 0
        val chart = binding.typeDistributionChart

        if (hasActualData) {
            // --- JIKA ADA DATA ---
            val entries = mutableListOf<PieEntry>()
            if (data.organik > 0) entries.add(PieEntry(data.organik.toFloat(), "Organik"))
            if (data.anorganik > 0) entries.add(PieEntry(data.anorganik.toFloat(), "Anorganik"))
            if (data.residu > 0) entries.add(PieEntry(data.residu.toFloat(), "Residu"))

            val colors = mutableListOf<Int>()
            if (data.organik > 0) colors.add(colorOrganik)
            if (data.anorganik > 0) colors.add(colorAnorganik)
            if (data.residu > 0) colors.add(colorResidu)

            val dataSet = PieDataSet(entries, "Distribusi Sampah")
            dataSet.colors = colors
            dataSet.valueTextSize = 10f
            dataSet.valueTextColor = Color.BLACK
            dataSet.sliceSpace = 2f // Spasi antar slice

            chart.data = PieData(dataSet).apply { setValueTextSize(0f) } // Sembunyikan label di dalam slice
            chart.centerText = "" // Hapus teks "No Data"
            chart.legend.isEnabled = true // Tampilkan legenda bawaan

        } else {
            // --- JIKA TIDAK ADA DATA (Logika 'No Data Today') ---
            val entries = listOf(PieEntry(1f, ""))
            val dataSet = PieDataSet(entries, "")
            dataSet.colors = listOf(colorNoData)

            chart.data = PieData(dataSet).apply { setValueTextSize(0f) }

            // Ini adalah pengganti plugin JS 'noDataDoughnutText'
            chart.centerText = "No Data Today"
            chart.setCenterTextSize(16f)
            chart.setCenterTextColor(Color.GRAY)
            chart.legend.isEnabled = false // Sembunyikan legenda
        }

        // Refresh chart
        chart.invalidate()
    }

    private fun updateLineChartData(data: List<Double>) {
        val entries = ArrayList<Entry>()
        data.forEachIndexed { index, value ->
            entries.add(Entry(index.toFloat(), value.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Berat Sampah (kg)")
        dataSet.color = colorLineChart
        dataSet.lineWidth = 2.5f
        dataSet.setCircleColor(colorLineChart)
        dataSet.circleRadius = 4f

        // Logika 'fill' dari Chart.js
        dataSet.setDrawFilled(true)
        dataSet.fillDrawable = ContextCompat.getDrawable(this, R.drawable.chart_fade_green) // Anda perlu membuat drawable ini
        dataSet.fillAlpha = 100

        val lineData = LineData(dataSet)
        lineData.setValueTextSize(0f) // Sembunyikan nilai pada titik

        binding.weeklyTrendChart.data = lineData
        binding.weeklyTrendChart.invalidate()
    }
}