package app.develop.camera.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.develop.camera.R
import app.develop.camera.ui.activities.MediaFileInfo
import app.develop.camera.util.dpToPx


class MediaFilesDetailAdapter(
    private val mediaFiles: List<MediaFileInfo>,
    private val isTablet: Boolean,
    private val onDateClick: (MediaFileInfo) -> Unit
) : RecyclerView.Adapter<MediaFilesDetailAdapter.InnerViewHolder>() {

    inner class InnerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnailImageView: ImageView = view.findViewById(R.id.thumbnailImageView)
        val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.inner_item, parent, false)
        return InnerViewHolder(view)
    }

    override fun onBindViewHolder(holder: InnerViewHolder, position: Int) {
        val mediaFile = mediaFiles[position]

        val imageSize = if(isTablet) {
            dpToPx(holder.itemView.context,60f)
        } else {
            dpToPx(holder.itemView.context,40f)
        }
        val layoutParams = holder.thumbnailImageView.layoutParams
        layoutParams.width = imageSize
        layoutParams.height = imageSize
        holder.thumbnailImageView.layoutParams = layoutParams

        holder.thumbnailImageView.setImageBitmap(mediaFile.thumbnail) // 显示缩略图

        holder.fileNameTextView.text = mediaFile.fileName // 显示文件名
        holder.itemView.setOnClickListener {
            onDateClick(mediaFile)
        }
    }

    override fun getItemCount(): Int = mediaFiles.size
}
