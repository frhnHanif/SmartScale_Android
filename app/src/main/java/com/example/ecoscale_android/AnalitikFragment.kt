package com.example.ecoscale_android

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.ecoscale_android.databinding.FragmentAnalitikBinding
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class AnalitikFragment : Fragment() {

    private var _binding: FragmentAnalitikBinding? = null
    private val binding get() = _binding!!

    // Menggunakan instance ViewModel yang SAMA dengan MainActivity
    private val viewModel: DashboardViewModel by activityViewModels()

    // Warna dari colors.xml (sesuaikan nama warnanya dengan project kamu)
    private val colorOrganik by lazy { ContextCompat.getColor(requireContext(), R.color.eco_organik) }
    private val colorAnorganik by lazy { ContextCompat.getColor(requireContext(), R.color.eco_anorganik) }
    private val colorResidu by lazy { ContextCompat.getColor(requireContext(), R.color.eco_residu) }
    private val colorNoData by lazy { ContextCompat.getColor(requireContext(), R.color.eco_no_data_gray) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalitikBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCharts()
        observeData()
    }

    private fun setupCharts() {
        // 1. Setup Stacked Bar Chart (Fakultas)
        binding.barChartFaculty.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            setPinchZoom(false)
            setScaleEnabled(false)

            // Sumbu X (Nama Fakultas)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f // Label tidak loncat
            xAxis.labelRotationAngle = -45f // Miringkan teks jika panjang

            // Sumbu Y
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false

            // Legenda
            legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        }

        // 2. Setup Pie Charts (Visual Dasar)
        listOf(binding.pieChartWeekly, binding.pieChartMonthly).forEach { chart ->
            chart.apply {
                description.isEnabled = false
                legend.isEnabled = false
                setHoleColor(Color.TRANSPARENT)
                holeRadius = 60f
                transparentCircleRadius = 65f
                setUsePercentValues(false)
                setNoDataText("Memuat...")
            }
        }
    }

    private fun observeData() {
        // A. Observasi Persentase Perubahan
        viewModel.dailyChangePercentage.observe(viewLifecycleOwner) { percent ->
            val absPercent = Math.abs(percent)
            binding.tvPercentageChange.text = "%.1f%%".format(absPercent)

            if (percent < 0) {
                // Sampah Turun (Bagus) -> Hijau
                binding.tvPercentageChange.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                binding.tvChangeDesc.text = "Lebih baik dari kemarin!"
            } else if (percent > 0) {
                // Sampah Naik (Buruk) -> Merah
                binding.tvPercentageChange.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                binding.tvChangeDesc.text = "Meningkat dari kemarin."
            } else {
                // Sama / Belum ada data
                binding.tvPercentageChange.setTextColor(Color.GRAY)
                binding.tvChangeDesc.text = "Sama seperti kemarin."
            }
        }

        // B. Observasi Pie Charts
        viewModel.weeklyDist.observe(viewLifecycleOwner) { data ->
            updatePieChart(binding.pieChartWeekly, data)
        }
        viewModel.monthlyDist.observe(viewLifecycleOwner) { data ->
            updatePieChart(binding.pieChartMonthly, data)
        }

        // C. Observasi Bar Chart Fakultas
        viewModel.facultyComposition.observe(viewLifecycleOwner) { facultyList ->
            updateBarChart(facultyList)
        }
    }

    private fun updatePieChart(chart: com.github.mikephil.charting.charts.PieChart, data: DoughnutData) {
        val hasData = data.organik > 0 || data.anorganik > 0 || data.residu > 0

        if (hasData) {
            val entries = listOf(
                PieEntry(data.organik.toFloat(), ""),
                PieEntry(data.anorganik.toFloat(), ""),
                PieEntry(data.residu.toFloat(), "")
            )
            // Filter nilai 0 agar chart rapi
            val validEntries = entries.filter { it.value > 0 }

            val colors = mutableListOf<Int>()
            if (data.organik > 0) colors.add(colorOrganik)
            if (data.anorganik > 0) colors.add(colorAnorganik)
            if (data.residu > 0) colors.add(colorResidu)

            val dataSet = PieDataSet(validEntries, "")
            dataSet.colors = colors
            dataSet.sliceSpace = 2f

            chart.data = PieData(dataSet).apply { setValueTextSize(0f) } // Hilangkan teks angka
            chart.centerText = ""
        } else {
            // Tampilan kosong
            val entries = listOf(PieEntry(1f, ""))
            val dataSet = PieDataSet(entries, "")
            dataSet.colors = listOf(colorNoData)
            chart.data = PieData(dataSet).apply { setValueTextSize(0f) }
            chart.centerText = "-"
        }
        chart.invalidate() // Refresh chart
    }

    private fun updateBarChart(facultyList: List<FacultyWasteData>) {
        if (facultyList.isEmpty()) {
            binding.barChartFaculty.clear()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        facultyList.forEachIndexed { index, facData ->
            // Stacked Bar: floatArrayOf(Organik, Anorganik, Residu)
            entries.add(BarEntry(index.toFloat(), floatArrayOf(
                facData.organik.toFloat(),
                facData.anorganik.toFloat(),
                facData.residu.toFloat()
            )))
            labels.add(facData.fakultas)
        }

        val set = BarDataSet(entries, "Komposisi Sampah (kg)")
        set.colors = listOf(colorOrganik, colorAnorganik, colorResidu) // Warna tumpukan
        set.stackLabels = arrayOf("Organik", "Anorganik", "Residu")
        set.valueTextColor = Color.BLACK
        set.valueTextSize = 10f

        val data = BarData(set)
        binding.barChartFaculty.data = data

        // Set Label Sumbu X (Nama Fakultas)
        binding.barChartFaculty.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.barChartFaculty.xAxis.labelCount = labels.size

        binding.barChartFaculty.invalidate()
        binding.barChartFaculty.animateY(1000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}