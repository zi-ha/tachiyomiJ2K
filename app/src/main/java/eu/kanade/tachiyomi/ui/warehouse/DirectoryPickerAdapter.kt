package eu.kanade.tachiyomi.ui.warehouse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.databinding.ItemDirectoryBinding
import java.io.File

class DirectoryPickerAdapter(
    private val onClick: (File) -> Unit,
) : RecyclerView.Adapter<DirectoryPickerAdapter.Holder>() {
    private var items: List<File> = emptyList()

    fun setItems(items: List<File>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder =
        Holder(
            ItemDirectoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        val file = items[position]
        holder.bind(file)
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(
        private val binding: ItemDirectoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            binding.name.text = file.name
            binding.root.setOnClickListener { onClick(file) }
        }
    }
}
