package com.custom.sidesense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val apps: List<AppRepo.AppInfo>,
    private val onClick: (AppRepo.AppInfo) -> Unit,
    private val onLongClick: (AppRepo.AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.item_icon)
        val label: TextView = v.findViewById(R.id.item_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_grid, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener { 
            onLongClick(app)
            true 
        }
    }

    override fun getItemCount() = apps.size
}
