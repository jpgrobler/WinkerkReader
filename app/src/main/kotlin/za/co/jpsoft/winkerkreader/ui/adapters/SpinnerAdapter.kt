package za.co.jpsoft.winkerkreader.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import za.co.jpsoft.winkerkreader.R

/**
 * Created by Pieter Grobler on 04/09/2017.
 */
class SpinnerAdapter(
    private val context: Context,
    private val images: IntArray?,
    private val textNames: Array<String>?
) : BaseAdapter() {

    // NOTE: Original Java had an unused 'inflter' field; preserved for exact parity.
    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int {
        var result = images?.size ?: -1
        if (result == -1) {
            result = textNames?.size ?: -1
        }
        return result
    }

    override fun getItem(position: Int): Any? = null

    override fun getItemId(position: Int): Long = 0

    private class ViewHolder {
        lateinit var icon: ImageView
        lateinit var names: TextView
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = ViewHolder()
        val vi = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = vi.inflate(R.layout.custom_spinner_layout, null)
        holder.icon = view.findViewById(R.id.imageView)
        holder.names = view.findViewById(R.id.textView)

        if (images != null) {
            holder.icon.visibility = View.VISIBLE
            holder.icon.setImageResource(images[position])
        } else {
            holder.icon.visibility = View.GONE
        }

        if (textNames != null) {
            holder.names.visibility = View.VISIBLE
            holder.names.text = textNames[position]
        } else {
            holder.names.visibility = View.GONE
        }

        return view
    }
}