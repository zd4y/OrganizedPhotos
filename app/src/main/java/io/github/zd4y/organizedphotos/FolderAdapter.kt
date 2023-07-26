package io.github.zd4y.organizedphotos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FolderAdapter(
    private var folders: MutableList<Folder>,
    private val onItemClick: (Folder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false)
        return ViewHolder(view) {
            onItemClick(folders[it])
        }
    }

    override fun getItemCount(): Int {
        return folders.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder)
    }

    inner class ViewHolder(itemView: View, onItemClicked: (Int) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val tvFolder: TextView = itemView.findViewById(R.id.tvFolder)

        init {
            itemView.setOnClickListener {
                onItemClicked(adapterPosition)
            }
        }

        fun bind(folder: Folder) {
            tvFolder.text = folder.name
        }
    }
}