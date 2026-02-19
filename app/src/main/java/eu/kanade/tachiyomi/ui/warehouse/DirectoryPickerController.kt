package eu.kanade.tachiyomi.ui.warehouse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DirectoryPickerControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.util.storage.DiskUtil
import uy.kohesive.injekt.injectLazy
import java.io.File

class DirectoryPickerController(
    bundle: Bundle,
) : BaseController<DirectoryPickerControllerBinding>(bundle) {
    private val preferences: eu.kanade.tachiyomi.data.preference.PreferencesHelper by injectLazy()

    private val adapter =
        DirectoryPickerAdapter { dir ->
            currentDir = dir
            render()
        }

    private lateinit var currentDir: File

    override fun getTitle(): String? = resources?.getString(R.string.choose_folder)

    override fun createBinding(inflater: LayoutInflater): DirectoryPickerControllerBinding =
        DirectoryPickerControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        val path = args.getString(KEY_INITIAL_PATH)?.takeIf { it.isNotBlank() }
        val start =
            path?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }
                ?: DiskUtil.getExternalStorages(preferences.context).firstOrNull()
                ?: File("/")

        currentDir = start

        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter

        binding.parentButton.setOnClickListener {
            val parent = currentDir.parentFile ?: return@setOnClickListener
            if (!parent.exists() || !parent.isDirectory) return@setOnClickListener
            currentDir = parent
            render()
        }

        binding.selectButton.setOnClickListener {
            (targetController as? DirectorySelectionListener)?.onDirectorySelected(currentDir.absolutePath)
            router.popCurrentController()
        }

        render()
    }

    private fun render() {
        binding.currentPath.text = currentDir.absolutePath
        val dirs =
            currentDir
                .listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory }
                ?.filterNot { it.name.startsWith('.') }
                ?.sortedBy { it.name.lowercase() }
                ?.toList()
                ?: emptyList()
        adapter.setItems(dirs)
    }

    companion object {
        private const val KEY_INITIAL_PATH = "initial_path"

        fun create(initialPath: String?): DirectoryPickerController =
            DirectoryPickerController(
                Bundle().apply {
                    putString(KEY_INITIAL_PATH, initialPath ?: "")
                },
            )
    }
}
