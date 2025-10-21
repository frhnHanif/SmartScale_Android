package com.example.ecoscale_android

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // <-- PENTING: Gunakan 'activityViewModels'
import com.example.ecoscale_android.databinding.FragmentOverviewBinding // <-- PENTING: Ganti ke binding fragment
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

class OverviewFragment : Fragment() {

    // Setup ViewBinding yang aman untuk Fragment
    private var _binding: FragmentOverviewBinding? = null
    private val binding get() = _binding!!

    // Gunakan 'activityViewModels()' untuk BERBAGI ViewModel dengan MainActivity
    private val viewModel: DashboardViewModel by activityViewModels()

    // Warna dari colors.xml (gunakan 'requireContext()' di Fragment)
    private val colorOrganik by lazy { ContextCompat.getColor(requireContext(), R.color.eco_organik) }
    private val colorAnorganik by lazy { ContextCompat.getColor(requireContext(), R.color.eco_anorganik) }
    private val colorResidu by lazy { ContextCompat.getColor(requireContext(), R.color.eco_residu) }
    private val colorLineChart by lazy { ContextCompat.getColor(requireContext(), R.color.eco_line_chart) }
    private val colorNoData by lazy { ContextCompat.getColor(requireContext(), R.color.eco_no_data_gray) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate layout khusus untuk fragment ini
        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Semua logika UI yang dulu di MainActivity, sekarang dipanggil di sini
        setupCharts()
        observeChartData() // Ganti nama fungsi agar lebih jelas
    }

    // Fungsi ini dipanggil saat Fragment dihancurkan untuk mencegah memory leak
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Menyiapkan tampilan statis grafik (SAMA SEPERTI SEBELUMNYA)
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
            isDrawHoleEnabled = true
            holeRadius = 80f
            transparentCircleRadius = 80f
            description.isEnabled = false
            legend.isEnabled = false
            setUsePercentValues(false)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(12f)
            setNoDataText("Memuat data...")
        }
    }

    // Meng-observe data khusus untuk chart di fragment ini
    private fun observeChartData() {
        // MainActivity akan meng-handle update 'tvCurrentDate' dan 'tvTotalSampahCard'

        // 1. Update Total Sampah (yang di BAWAH chart donat)
        viewModel.totalSampahToday.observe(viewLifecycleOwner) { total ->
            val formattedTotal = "%.1f kg".format(total)
            binding.tvTotalSampah.text = formattedTotal
        }

        // 2. Update Grafik Donat & Rinciannya
        viewModel.doughnutData.observe(viewLifecycleOwner) { data ->
            // Update Teks Rincian
            binding.tvTotalOrganik.text = "%.1f kg".format(data.organik)
            binding.tvTotalAnorganik.text = "%.1f kg".format(data.anorganik)
            binding.tvTotalResidu.text = "%.1f kg".format(data.residu)

            // Update Grafik Donat
            updateDoughnutChartData(data)
        }

        // 3. Update Grafik Tren Mingguan
        viewModel.weeklyTotalData.observe(viewLifecycleOwner) { weeklyData ->
            updateLineChartData(weeklyData)
        }
    }

    // --- Fungsi Update Grafik (SAMA SEPERTI SEBELUMNYA) ---

    private fun updateDoughnutChartData(data: DoughnutData) {
        val hasActualData = data.organik > 0 || data.anorganik > 0 || data.residu > 0
        val chart = binding.typeDistributionChart

        if (hasActualData) {
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
            dataSet.sliceSpace = 2f

            chart.data = PieData(dataSet).apply { setValueTextSize(0f) }
            chart.centerText = ""
            chart.legend.isEnabled = true

        } else {
            val entries = listOf(PieEntry(1f, ""))
            val dataSet = PieDataSet(entries, "")
            dataSet.colors = listOf(colorNoData)

            chart.data = PieData(dataSet).apply { setValueTextSize(0f) }
            chart.centerText = "No Data Today"
            chart.setCenterTextSize(16f)
            chart.setCenterTextColor(Color.GRAY)
            chart.legend.isEnabled = false
        }

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

        dataSet.setDrawFilled(true)
        // PENTING: Ganti 'this' menjadi 'requireContext()'
        dataSet.fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.chart_fade_green)
        dataSet.fillAlpha = 100

        val lineData = LineData(dataSet)
        lineData.setValueTextSize(0f)

        binding.weeklyTrendChart.data = lineData
        binding.weeklyTrendChart.invalidate()
    }
}