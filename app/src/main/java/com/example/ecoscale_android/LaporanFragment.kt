package com.example.ecoscale_android

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecoscale_android.databinding.FragmentLaporanBinding
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LaporanFragment : Fragment() {

    private var _binding: FragmentLaporanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels()
    private lateinit var adapter: ReportAdapter

    // Variabel filter
    private val calendarStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) } // Awal bulan
    private val calendarEnd = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLaporanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObservers()

        // Load data default (Bulan ini) saat pertama buka
        generateReport()
    }

    private fun setupUI() {
        // 1. Setup RecyclerView
        adapter = ReportAdapter(emptyList())
        binding.rvReportHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReportHistory.adapter = adapter
        binding.rvReportHistory.isNestedScrollingEnabled = false // Agar smooth di dalam ScrollView

        // 2. Setup Dropdown Fakultas
        val fakultasList = listOf("Semua Fakultas", "FT", "FK", "FSM", "FH", "FKM", "FEB", "FISIP", "FIB", "FPsi", "FPIK", "FPP", "SV")
        val fakultasAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, fakultasList)
        binding.actvFakultasFilter.setAdapter(fakultasAdapter)

        // 3. Setup Date Pickers
        updateDateLabels()
        binding.etStartDate.setOnClickListener { showDatePicker(calendarStart, binding.etStartDate) }
        binding.etEndDate.setOnClickListener { showDatePicker(calendarEnd, binding.etEndDate) }

        // 4. Tombol Actions
        binding.btnGenerateReport.setOnClickListener {
            generateReport()
        }

        binding.btnExportExcel.setOnClickListener {
            exportToCSV()
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBarReport.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnGenerateReport.isEnabled = !isLoading
        }

        viewModel.transactionHistory.observe(viewLifecycleOwner) { list ->
            adapter.updateData(list)

            // Update Card Stats berdasarkan hasil filter
            val totalBerat = list.sumOf { it.berat }
            binding.tvMonthlyTotal.text = "%.1f kg".format(totalBerat)
            binding.tvTransactionCount.text = list.size.toString()
            binding.tvResultCount.text = "Menampilkan ${list.size} data"

            // Jika kosong
            if(list.isEmpty()) {
                Toast.makeText(requireContext(), "Tidak ada data pada periode ini", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.co2Reduced.observe(viewLifecycleOwner) { co2 ->
            binding.tvCo2Reduced.text = "%.1f kg".format(co2)
        }
    }

    private fun generateReport() {
        val startDate = calendarStart.timeInMillis
        val endDate = calendarEnd.timeInMillis
        val fakultas = binding.actvFakultasFilter.text.toString()

        viewModel.fetchFilteredReport(startDate, endDate, fakultas)
    }

    private fun showDatePicker(calendar: Calendar, updateView: android.widget.TextView) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
                updateView.text = dateFormat.format(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateLabels() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        binding.etStartDate.setText(dateFormat.format(calendarStart.time))
        binding.etEndDate.setText(dateFormat.format(calendarEnd.time))
    }

    // Fitur Export ke CSV (Pengganti Excel agar ringan di Android)
    private fun exportToCSV() {
        val data = viewModel.transactionHistory.value
        if (data.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data untuk diekspor", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Nama File
            val fileName = "Laporan_EcoScale_${System.currentTimeMillis()}.csv"

            // Simpan di Cache directory agar tidak butuh permission storage yang rumit
            val file = File(requireContext().cacheDir, fileName)
            val writer = FileWriter(file)

            // Header CSV
            writer.append("Tanggal,Waktu,Fakultas,Jenis Sampah,Berat (kg)\n")

            // Isi Data
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

            for (item in data) {
                writer.append("${dateFormat.format(item.date)},")
                writer.append("${timeFormat.format(item.date)},")
                writer.append("${item.fakultas},")
                writer.append("${item.jenis},")
                writer.append("${item.berat}\n")
            }
            writer.flush()
            writer.close()

            // Share File Intent (Buka di Excel/Sheets)
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/csv"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Buka Laporan dengan..."))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Gagal export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}