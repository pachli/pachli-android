/*
 * Copyright 2017 Andrew Dawson
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewGroupCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.pachli.adapter.AccountFieldEditAdapter
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.InstanceInfo.Companion.DEFAULT_MAX_ACCOUNT_FIELDS
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.extensions.InsetType
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.core.ui.extensions.applyWindowInsets
import app.pachli.core.ui.extensions.await
import app.pachli.core.ui.extensions.getErrorString
import app.pachli.databinding.ActivityEditProfileBinding
import app.pachli.util.Error
import app.pachli.util.Loading
import app.pachli.util.Success
import app.pachli.viewmodel.EditProfileViewModel
import app.pachli.viewmodel.ProfileDataInUi
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class EditProfileActivity : BaseActivity() {

    companion object {
        const val AVATAR_SIZE = 400
        const val HEADER_WIDTH = 1500
        const val HEADER_HEIGHT = 500
    }

    private val viewModel: EditProfileViewModel by viewModels()

    private val binding by viewBinding(ActivityEditProfileBinding::inflate)

    private val accountFieldEditAdapter: AccountFieldEditAdapter = AccountFieldEditAdapter {
        viewModel.onChange(currentProfileData)
    }

    private var maxAccountFields = DEFAULT_MAX_ACCOUNT_FIELDS

    private enum class PickType {
        AVATAR,
        HEADER,
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result is CropImage.CancelledResult) {
            return@registerForActivityResult
        }

        if (!result.isSuccessful) {
            return@registerForActivityResult onPickFailure(result.error)
        }

        if (result.uriContent == viewModel.getAvatarUri()) {
            viewModel.newAvatarPicked()
        } else {
            viewModel.newHeaderPicked()
        }
    }

    private val currentProfileData
        get() = ProfileDataInUi(
            displayName = binding.displayNameEditText.text.toString(),
            note = binding.noteEditText.text.toString(),
            locked = binding.lockedCheckBox.isChecked,
            fields = accountFieldEditAdapter.getFieldData(),
        )

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showUnsavedChangesDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.includedToolbar.appbar.applyDefaultWindowInsets()
        binding.scrollView.applyWindowInsets(
            left = InsetType.PADDING,
            right = InsetType.PADDING,
            bottom = InsetType.PADDING,
            withIme = true,
        )

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setTitle(R.string.title_edit_profile)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.avatarButton.setOnClickListener { pickMedia(PickType.AVATAR) }
        binding.headerButton.setOnClickListener { pickMedia(PickType.HEADER) }

        binding.fieldList.layoutManager = LinearLayoutManager(this)
        binding.fieldList.adapter = accountFieldEditAdapter

        val plusDrawable = IconicsDrawable(this, GoogleMaterial.Icon.gmd_add).apply {
            sizeDp = 12
            colorInt = Color.WHITE
        }

        binding.addFieldButton.setCompoundDrawablesRelativeWithIntrinsicBounds(plusDrawable, null, null, null)

        binding.addFieldButton.setOnClickListener {
            accountFieldEditAdapter.addField()
            if (accountFieldEditAdapter.itemCount >= maxAccountFields) {
                it.isVisible = false
            }

            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, it.bottom)
            }
        }

        viewModel.obtainProfile(intent.pachliAccountId)

        viewModel.profileData.observe(this) { profileRes ->
            when (profileRes) {
                is Success -> {
                    val me = profileRes.data
                    if (me != null) {
                        binding.displayNameEditText.setText(me.displayName)
                        binding.noteEditText.setText(me.source.note)
                        binding.lockedCheckBox.isChecked = me.locked

                        accountFieldEditAdapter.setFields(me.source.fields)
                        binding.addFieldButton.isVisible =
                            me.source.fields.size < maxAccountFields

                        if (viewModel.avatarData.value == null) {
                            glide.load(me.avatar)
                                .placeholder(DR.drawable.avatar_default)
                                .transform(
                                    FitCenter(),
                                    RoundedCorners(resources.getDimensionPixelSize(DR.dimen.avatar_radius_80dp)),
                                )
                                .into(binding.avatarPreview)
                        }

                        if (viewModel.headerData.value == null) {
                            glide.load(me.header)
                                .into(binding.headerPreview)
                        }
                    }
                }
                is Error -> {
                    Snackbar.make(binding.avatarButton, app.pachli.core.ui.R.string.error_generic, Snackbar.LENGTH_LONG)
                        .setAction(app.pachli.core.ui.R.string.action_retry) {
                            viewModel.obtainProfile(intent.pachliAccountId)
                        }
                        .show()
                }
                is Loading -> { }
            }
        }

        lifecycleScope.launch {
            viewModel.instanceData.collect { instanceInfo ->
                maxAccountFields = instanceInfo.maxFields
                accountFieldEditAdapter.setFieldLimits(instanceInfo.maxFieldNameLength, instanceInfo.maxFieldValueLength)
                binding.addFieldButton.isVisible =
                    accountFieldEditAdapter.itemCount < maxAccountFields
            }
        }

        observeImage(viewModel.avatarData, binding.avatarPreview, true)
        observeImage(viewModel.headerData, binding.headerPreview, false)

        viewModel.saveData.observe(
            this,
        ) {
            when (it) {
                is Success -> {
                    finish()
                }
                is Loading -> {
                    binding.saveProgressBar.visibility = View.VISIBLE
                }
                is Error -> {
                    onSaveFailure(it.cause?.getErrorString(this))
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isDirty.collectLatest { onBackPressedCallback.isEnabled = it }
        }

        binding.displayNameEditText.doAfterTextChanged {
            viewModel.onChange(currentProfileData)
        }

        binding.noteEditText.doAfterTextChanged {
            viewModel.onChange(currentProfileData)
        }

        binding.lockedCheckBox.setOnCheckedChangeListener { _, _ ->
            viewModel.onChange(currentProfileData)
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            viewModel.updateProfile(currentProfileData)
        }
    }

    private fun observeImage(
        liveData: LiveData<Uri>,
        imageView: ImageView,
        roundedCorners: Boolean,
    ) {
        liveData.observe(
            this,
        ) { imageUri ->

            // skipping all caches so we can always reuse the same uri
            val request = glide.load(imageUri)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)

            if (roundedCorners) {
                request.transform(
                    FitCenter(),
                    RoundedCorners(resources.getDimensionPixelSize(DR.dimen.avatar_radius_80dp)),
                ).into(imageView)
            } else {
                request.into(imageView)
            }

            imageView.show()
        }
    }

    private fun pickMedia(pickType: PickType) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        when (pickType) {
            PickType.AVATAR -> {
                cropImage.launch(
                    CropImageContractOptions(
                        uri = null,
                        cropImageOptions = CropImageOptions(
                            customOutputUri = viewModel.getAvatarUri(),
                            imageSourceIncludeCamera = false,
                            imageSourceIncludeGallery = true,
                            aspectRatioX = AVATAR_SIZE,
                            aspectRatioY = AVATAR_SIZE,
                            fixAspectRatio = true,
                            outputRequestWidth = AVATAR_SIZE,
                            outputRequestHeight = AVATAR_SIZE,
                            outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                            outputCompressFormat = Bitmap.CompressFormat.PNG,
                        ),
                    ),
                )
            }
            PickType.HEADER -> {
                cropImage.launch(
                    CropImageContractOptions(
                        uri = null,
                        cropImageOptions = CropImageOptions(
                            customOutputUri = viewModel.getHeaderUri(),
                            imageSourceIncludeCamera = false,
                            imageSourceIncludeGallery = true,
                            aspectRatioX = HEADER_WIDTH,
                            aspectRatioY = HEADER_HEIGHT,
                            fixAspectRatio = true,
                            outputRequestWidth = HEADER_WIDTH,
                            outputRequestHeight = HEADER_HEIGHT,
                            outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                            outputCompressFormat = Bitmap.CompressFormat.PNG,
                        ),
                    ),
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.edit_profile_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                save()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun save() = viewModel.save(currentProfileData)

    private fun onSaveFailure(msg: String?) {
        val errorMsg = msg ?: getString(R.string.error_media_upload_sending)
        Snackbar.make(binding.avatarButton, errorMsg, Snackbar.LENGTH_LONG).show()
        binding.saveProgressBar.visibility = View.GONE
    }

    private fun onPickFailure(throwable: Throwable?) {
        Timber.w(throwable, "failed to pick media")
        Snackbar.make(binding.avatarButton, R.string.error_media_upload_sending, Snackbar.LENGTH_LONG).show()
    }

    private fun showUnsavedChangesDialog() = lifecycleScope.launch {
        when (launchSaveDialog()) {
            AlertDialog.BUTTON_POSITIVE -> save()
            else -> finish()
        }
    }

    private suspend fun launchSaveDialog() = AlertDialog.Builder(this)
        .setMessage(getString(R.string.dialog_save_profile_changes_message))
        .create()
        .await(R.string.action_save, R.string.action_discard)
}
