package com.therapea.app.features.therapists.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.therapea.app.R
import com.therapea.app.features.therapists.model.SessionRecord

class RecordAdapter(
    private val records: List<SessionRecord>
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    inner class RecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtType: TextView = view.findViewById(R.id.txtType)
        val txtNotes: TextView = view.findViewById(R.id.txtNotes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline_record, parent, false)

        return RecordViewHolder(view)
    }

    override fun getItemCount(): Int = records.size

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {

        val record = records[position]

        holder.txtDate.text = record.date
        holder.txtType.text = record.type
        holder.txtNotes.text = record.notes
    }
}