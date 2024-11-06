package app.develop.camera.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.develop.camera.R
import app.develop.camera.ui.activities.MediaFilesByDate
import app.develop.camera.util.dpToPx

class MediaFilesByDataAdapter(
    private val mediaFilesByDate: List<MediaFilesByDate>,
    private val isTablet: Boolean,
    private val onDateClick: (MediaFilesByDate) -> Unit
) : RecyclerView.Adapter<MediaFilesByDataAdapter.OuterViewHolder>() {

    inner class OuterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateTextView: TextView = view.findViewById(R.id.dateTextView)
        val fileIcon: ImageView = view.findViewById(R.id.icon_file)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OuterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.outer_item, parent, false)
        return OuterViewHolder(view)
    }

    override fun onBindViewHolder(holder: OuterViewHolder, position: Int) {
        val mediaFilesByDate = mediaFilesByDate[position]
        val dateString = mediaFilesByDate.date
        if (dateString.length == 8) {
            holder.dateTextView.text = "${dateString.substring(0, 4)}-${dateString.substring(4, 6)}-${dateString.substring(6, 8)}"
        } else {
            holder.dateTextView.text = mediaFilesByDate.date
        }
        val imageSize = if(isTablet) {
            dpToPx(holder.itemView.context,60f)
        } else {
            dpToPx(holder.itemView.context,40f)
        }
        val layoutParams = holder.fileIcon.layoutParams
        layoutParams.width = imageSize
        layoutParams.height = imageSize
        holder.fileIcon.layoutParams = layoutParams

        holder.fileIcon
        holder.itemView.setOnClickListener {
            onDateClick(mediaFilesByDate)
        }
    }



    override fun getItemCount(): Int = mediaFilesByDate.size
}
