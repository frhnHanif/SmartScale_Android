package com.example.ecoscale_android

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ecoscale_android.databinding.ItemReportRowBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ReportAdapter(private var list: List<WasteTransaction>) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemReportRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))

        holder.binding.apply {
            tvDate.text = dateFormat.format(item.date)
            tvTime.text = timeFormat.format(item.date)
            tvFaculty.text = item.fakultas
            tvType.text = item.jenis
            tvWeight.text = "%.1f kg".format(item.berat)
        }
    }

    override fun getItemCount() = list.size

    fun updateData(newList: List<WasteTransaction>) {
        list = newList
        notifyDataSetChanged()
    }
}