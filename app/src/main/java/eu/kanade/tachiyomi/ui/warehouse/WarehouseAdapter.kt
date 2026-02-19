package eu.kanade.tachiyomi.ui.warehouse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.models.LocalLocation
import eu.kanade.tachiyomi.databinding.ItemWarehouseBinding

class WarehouseAdapter(
    private val listener: OnLocationClickListener,
) : RecyclerView.Adapter<WarehouseAdapter.LocationHolder>() {
    var items: List<LocalLocation> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    interface OnLocationClickListener {
        fun onRefresh(location: LocalLocation)

        fun onDelete(location: LocalLocation)

        fun onToggle(location: LocalLocation)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): LocationHolder {
        val binding = ItemWarehouseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LocationHolder(binding)
    }

    override fun onBindViewHolder(
        holder: LocationHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LocationHolder(
        private val binding: ItemWarehouseBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(location: LocalLocation) {
            binding.name.text = location.name
            binding.path.text = location.directory
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = location.enabled

            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != location.enabled) {
                    listener.onToggle(location.copy(enabled = isChecked))
                }
            }

            binding.refreshButton.setOnClickListener {
                listener.onRefresh(location)
            }

            binding.moreButton.setOnClickListener {
                val popup = PopupMenu(it.context, it)
                popup.menu.add(0, 1, 0, itemView.context.getString(R.string.action_delete))
                popup.setOnMenuItemClickListener { item ->
                    if (item.itemId == 1) {
                        listener.onDelete(location)
                        true
                    } else {
                        false
                    }
                }
                popup.show()
            }
        }
    }
}
