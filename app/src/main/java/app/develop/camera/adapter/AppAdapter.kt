package app.develop.camera.adapter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.develop.camera.R
import app.develop.camera.util.CheckUtils

/**
 * AppAdapter
 */
class AppAdapter(private val mList: List<ResolveInfo>?, private val isPad: Boolean, private val mContext: Context) :
    RecyclerView.Adapter<AppAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = View.inflate(parent.context, R.layout.rv_app_item, null)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val displayMetrics: DisplayMetrics = mContext.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val imageSize = if (isPad) {
            screenWidth / 8
        } else {
            screenWidth / 6
        }

        val layoutParams = holder.mIcon.layoutParams
        layoutParams.width = imageSize
        layoutParams.height = imageSize // 使图片保持方形
        holder.mIcon.layoutParams = layoutParams

        holder.mIcon.setImageDrawable(mList!![position].loadIcon(mContext.packageManager))
        holder.mTitle.text = mList[position].loadLabel(mContext.packageManager)
        holder.itemView.setOnClickListener { v: View? ->
            val launchIntent = Intent()
            launchIntent.setComponent(
                ComponentName(
                    mList[position].activityInfo.packageName,
                    mList[position].activityInfo.name
                )
            )
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            mContext.startActivity(launchIntent)
        }


//        holder.itemView.setOnLongClickListener(v -> {
//            Log.d("OnLongClick", "OnLongClick====>");
//            Intent intent = new Intent(Intent.ACTION_DELETE);
//            intent.setData(Uri.parse("package:" + mList.get(position).activityInfo.packageName));
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            mContext.startActivity(intent);
//            return true;
//        });
    }




    override fun getItemCount(): Int {
        return mList?.size ?: 0
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mIcon: ImageView = itemView.findViewById(R.id.iv)
        val mTitle: TextView = itemView.findViewById(R.id.tv)
    }
}
