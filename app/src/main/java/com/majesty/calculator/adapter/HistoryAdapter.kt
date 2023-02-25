package com.majesty.calculator.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.majesty.calculator.databinding.ItemHistoryBinding
import com.majesty.calculator.model.History

class HistoryAdapter(private val context: Context, historyList: ArrayList<History>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var historyList: ArrayList<History>
    init {
        this.historyList = historyList
    }

    inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return historyList.size
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder){
            with(historyList[position]){
                binding.tvLine1.text = this.input
                binding.tvLine2.text = this.result
                if (!this.encrypted) {
                    binding.encryptedText.text = "Database Storage"
                }
                Glide.with(context).load(this.image).into(binding.imgInput)
            }
        }
    }

}