package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ReaderTransitionViewBinding
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class ReaderTransitionView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        private val binding: ReaderTransitionViewBinding =
            ReaderTransitionViewBinding.inflate(LayoutInflater.from(context), this, true)
        private val preferences: PreferencesHelper by injectLazy()

        init {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        fun bind(
            transition: ChapterTransition,
            manga: Manga?,
        ) {
            manga ?: return
            when (transition) {
                is ChapterTransition.Prev -> bindPrevChapterTransition(transition, manga)
                is ChapterTransition.Next -> bindNextChapterTransition(transition, manga)
            }

            missingChapterWarning(transition)
        }

        /**
         * Binds a previous chapter transition on this view and subscribes to the page load status.
         */
        private fun bindPrevChapterTransition(
            transition: ChapterTransition,
            manga: Manga,
        ) {
            val prevChapter = transition.to

            binding.lowerText.isVisible = prevChapter != null
            if (prevChapter != null) {
                binding.upperText.textAlignment = TEXT_ALIGNMENT_TEXT_START
                binding.upperText.text =
                    buildSpannedString {
                        bold { append(context.getString(R.string.previous_title)) }
                        append("\n${prevChapter.chapter.preferredChapterName(context, manga, preferences)}")
                    }
                binding.lowerText.text =
                    buildSpannedString {
                        bold { append(context.getString(R.string.current_chapter)) }
                        val name = transition.from.chapter.preferredChapterName(context, manga, preferences)
                        append("\n$name")
                    }
            } else {
                binding.upperText.textAlignment = TEXT_ALIGNMENT_CENTER
                binding.upperText.text = context.getString(R.string.theres_no_previous_chapter)
            }
        }

        /**
         * Binds a next chapter transition on this view and subscribes to the load status.
         */
        private fun bindNextChapterTransition(
            transition: ChapterTransition,
            manga: Manga,
        ) {
            val nextChapter = transition.to

            binding.lowerText.isVisible = nextChapter != null
            if (nextChapter != null) {
                binding.upperText.textAlignment = TEXT_ALIGNMENT_TEXT_START
                binding.upperText.text =
                    buildSpannedString {
                        bold { append(context.getString(R.string.finished_chapter)) }
                        val name = transition.from.chapter.preferredChapterName(context, manga, preferences)
                        append("\n$name")
                    }
                binding.lowerText.text =
                    buildSpannedString {
                        bold { append(context.getString(R.string.next_title)) }
                        append("\n${nextChapter.chapter.preferredChapterName(context, manga, preferences)}")
                    }
            } else {
                binding.upperText.textAlignment = TEXT_ALIGNMENT_CENTER
                binding.upperText.text = context.getString(R.string.theres_no_next_chapter)
            }
        }

        fun setTextColors(
            @ColorInt color: Int,
        ) {
            binding.upperText.setTextColor(color)
            binding.warningText.setTextColor(color)
            binding.lowerText.setTextColor(color)
        }

        private fun missingChapterWarning(transition: ChapterTransition) {
            if (transition.to == null) {
                binding.warning.isVisible = false
                return
            }

            val hasMissingChapters =
                when (transition) {
                    is ChapterTransition.Prev -> hasMissingChapters(transition.from, transition.to)
                    is ChapterTransition.Next -> hasMissingChapters(transition.to, transition.from)
                }

            if (!hasMissingChapters) {
                binding.warning.isVisible = false
                return
            }

            val chapterDifference =
                when (transition) {
                    is ChapterTransition.Prev -> calculateChapterDifference(transition.from, transition.to)
                    is ChapterTransition.Next -> calculateChapterDifference(transition.to, transition.from)
                }

            binding.warningText.text =
                resources.getQuantityString(R.plurals.missing_chapters_warning, chapterDifference.toInt(), chapterDifference.toInt())
            binding.warning.isVisible = true
        }
    }
