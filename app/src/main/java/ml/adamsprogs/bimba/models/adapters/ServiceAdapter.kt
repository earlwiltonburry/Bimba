package ml.adamsprogs.bimba.models.adapters

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.LayoutInflater
import ml.adamsprogs.bimba.R
import android.app.Activity
import android.widget.ArrayAdapter
import ml.adamsprogs.bimba.ProviderProxy


class ServiceAdapter(context: Activity, resourceId: Int, list: List<RowItem>) : ArrayAdapter<ServiceAdapter.RowItem>(context, resourceId, list) {

    private val inflater: LayoutInflater = context.layoutInflater

    @SuppressLint("ViewHolder", "InflateParams")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowItem: RowItem = getItem(position)
        val rowView = inflater.inflate(R.layout.toolbar_spinner_item, null, true)
        rowView.findViewById<TextView>(R.id.text).text = rowItem.description

        return rowView
    }

    @SuppressLint("InflateParams")
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val rowItem: RowItem = getItem(position)
        val rowView = inflater.inflate(R.layout.toolbar_spinner_item, null, true)
        rowView.findViewById<TextView>(R.id.text).text = rowItem.description

        return rowView

    }

    data class RowItem(val service: String, val description: String) : Comparable<RowItem> {
        override fun compareTo(other: RowItem): Int {
            val proxy = ProviderProxy()
            return proxy.getServiceFirstDay(service).compareTo(proxy.getServiceFirstDay(other.service))
        }
    }
}
