package app.pachli.components.filters

import android.content.Context
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.R
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterKeyword
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.extensions.await
import app.pachli.databinding.ActivityEditContentFilterBinding
import app.pachli.databinding.DialogFilterBinding
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Edit a single server-side content filter.
 */
@AndroidEntryPoint
class EditContentFilterActivity : BaseActivity() {
    private val binding by viewBinding(ActivityEditContentFilterBinding::inflate)

    // Pass the optional filter and filterId values from the intent to
    // EditContentFilterViewModel.
    private val viewModel: EditContentFilterViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<EditContentFilterViewModel.Factory> { factory ->
                factory.create(
                    intent.pachliAccountId,
                    EditContentFilterActivityIntent.getContentFilter(intent),
                    EditContentFilterActivityIntent.getContentFilterId(intent),
                )
            }
        },
    )

    private lateinit var filterDurationAdapter: FilterDurationAdapter

    private lateinit var filterContextSwitches: Map<SwitchMaterial, FilterContext>

    /** The active snackbar */
    private var snackbar: Snackbar? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            lifecycleScope.launch {
                if (showUnsavedChangesFilterDialog() == BUTTON_NEGATIVE) finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        binding.apply {
            filterContextSwitches = mapOf(
                filterContextHome to FilterContext.HOME,
                filterContextNotifications to FilterContext.NOTIFICATIONS,
                filterContextPublic to FilterContext.PUBLIC,
                filterContextThread to FilterContext.THREAD,
                filterContextAccount to FilterContext.ACCOUNT,
            )
        }

        setContentView(binding.root)
        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setTitle(
            when (viewModel.uiMode) {
                UiMode.CREATE -> R.string.filter_addition_title
                UiMode.EDIT -> R.string.filter_edit_title
            },
        )

        binding.actionChip.setOnClickListener { showAddKeywordDialog() }

        filterDurationAdapter = FilterDurationAdapter(this, viewModel.uiMode)
        binding.filterDurationSpinner.adapter = filterDurationAdapter
        binding.filterDurationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setExpiresIn(filterDurationAdapter.getItem(position)!!.duration)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                viewModel.setExpiresIn(0)
            }
        }

        binding.filterSaveButton.setOnClickListener { saveChanges() }
        binding.filterDeleteButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.contentFilterViewData.value.get()?.let {
                    if (showDeleteFilterDialog(it.title) == BUTTON_POSITIVE) deleteFilter()
                }
            }
        }
        binding.filterDeleteButton.visible(viewModel.uiMode == UiMode.EDIT)

        for (switch in filterContextSwitches.keys) {
            switch.setOnCheckedChangeListener { _, isChecked ->
                val context = filterContextSwitches[switch]!!
                if (isChecked) {
                    viewModel.addContext(context)
                } else {
                    viewModel.deleteContext(context)
                }
            }
        }
        binding.filterTitle.doAfterTextChanged { editable ->
            viewModel.setTitle(editable.toString())
        }
        binding.filterActionWarn.setOnCheckedChangeListener { _, checked ->
            viewModel.setAction(if (checked) FilterAction.WARN else FilterAction.HIDE)
        }

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.uiResult.collect(::bindUiResult) }

                launch { viewModel.contentFilterViewData.collect(::bindFilter) }

                launch { viewModel.isDirty.collectLatest { onBackPressedCallback.isEnabled = it } }

                launch {
                    viewModel.validationErrors.collectLatest { errors ->
                        binding.filterTitleWrapper.error = if (errors.contains(ContentFilterValidationError.NO_TITLE)) {
                            getString(R.string.error_filter_missing_title)
                        } else {
                            null
                        }

                        binding.keywordChipsError.isVisible = errors.contains(ContentFilterValidationError.NO_KEYWORDS)
                        binding.filterContextError.isVisible = errors.contains(ContentFilterValidationError.NO_CONTEXT)
                    }
                }

                launch {
                    viewModel.isDirty.combine(viewModel.validationErrors) { dirty, errors ->
                        dirty && errors.isEmpty()
                    }.collectLatest { binding.filterSaveButton.isEnabled = it }
                }
            }
        }
    }

    /** Act on the result of UI actions */
    private fun bindUiResult(uiResult: Result<UiSuccess, UiError>) {
        uiResult.onFailure(::bindUiError)
        uiResult.onSuccess { uiSuccess ->
            when (uiSuccess) {
                UiSuccess.SaveFilter -> finish()
                UiSuccess.DeleteFilter -> finish()
            }
        }
    }

    private fun bindUiError(uiError: UiError) {
        val message = uiError.fmt(this)
        snackbar?.dismiss()
        try {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).apply {
                setAction(app.pachli.core.ui.R.string.action_retry) {
                    when (uiError) {
                        is UiError.DeleteContentFilterError -> viewModel.deleteContentFilter()
                        is UiError.GetContentFilterError -> viewModel.reload()
                        is UiError.SaveContentFilterError -> viewModel.saveChanges()
                    }
                }
                show()
                snackbar = this
            }
        } catch (_: IllegalArgumentException) {
            // On rare occasions this code is running before the fragment's
            // view is connected to the parent. This causes Snackbar.make()
            // to crash.  See https://issuetracker.google.com/issues/228215869.
            // For now, swallow the exception.
        }
    }

    private fun bindFilter(result: Result<ContentFilterViewData?, UiError.GetContentFilterError>) {
        result.onFailure(::bindUiError)

        result.onSuccess { filterViewData ->
            filterViewData ?: return

            when (val expiresIn = filterViewData.expiresIn) {
                -1 -> binding.filterDurationSpinner.setSelection(0)
                else -> {
                    filterDurationAdapter.items.indexOfFirst { it.duration == expiresIn }.let {
                        if (it == -1) {
                            binding.filterDurationSpinner.setSelection(0)
                        } else {
                            binding.filterDurationSpinner.setSelection(it)
                        }
                    }
                }
            }

            if (filterViewData.title != binding.filterTitle.text.toString()) {
                // We also get this callback when typing in the field,
                // which messes with the cursor focus
                binding.filterTitle.setText(filterViewData.title)
            }

            bindKeywords(filterViewData.keywords)

            for ((key, value) in filterContextSwitches) {
                key.isChecked = filterViewData.contexts.contains(value)
            }

            when (filterViewData.filterAction) {
                FilterAction.HIDE -> binding.filterActionHide.isChecked = true
                else -> binding.filterActionWarn.isChecked = true
            }
        }
    }

    private fun bindKeywords(newKeywords: List<FilterKeyword>) {
        newKeywords.forEachIndexed { index, filterKeyword ->
            val chip = binding.keywordChips.getChildAt(index).takeUnless {
                it.id == R.id.actionChip
            } as Chip? ?: Chip(this).apply {
                setCloseIconResource(R.drawable.ic_cancel_24dp)
                isCheckable = false
                binding.keywordChips.addView(this, binding.keywordChips.size - 1)
            }

            chip.text = if (filterKeyword.wholeWord) {
                binding.root.context.getString(
                    R.string.filter_keyword_display_format,
                    filterKeyword.keyword,
                )
            } else {
                filterKeyword.keyword
            }
            chip.isCloseIconVisible = true
            chip.setOnClickListener {
                showEditKeywordDialog(newKeywords[index])
            }
            chip.setOnCloseIconClickListener {
                viewModel.deleteKeyword(newKeywords[index])
            }
        }

        while (binding.keywordChips.size - 1 > newKeywords.size) {
            binding.keywordChips.removeViewAt(newKeywords.size)
        }
    }

    private fun showAddKeywordDialog() {
        val binding = DialogFilterBinding.inflate(layoutInflater)
        binding.phraseWholeWord.isChecked = true
        AlertDialog.Builder(this)
            .setTitle(R.string.filter_keyword_addition_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.addKeyword(
                    FilterKeyword(
                        "",
                        binding.phraseEditText.text.toString(),
                        binding.phraseWholeWord.isChecked,
                    ),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditKeywordDialog(keyword: FilterKeyword) {
        val binding = DialogFilterBinding.inflate(layoutInflater)
        binding.phraseEditText.setText(keyword.keyword)
        binding.phraseWholeWord.isChecked = keyword.wholeWord

        AlertDialog.Builder(this)
            .setTitle(R.string.filter_edit_keyword_title)
            .setView(binding.root)
            .setPositiveButton(R.string.filter_dialog_update_button) { _, _ ->
                viewModel.updateKeyword(
                    keyword,
                    keyword.copy(
                        keyword = binding.phraseEditText.text.toString(),
                        wholeWord = binding.phraseWholeWord.isChecked,
                    ),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Dialog that warns the user they have unsaved changes, and prompts
     * to continue editing or discard the changes.
     *
     * @return [BUTTON_NEGATIVE] if the user chose to discard the changes,
     *   [BUTTON_POSITIVE] if the user chose to continue editing.
     */
    suspend fun showUnsavedChangesFilterDialog() = AlertDialog.Builder(this)
        .setMessage(R.string.unsaved_changes)
        .setCancelable(true)
        .create()
        .await(R.string.action_continue_edit, R.string.action_discard)

    // TODO use a progress bar here (see EditProfileActivity/activity_edit_profile.xml for example)?
    private fun saveChanges() = viewModel.saveChanges()

    private fun deleteFilter() = viewModel.deleteContentFilter()
}

data class FilterDuration(
    /** Filter duration, in seconds. -1 means no change, 0 means indefinite. */
    val duration: Int,
    /** Label to use for this duration. */
    val label: String,
)

/**
 * Displays [FilterDuration] derived from R.array.filter_duration_values and
 * R.array.filter_duration_labels.
 *
 * In addition, if [uiMode] is [UiMode.EDIT] an extra duration corresponding to
 * "no change" is included in the list of possible values.
 */
class FilterDurationAdapter(context: Context, uiMode: UiMode) : ArrayAdapter<FilterDuration>(
    context,
    android.R.layout.simple_list_item_1,
) {
    val items = buildList {
        if (uiMode == UiMode.EDIT) {
            add(FilterDuration(-1, context.getString(R.string.duration_no_change)))
        }

        val values = context.resources.getIntArray(R.array.filter_duration_values)
        val labels = context.resources.getStringArray(R.array.filter_duration_labels)
        assert(values.size == labels.size)

        values.zip(labels) { value, label ->
            add(FilterDuration(duration = value, label = label))
        }
    }

    init {
        addAll(items)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        getItem(position)?.let { item -> (view as? TextView)?.text = item.label }
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        getItem(position)?.let { item -> (view as? TextView)?.text = item.label }
        return view
    }
}
