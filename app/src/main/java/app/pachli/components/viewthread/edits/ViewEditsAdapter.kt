package app.pachli.components.viewthread.edits

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.PollAdapter
import app.pachli.adapter.PollAdapter.DisplayMode
import app.pachli.core.activity.decodeBlurHash
import app.pachli.core.activity.emojify
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.designsystem.R as DR
import app.pachli.core.network.model.StatusEdit
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.databinding.ItemStatusEditBinding
import app.pachli.interfaces.LinkListener
import app.pachli.util.BindingHolder
import app.pachli.util.aspectRatios
import app.pachli.util.setClickableText
import app.pachli.viewdata.PollOptionViewData
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import org.xml.sax.XMLReader

class ViewEditsAdapter(
    private val edits: List<StatusEdit>,
    private val animateAvatars: Boolean,
    private val animateEmojis: Boolean,
    private val useBlurhash: Boolean,
    private val listener: LinkListener,
) : RecyclerView.Adapter<BindingHolder<ItemStatusEditBinding>>() {

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    /** Size of large text in this theme, in px */
    private var largeTextSizePx: Float = 0f

    /** Size of medium text in this theme, in px */
    private var mediumTextSizePx: Float = 0f

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BindingHolder<ItemStatusEditBinding> {
        val binding = ItemStatusEditBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        binding.statusEditMediaPreview.clipToOutline = true

        val typedValue = TypedValue()
        val context = binding.root.context
        val displayMetrics = context.resources.displayMetrics
        context.theme.resolveAttribute(DR.attr.status_text_large, typedValue, true)
        largeTextSizePx = typedValue.getDimension(displayMetrics)
        context.theme.resolveAttribute(DR.attr.status_text_medium, typedValue, true)
        mediumTextSizePx = typedValue.getDimension(displayMetrics)

        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemStatusEditBinding>, position: Int) {
        val edit = edits[position]

        val binding = holder.binding

        val context = binding.root.context

        val infoStringRes = if (position == edits.lastIndex) {
            R.string.status_created_info
        } else {
            R.string.status_edit_info
        }

        // Show the most recent version of the status using large text to make it clearer for
        // the user, and for similarity with thread view.
        val variableTextSize = if (position == edits.lastIndex) {
            mediumTextSizePx
        } else {
            largeTextSizePx
        }
        binding.statusEditContentWarningDescription.setTextSize(TypedValue.COMPLEX_UNIT_PX, variableTextSize)
        binding.statusEditContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, variableTextSize)
        binding.statusEditMediaSensitivity.setTextSize(TypedValue.COMPLEX_UNIT_PX, variableTextSize)

        val timestamp = absoluteTimeFormatter.format(edit.createdAt, false)

        binding.statusEditInfo.text = context.getString(infoStringRes, timestamp)

        if (edit.spoilerText.isEmpty()) {
            binding.statusEditContentWarningDescription.hide()
            binding.statusEditContentWarningSeparator.hide()
        } else {
            binding.statusEditContentWarningDescription.show()
            binding.statusEditContentWarningSeparator.show()
            binding.statusEditContentWarningDescription.text = edit.spoilerText.emojify(
                edit.emojis,
                binding.statusEditContentWarningDescription,
                animateEmojis,
            )
        }

        val emojifiedText = edit
            .content
            .parseAsMastodonHtml(PachliTagHandler(context))
            .emojify(edit.emojis, binding.statusEditContent, animateEmojis)

        setClickableText(binding.statusEditContent, emojifiedText, emptyList(), emptyList(), listener)

        val poll = edit.poll
        if (poll == null) {
            binding.statusEditPollOptions.hide()
            binding.statusEditPollDescription.hide()
        } else {
            binding.statusEditPollOptions.show()

            // not used for now since not reported by the api
            // https://github.com/mastodon/mastodon/issues/22571
            // binding.statusEditPollDescription.show()

            val pollAdapter = PollAdapter(
                options = poll.options.map { PollOptionViewData.from(it, false) },
                votesCount = 0,
                votersCount = null,
                edit.emojis,
                animateEmojis = animateEmojis,
                displayMode = if (poll.multiple) DisplayMode.MULTIPLE_CHOICE else DisplayMode.SINGLE_CHOICE,
                enabled = false,
                resultClickListener = null,
                pollOptionClickListener = null,
            )

            binding.statusEditPollOptions.adapter = pollAdapter
            binding.statusEditPollOptions.layoutManager = LinearLayoutManager(context)
        }

        if (edit.mediaAttachments.isEmpty()) {
            binding.statusEditMediaPreview.hide()
            binding.statusEditMediaSensitivity.hide()
        } else {
            binding.statusEditMediaPreview.show()
            binding.statusEditMediaPreview.aspectRatios = edit.mediaAttachments.aspectRatios()

            binding.statusEditMediaPreview.forEachIndexed { index, imageView, descriptionIndicator ->

                val attachment = edit.mediaAttachments[index]
                val hasDescription = !attachment.description.isNullOrBlank()

                if (hasDescription) {
                    imageView.contentDescription = attachment.description
                } else {
                    imageView.contentDescription =
                        imageView.context.getString(R.string.action_view_media)
                }
                descriptionIndicator.visibility = if (hasDescription) View.VISIBLE else View.GONE

                val blurhash = attachment.blurhash

                val placeholder: Drawable = if (blurhash != null && useBlurhash) {
                    decodeBlurHash(context, blurhash)
                } else {
                    ColorDrawable(MaterialColors.getColor(imageView, android.R.attr.colorBackground))
                }

                if (attachment.previewUrl.isNullOrEmpty()) {
                    imageView.removeFocalPoint()
                    Glide.with(imageView)
                        .load(placeholder)
                        .centerInside()
                        .into(imageView)
                } else {
                    val focus = attachment.meta?.focus

                    if (focus != null) {
                        imageView.setFocalPoint(focus)
                        Glide.with(imageView.context)
                            .load(attachment.previewUrl)
                            .placeholder(placeholder)
                            .centerInside()
                            .addListener(imageView)
                            .into(imageView)
                    } else {
                        imageView.removeFocalPoint()
                        Glide.with(imageView)
                            .load(attachment.previewUrl)
                            .placeholder(placeholder)
                            .centerInside()
                            .into(imageView)
                    }
                }
            }
            binding.statusEditMediaSensitivity.visible(edit.sensitive)
        }
    }

    override fun getItemCount() = edits.size

    companion object {
        private const val VIEW_TYPE_EDITS_NEWEST = 0
        private const val VIEW_TYPE_EDITS = 1
    }
}

/**
 * Handle XML tags created by [ViewEditsViewModel] and create custom spans to display inserted or
 * deleted text.
 */
class PachliTagHandler(val context: Context) : Html.TagHandler {
    /** Class to mark the start of a span of deleted text */
    class Del

    /** Class to mark the start of a span of inserted text */
    class Ins

    override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
        when (tag) {
            DELETED_TEXT_EL -> {
                if (opening) {
                    start(output as SpannableStringBuilder, Del())
                } else {
                    end(
                        output as SpannableStringBuilder,
                        Del::class.java,
                        DeletedTextSpan(context),
                    )
                }
            }
            INSERTED_TEXT_EL -> {
                if (opening) {
                    start(output as SpannableStringBuilder, Ins())
                } else {
                    end(
                        output as SpannableStringBuilder,
                        Ins::class.java,
                        InsertedTextSpan(context),
                    )
                }
            }
        }
    }

    /** @return the last span in [text] of type [kind], or null if that kind is not in text */
    private fun <T> getLast(text: Spanned, kind: Class<T>): Any? {
        val spans = text.getSpans(0, text.length, kind)
        return spans?.get(spans.size - 1)
    }

    /**
     * Mark the start of a span of [text] with [mark] so it can be discovered later by [end].
     */
    private fun start(text: SpannableStringBuilder, mark: Any) {
        val len = text.length
        text.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK)
    }

    /**
     * Set a [span] over the [text] most from the point recently marked with [mark] to the end
     * of the text.
     */
    private fun <T> end(text: SpannableStringBuilder, mark: Class<T>, span: Any) {
        val len = text.length
        val obj = getLast(text, mark)
        val where = text.getSpanStart(obj)
        text.removeSpan(obj)
        if (where != len) {
            text.setSpan(span, where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Span that signifies deleted text */
    class DeletedTextSpan(context: Context) : CharacterStyle() {
        private var bgColor: Int

        init {
            bgColor = context.getColor(DR.color.view_edits_background_delete)
        }

        override fun updateDrawState(tp: TextPaint) {
            tp.bgColor = bgColor
            tp.isStrikeThruText = true
        }
    }

    /**
     *  Span that signifies inserted text.
     *
     *  Derives from [MetricAffectingSpan] as making the font bold can change
     *  its metrics.
     */
    class InsertedTextSpan(context: Context) : MetricAffectingSpan() {
        private var bgColor: Int

        init {
            bgColor = context.getColor(DR.color.view_edits_background_insert)
        }

        override fun updateDrawState(tp: TextPaint) {
            updateMeasureState(tp)
            tp.bgColor = bgColor
        }

        override fun updateMeasureState(tp: TextPaint) {
            // Try and create a bold version of the active font to preserve
            // the user's custom font selection.
            tp.typeface = Typeface.create(tp.typeface, Typeface.BOLD)
        }
    }

    companion object {
        /** XML element to represent text that has been deleted */
        // Can't be an element that Android's HTML parser recognises, otherwise the tagHandler
        // won't be called for it.
        const val DELETED_TEXT_EL = "pachli-del"

        /** XML element to represent text that has been inserted */
        // Can't be an element that Android's HTML parser recognises, otherwise the tagHandler
        // won't be called for it.
        const val INSERTED_TEXT_EL = "pachli-ins"
    }
}
