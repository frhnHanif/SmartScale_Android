package com.example.ecoscale_android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.ecoscale_android.databinding.FragmentInputManualBinding

class InputManualFragment : Fragment() {

    private var _binding: FragmentInputManualBinding? = null
    private val binding get() = _binding!!

    // Gunakan ViewModel yang sama dengan Activity untuk akses fungsi upload
    private val viewModel: DashboardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputManualBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDropdowns()
        setupSubmitButton()
        observeStatus()
    }

    private fun setupDropdowns() {
        // 1. Dropdown Jenis Sampah
        val jenisList = listOf("Organik", "Anorganik", "Residu")
        val jenisAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, jenisList)
        binding.actvJenis.setAdapter(jenisAdapter)

        // 2. Dropdown Fakultas
        val fakultasList = listOf("FT", "FK", "FSM", "FH", "FKM", "FEB", "FISIP", "FIB", "FPsi", "FPIK", "FPP")
        val fakultasAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, fakultasList)
        binding.actvFakultas.setAdapter(fakultasAdapter)
    }

    private fun setupSubmitButton() {
        binding.btnSubmit.setOnClickListener {
            // Ambil data dari input
            val beratStr = binding.etBerat.text.toString()
            val jenis = binding.actvJenis.text.toString()
            val fakultas = binding.actvFakultas.text.toString()

            // Validasi sederhana
            if (beratStr.isEmpty()) {
                binding.tilBerat.error = "Berat tidak boleh kosong"
                return@setOnClickListener
            } else {
                binding.tilBerat.error = null
            }

            if (jenis.isEmpty()) {
                binding.tilJenis.error = "Pilih jenis sampah"
                return@setOnClickListener
            }

            if (fakultas.isEmpty()) {
                binding.tilFakultas.error = "Pilih fakultas"
                return@setOnClickListener
            }

            // Konversi dan Kirim
            val berat = beratStr.toDoubleOrNull()
            if (berat != null && berat > 0) {
                viewModel.uploadManualData(berat, jenis, fakultas)
            } else {
                binding.tilBerat.error = "Berat tidak valid"
            }
        }
    }

    private fun observeStatus() {
        // Observasi Loading State
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnSubmit.isEnabled = !isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSubmit.text = if (isLoading) "Mengirim..." else "Kirim Data"
        }

        // Observasi Status Sukses/Gagal
        viewModel.uploadStatus.observe(viewLifecycleOwner) { statusMsg ->
            statusMsg?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

                if (msg.contains("Sukses")) {
                    clearInputs()
                }

                // Reset status di ViewModel agar tidak muncul lagi saat rotasi layar
                viewModel.clearUploadStatus()
            }
        }
    }

    private fun clearInputs() {
        binding.etBerat.text?.clear()
        binding.etBerat.clearFocus()
        // Reset dropdown ke default pilihan pertama jika perlu, atau biarkan terpilih
        binding.actvJenis.setText("Organik", false)
        binding.actvFakultas.setText("FT", false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}