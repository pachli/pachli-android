package app.pachli.adapter;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import app.pachli.R;
import app.pachli.ViewMediaActivity;
import app.pachli.entity.Attachment;
import app.pachli.entity.Attachment.Focus;
import app.pachli.entity.Attachment.MetaData;
import app.pachli.entity.Card;
import app.pachli.entity.Emoji;
import app.pachli.entity.Filter;
import app.pachli.entity.FilterResult;
import app.pachli.entity.HashTag;
import app.pachli.entity.Poll;
import app.pachli.entity.PreviewCardKind;
import app.pachli.entity.Status;
import app.pachli.interfaces.StatusActionListener;
import app.pachli.util.AbsoluteTimeFormatter;
import app.pachli.util.AttachmentHelper;
import app.pachli.util.CardViewMode;
import app.pachli.util.CompositeWithOpaqueBackground;
import app.pachli.util.CustomEmojiHelper;
import app.pachli.util.ImageLoadingHelper;
import app.pachli.util.LinkHelper;
import app.pachli.util.NumberUtils;
import app.pachli.util.StatusDisplayOptions;
import app.pachli.util.TimestampUtils;
import app.pachli.util.TouchDelegateHelper;
import app.pachli.view.MediaPreviewImageView;
import app.pachli.view.MediaPreviewLayout;
import app.pachli.view.PollView;
import app.pachli.view.PreviewCardView;
import app.pachli.viewdata.PollViewData;
import app.pachli.viewdata.StatusViewData;
import at.connyduck.sparkbutton.SparkButton;
import at.connyduck.sparkbutton.helpers.Utils;
import kotlin.collections.CollectionsKt;

public abstract class StatusBaseViewHolder extends RecyclerView.ViewHolder {
    public static class Key {
        public static final String KEY_CREATED = "created";
    }

    private final String TAG = "StatusBaseViewHolder";

    private final TextView displayName;
    private final TextView username;
    private final ImageButton replyButton;
    private final TextView replyCountLabel;
    private final SparkButton reblogButton;
    private final SparkButton favouriteButton;
    private final SparkButton bookmarkButton;
    private final ImageButton moreButton;
    private final ConstraintLayout mediaContainer;
    protected final MediaPreviewLayout mediaPreview;
    private final TextView sensitiveMediaWarning;
    private final View sensitiveMediaShow;
    @NonNull
    protected final TextView[] mediaLabels;
    @NonNull
    protected final CharSequence[] mediaDescriptions;
    private final MaterialButton contentWarningButton;
    private final ImageView avatarInset;

    public final ImageView avatar;
    public final TextView metaInfo;
    public final TextView content;
    public final TextView contentWarningDescription;

    @NonNull
    private final PollView pollView;
    private final PreviewCardView cardView;
    protected final LinearLayout filteredPlaceholder;
    protected final TextView filteredPlaceholderLabel;
    protected final Button filteredPlaceholderShowButton;
    protected final ConstraintLayout statusContainer;

    private final NumberFormat numberFormat = NumberFormat.getNumberInstance();
    private final AbsoluteTimeFormatter absoluteTimeFormatter = new AbsoluteTimeFormatter();

    protected final int avatarRadius48dp;
    private final int avatarRadius36dp;
    private final int avatarRadius24dp;

    @NonNull
    private final Drawable mediaPreviewUnloaded;

    protected StatusBaseViewHolder(@NonNull View itemView) {
        super(itemView);
        displayName = itemView.findViewById(R.id.status_display_name);
        username = itemView.findViewById(R.id.status_username);
        metaInfo = itemView.findViewById(R.id.status_meta_info);
        content = itemView.findViewById(R.id.status_content);
        avatar = itemView.findViewById(R.id.status_avatar);
        replyButton = itemView.findViewById(R.id.status_reply);
        replyCountLabel = itemView.findViewById(R.id.status_replies);
        reblogButton = itemView.findViewById(R.id.status_inset);
        favouriteButton = itemView.findViewById(R.id.status_favourite);
        bookmarkButton = itemView.findViewById(R.id.status_bookmark);
        moreButton = itemView.findViewById(R.id.status_more);

        mediaContainer = itemView.findViewById(R.id.status_media_preview_container);
        mediaContainer.setClipToOutline(true);
        mediaPreview = itemView.findViewById(R.id.status_media_preview);

        sensitiveMediaWarning = itemView.findViewById(R.id.status_sensitive_media_warning);
        sensitiveMediaShow = itemView.findViewById(R.id.status_sensitive_media_button);
        mediaLabels = new TextView[]{
                itemView.findViewById(R.id.status_media_label_0),
                itemView.findViewById(R.id.status_media_label_1),
                itemView.findViewById(R.id.status_media_label_2),
                itemView.findViewById(R.id.status_media_label_3)
        };
        mediaDescriptions = new CharSequence[mediaLabels.length];
        contentWarningDescription = itemView.findViewById(R.id.status_content_warning_description);
        contentWarningButton = itemView.findViewById(R.id.status_content_warning_button);
        avatarInset = itemView.findViewById(R.id.status_avatar_inset);

        pollView = itemView.findViewById(R.id.status_poll);

        cardView = itemView.findViewById(R.id.status_card_view);

        filteredPlaceholder = itemView.findViewById(R.id.status_filtered_placeholder);
        filteredPlaceholderLabel = itemView.findViewById(R.id.status_filter_label);
        filteredPlaceholderShowButton = itemView.findViewById(R.id.status_filter_show_anyway);
        statusContainer = itemView.findViewById(R.id.status_container);

        this.avatarRadius48dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_48dp);
        this.avatarRadius36dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_36dp);
        this.avatarRadius24dp = itemView.getContext().getResources().getDimensionPixelSize(R.dimen.avatar_radius_24dp);

        mediaPreviewUnloaded = new ColorDrawable(MaterialColors.getColor(itemView, android.R.attr.textColorLink));

        TouchDelegateHelper.expandTouchSizeToFillRow((ViewGroup) itemView, CollectionsKt.listOfNotNull(replyButton, reblogButton, favouriteButton, bookmarkButton, moreButton));
    }

    protected void setDisplayName(@NonNull String name, @Nullable List<Emoji> customEmojis, @NonNull StatusDisplayOptions statusDisplayOptions) {
        CharSequence emojifiedName = CustomEmojiHelper.emojify(
                name, customEmojis, displayName, statusDisplayOptions.animateEmojis()
        );
        displayName.setText(emojifiedName);
    }

    protected void setUsername(@NonNull String name) {
        Context context = username.getContext();
        String usernameText = context.getString(R.string.post_username_format, name);
        username.setText(usernameText);
    }

    public void toggleContentWarning() {
        contentWarningButton.performClick();
    }

    protected void setSpoilerAndContent(@NonNull StatusViewData status,
                                        @NonNull StatusDisplayOptions statusDisplayOptions,
                                        @NonNull final StatusActionListener listener) {

        Status actionable = status.getActionable();
        String spoilerText = status.getSpoilerText();
        List<Emoji> emojis = actionable.getEmojis();

        boolean sensitive = !TextUtils.isEmpty(spoilerText);
        boolean expanded = status.isExpanded();

        if (sensitive) {
            CharSequence emojiSpoiler = CustomEmojiHelper.emojify(
                    spoilerText, emojis, contentWarningDescription, statusDisplayOptions.animateEmojis()
            );
            contentWarningDescription.setText(emojiSpoiler);
            contentWarningDescription.setVisibility(View.VISIBLE);
            contentWarningButton.setVisibility(View.VISIBLE);
            setContentWarningButtonText(expanded);
            contentWarningButton.setOnClickListener(view -> toggleExpandedState(true, !expanded, status, statusDisplayOptions, listener));
            this.setTextVisible(true, expanded, status, statusDisplayOptions, listener);
        } else {
            contentWarningDescription.setVisibility(View.GONE);
            contentWarningButton.setVisibility(View.GONE);
            this.setTextVisible(false, true, status, statusDisplayOptions, listener);
        }
    }

    private void setContentWarningButtonText(boolean expanded) {
        if (expanded) {
            contentWarningButton.setText(R.string.post_content_warning_show_less);
        } else {
            contentWarningButton.setText(R.string.post_content_warning_show_more);
        }
    }

    protected void toggleExpandedState(boolean sensitive,
                                       boolean expanded,
                                       @NonNull final StatusViewData status,
                                       @NonNull final StatusDisplayOptions statusDisplayOptions,
                                       @NonNull final StatusActionListener listener) {

        contentWarningDescription.invalidate();
        int adapterPosition = getBindingAdapterPosition();
        if (adapterPosition != RecyclerView.NO_POSITION) {
            listener.onExpandedChange(expanded, adapterPosition);
        }
        setContentWarningButtonText(expanded);

        this.setTextVisible(sensitive, expanded, status, statusDisplayOptions, listener);

        setupCard(status, expanded, statusDisplayOptions.cardViewMode(), statusDisplayOptions, listener);
    }

    private void setTextVisible(boolean sensitive,
                                boolean expanded,
                                @NonNull final StatusViewData status,
                                @NonNull final StatusDisplayOptions statusDisplayOptions,
                                @NonNull final StatusActionListener listener) {

        Status actionable = status.getActionable();
        Spanned content = status.getContent();
        List<Status.Mention> mentions = actionable.getMentions();
        List<HashTag> tags =actionable.getTags();
        List<Emoji> emojis = actionable.getEmojis();
        Poll poll = actionable.getPoll();

        if (expanded) {
            CharSequence emojifiedText = CustomEmojiHelper.emojify(content, emojis, this.content, statusDisplayOptions.animateEmojis());
            LinkHelper.setClickableText(this.content, emojifiedText, mentions, tags, listener);
            for (int i = 0; i < mediaLabels.length; ++i) {
                updateMediaLabel(i, sensitive, true);
            }
            if (poll != null) {
                PollView.OnClickListener pollListener = (List<Integer> choices) -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        if (choices == null) {
                            listener.onViewThread(position);
                        } else {
                            listener.onVoteInPoll(position, choices);
                        }
                    }
                };
                pollView.bind(
                    PollViewData.Companion.from(poll),
                    emojis,
                    statusDisplayOptions,
                    numberFormat,
                    absoluteTimeFormatter,
                    pollListener
                );
            } else {
                pollView.hide();
            }
        } else {
            pollView.hide();
            LinkHelper.setClickableMentions(this.content, mentions, listener);
        }
        if (TextUtils.isEmpty(this.content.getText())) {
            this.content.setVisibility(View.GONE);
        } else {
            this.content.setVisibility(View.VISIBLE);
        }
    }

    private void setAvatar(String url,
                           @Nullable String rebloggedUrl,
                           boolean isBot,
                           @NonNull StatusDisplayOptions statusDisplayOptions) {

        int avatarRadius;
        if (TextUtils.isEmpty(rebloggedUrl)) {
            avatar.setPaddingRelative(0, 0, 0, 0);

            if (statusDisplayOptions.showBotOverlay() && isBot) {
                avatarInset.setVisibility(View.VISIBLE);
                Glide.with(avatarInset)
                        .load(R.drawable.bot_badge)
                        .into(avatarInset);
            } else {
                avatarInset.setVisibility(View.GONE);
            }

            avatarRadius = avatarRadius48dp;

        } else {
            int padding = Utils.dpToPx(avatar.getContext(), 12);
            avatar.setPaddingRelative(0, 0, padding, padding);

            avatarInset.setVisibility(View.VISIBLE);
            avatarInset.setBackground(null);
            ImageLoadingHelper.loadAvatar(rebloggedUrl, avatarInset, avatarRadius24dp,
                    statusDisplayOptions.animateAvatars(), null);

            avatarRadius = avatarRadius36dp;
        }

        ImageLoadingHelper.loadAvatar(url, avatar, avatarRadius,
            statusDisplayOptions.animateAvatars(),
            Collections.singletonList(new CompositeWithOpaqueBackground(avatar)));
    }

    protected void setMetaData(@NonNull StatusViewData statusViewData, @NonNull StatusDisplayOptions statusDisplayOptions, @NonNull StatusActionListener listener) {

        Status status = statusViewData.getActionable();
        Date createdAt = status.getCreatedAt();
        Date editedAt = status.getEditedAt();

        String timestampText;
        if (statusDisplayOptions.useAbsoluteTime()) {
            timestampText = absoluteTimeFormatter.format(createdAt, true);
        } else {
            if (createdAt == null) {
                timestampText = "?m";
            } else {
                long then = createdAt.getTime();
                long now = System.currentTimeMillis();
                timestampText = TimestampUtils.getRelativeTimeSpanString(metaInfo.getContext(), then, now);
            }
        }

        if (editedAt != null) {
            timestampText = metaInfo.getContext().getString(R.string.post_timestamp_with_edited_indicator, timestampText);
        }
        metaInfo.setText(timestampText);
    }

    private CharSequence getCreatedAtDescription(@Nullable Date createdAt,
                                                 @NonNull StatusDisplayOptions statusDisplayOptions) {
        if (statusDisplayOptions.useAbsoluteTime()) {
            return absoluteTimeFormatter.format(createdAt, true);
        } else {
            /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
             * as 17 meters instead of minutes. */

            if (createdAt == null) {
                return "? minutes";
            } else {
                long then = createdAt.getTime();
                long now = System.currentTimeMillis();
                return DateUtils.getRelativeTimeSpanString(then, now,
                        DateUtils.SECOND_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE);
            }
        }
    }

    protected void setIsReply(boolean isReply) {
        if (isReply) {
            replyButton.setImageResource(R.drawable.ic_reply_all_24dp);
        } else {
            replyButton.setImageResource(R.drawable.ic_reply_24dp);
        }

    }

    protected void setReplyCount(int repliesCount, boolean fullStats) {
        // This label only exists in the non-detailed view (to match the web ui)
        if (replyCountLabel == null) return;

        if (fullStats) {
            replyCountLabel.setText(NumberUtils.formatNumber(repliesCount, 1000));
            return;
        }

        // Show "0", "1", or "1+" for replies otherwise, so the user knows if there is a thread
        // that they can click through to read.
        replyCountLabel.setText((repliesCount > 1 ? replyCountLabel.getContext().getString(R.string.status_count_one_plus) : Integer.toString(repliesCount)));
    }

    private void setReblogged(boolean reblogged) {
        reblogButton.setChecked(reblogged);
    }

    // This should only be called after setReblogged, in order to override the tint correctly.
    private void setRebloggingEnabled(boolean enabled, Status.Visibility visibility) {
        reblogButton.setEnabled(enabled && visibility != Status.Visibility.PRIVATE);

        if (enabled) {
            int inactiveId;
            int activeId;
            if (visibility == Status.Visibility.PRIVATE) {
                inactiveId = R.drawable.ic_reblog_private_24dp;
                activeId = R.drawable.ic_reblog_private_active_24dp;
            } else {
                inactiveId = R.drawable.ic_reblog_24dp;
                activeId = R.drawable.ic_reblog_active_24dp;
            }
            reblogButton.setInactiveImage(inactiveId);
            reblogButton.setActiveImage(activeId);
        } else {
            int disabledId;
            if (visibility == Status.Visibility.DIRECT) {
                disabledId = R.drawable.ic_reblog_direct_24dp;
            } else {
                disabledId = R.drawable.ic_reblog_private_24dp;
            }
            reblogButton.setInactiveImage(disabledId);
            reblogButton.setActiveImage(disabledId);
        }
    }

    protected void setFavourited(boolean favourited) {
        favouriteButton.setChecked(favourited);
    }

    protected void setBookmarked(boolean bookmarked) {
        bookmarkButton.setChecked(bookmarked);
    }

    @NonNull
    private BitmapDrawable decodeBlurHash(@NonNull String blurhash) {
        return ImageLoadingHelper.decodeBlurHash(this.avatar.getContext(), blurhash);
    }

    private void loadImage(@NonNull MediaPreviewImageView imageView,
                           @Nullable String previewUrl,
                           @Nullable MetaData meta,
                           @Nullable String blurhash) {

        Drawable placeholder = blurhash != null ? decodeBlurHash(blurhash) : mediaPreviewUnloaded;

        if (TextUtils.isEmpty(previewUrl)) {
            imageView.removeFocalPoint();

            Glide.with(imageView)
                    .load(placeholder)
                    .centerInside()
                    .into(imageView);
        } else {
            Focus focus = meta != null ? meta.getFocus() : null;

            if (focus != null) { // If there is a focal point for this attachment:
                imageView.setFocalPoint(focus);

                Glide.with(imageView.getContext())
                        .load(previewUrl)
                        .placeholder(placeholder)
                        .centerInside()
                        .addListener(imageView)
                        .into(imageView);
            } else {
                imageView.removeFocalPoint();

                Glide.with(imageView)
                        .load(previewUrl)
                        .placeholder(placeholder)
                        .centerInside()
                        .into(imageView);
            }
        }
    }

    protected void setMediaPreviews(
        @NonNull final List<Attachment> attachments,
        boolean sensitive,
        @NonNull final StatusActionListener listener,
        boolean showingContent,
        boolean useBlurhash
    ) {

        mediaPreview.setVisibility(View.VISIBLE);
        mediaPreview.setAspectRatios(AttachmentHelper.aspectRatios(attachments));

        mediaPreview.forEachIndexed((i, imageView, descriptionIndicator) -> {
            Attachment attachment = attachments.get(i);
            String previewUrl = attachment.getPreviewUrl();
            String description = attachment.getDescription();
            boolean hasDescription = !TextUtils.isEmpty(description);

            if (hasDescription) {
                imageView.setContentDescription(description);
            } else {
                imageView.setContentDescription(imageView.getContext().getString(R.string.action_view_media));
            }

            loadImage(
                    imageView,
                    showingContent ? previewUrl : null,
                    attachment.getMeta(),
                    useBlurhash ? attachment.getBlurhash() : null
            );

            final Attachment.Type type = attachment.getType();
            if (showingContent && (type == Attachment.Type.VIDEO || type == Attachment.Type.GIFV)) {
                imageView.setForeground(ContextCompat.getDrawable(itemView.getContext(), R.drawable.play_indicator_overlay));
            } else {
                imageView.setForeground(null);
            }

            setAttachmentClickListener(imageView, listener, i, attachment, true);

            if (sensitive) {
                sensitiveMediaWarning.setText(R.string.post_sensitive_media_title);
            } else {
                sensitiveMediaWarning.setText(R.string.post_media_hidden_title);
            }

            sensitiveMediaWarning.setVisibility(showingContent ? View.GONE : View.VISIBLE);
            sensitiveMediaShow.setVisibility(showingContent ? View.VISIBLE : View.GONE);

            descriptionIndicator.setVisibility(hasDescription && showingContent ? View.VISIBLE : View.GONE);

            sensitiveMediaShow.setOnClickListener(v -> {
                if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onContentHiddenChange(false, getBindingAdapterPosition());
                }
                v.setVisibility(View.GONE);
                sensitiveMediaWarning.setVisibility(View.VISIBLE);
                descriptionIndicator.setVisibility(View.GONE);
            });
            sensitiveMediaWarning.setOnClickListener(v -> {
                if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onContentHiddenChange(true, getBindingAdapterPosition());
                }
                v.setVisibility(View.GONE);
                sensitiveMediaShow.setVisibility(View.VISIBLE);
                descriptionIndicator.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
            });

            return null;
        });
    }

    @DrawableRes
    private static int getLabelIcon(@NonNull Attachment.Type type) {
        return switch (type) {
            case IMAGE -> R.drawable.ic_photo_24dp;
            case GIFV, VIDEO -> R.drawable.ic_videocam_24dp;
            case AUDIO -> R.drawable.ic_music_box_24dp;
            default -> R.drawable.ic_attach_file_24dp;
        };
    }

    private void updateMediaLabel(int index, boolean sensitive, boolean showingContent) {
        Context context = itemView.getContext();
        CharSequence label = (sensitive && !showingContent) ?
                context.getString(R.string.post_sensitive_media_title) :
                mediaDescriptions[index];
        mediaLabels[index].setText(label);
    }

    protected void setMediaLabel(@NonNull List<Attachment> attachments, boolean sensitive,
                                 @NonNull final StatusActionListener listener, boolean showingContent) {
        Context context = itemView.getContext();
        for (int i = 0; i < mediaLabels.length; i++) {
            TextView mediaLabel = mediaLabels[i];
            if (i < attachments.size()) {
                Attachment attachment = attachments.get(i);
                mediaLabel.setVisibility(View.VISIBLE);
                mediaDescriptions[i] = AttachmentHelper.getFormattedDescription(attachment, context);
                updateMediaLabel(i, sensitive, showingContent);

                // Set the icon next to the label.
                int drawableId = getLabelIcon(attachments.get(0).getType());
                mediaLabel.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0);

                setAttachmentClickListener(mediaLabel, listener, i, attachment, false);
            } else {
                mediaLabel.setVisibility(View.GONE);
            }
        }
    }

    private void setAttachmentClickListener(@NonNull View view, @NonNull StatusActionListener listener,
                                            int index, @NonNull Attachment attachment, boolean animateTransition) {
        view.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                if (sensitiveMediaWarning.getVisibility() == View.VISIBLE) {
                    listener.onContentHiddenChange(true, getBindingAdapterPosition());
                } else {
                    listener.onViewMedia(position, index, animateTransition ? v : null);
                }
            }
        });
        view.setOnLongClickListener(v -> {
            CharSequence description = AttachmentHelper.getFormattedDescription(attachment, view.getContext());
            Toast.makeText(view.getContext(), description, Toast.LENGTH_LONG).show();
            return true;
        });
    }

    protected void hideSensitiveMediaWarning() {
        sensitiveMediaWarning.setVisibility(View.GONE);
        sensitiveMediaShow.setVisibility(View.GONE);
    }

    protected void setupButtons(@NonNull final StatusActionListener listener,
                                @NonNull final String accountId,
                                @Nullable final String statusContent,
                                @NonNull StatusDisplayOptions statusDisplayOptions) {
        View.OnClickListener profileButtonClickListener = button -> listener.onViewAccount(accountId);

        avatar.setOnClickListener(profileButtonClickListener);
        displayName.setOnClickListener(profileButtonClickListener);

        replyButton.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onReply(position);
            }
        });


        if (reblogButton != null) {
            reblogButton.setEventListener((button, buttonState) -> {
                // return true to play animation
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    if (statusDisplayOptions.confirmReblogs()) {
                        showConfirmReblog(listener, buttonState, position);
                        return false;
                    } else {
                        listener.onReblog(!buttonState, position);
                        return true;
                    }
                } else {
                    return false;
                }
            });
        }


        favouriteButton.setEventListener((button, buttonState) -> {
            // return true to play animation
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                if (statusDisplayOptions.confirmFavourites()) {
                    showConfirmFavourite(listener, buttonState, position);
                    return false;
                } else {
                    listener.onFavourite(!buttonState, position);
                    return true;
                }
            } else {
                return true;
            }
        });

        bookmarkButton.setEventListener((button, buttonState) -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onBookmark(!buttonState, position);
            }
            return true;
        });

        moreButton.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onMore(v, position);
            }
        });
        /* Even though the content TextView is a child of the container, it won't respond to clicks
         * if it contains URLSpans without also setting its listener. The surrounding spans will
         * just eat the clicks instead of deferring to the parent listener, but WILL respond to a
         * listener directly on the TextView, for whatever reason. */
        View.OnClickListener viewThreadListener = v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewThread(position);
            }
        };
        content.setOnClickListener(viewThreadListener);
        itemView.setOnClickListener(viewThreadListener);
    }

    private void showConfirmReblog(@NonNull StatusActionListener listener,
                                   boolean buttonState,
                                   int position) {
        PopupMenu popup = new PopupMenu(itemView.getContext(), reblogButton);
        popup.inflate(R.menu.status_reblog);
        Menu menu = popup.getMenu();
        if (buttonState) {
            menu.findItem(R.id.menu_action_reblog).setVisible(false);
        } else {
            menu.findItem(R.id.menu_action_unreblog).setVisible(false);
        }
        popup.setOnMenuItemClickListener(item -> {
            listener.onReblog(!buttonState, position);
            if(!buttonState) {
                reblogButton.playAnimation();
            }
            return true;
        });
        popup.show();
    }

    private void showConfirmFavourite(@NonNull StatusActionListener listener,
                                      boolean buttonState,
                                      int position) {
        PopupMenu popup = new PopupMenu(itemView.getContext(), favouriteButton);
        popup.inflate(R.menu.status_favourite);
        Menu menu = popup.getMenu();
        if (buttonState) {
            menu.findItem(R.id.menu_action_favourite).setVisible(false);
        } else {
            menu.findItem(R.id.menu_action_unfavourite).setVisible(false);
        }
        popup.setOnMenuItemClickListener(item -> {
            listener.onFavourite(!buttonState, position);
            if(!buttonState) {
                favouriteButton.playAnimation();
            }
            return true;
        });
        popup.show();
    }

    public void setupWithStatus(@NonNull StatusViewData status, @NonNull final StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions) {
        this.setupWithStatus(status, listener, statusDisplayOptions, null);
    }

    public void setupWithStatus(@NonNull StatusViewData status,
                                @NonNull final StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions,
                                @Nullable Object payloads) {
        if (payloads == null) {
            Status actionable = status.getActionable();
            setDisplayName(actionable.getAccount().getName(), actionable.getAccount().getEmojis(), statusDisplayOptions);
            setUsername(status.getUsername());
            setMetaData(status, statusDisplayOptions, listener);
            setIsReply(actionable.getInReplyToId() != null);
            setReplyCount(actionable.getRepliesCount(), statusDisplayOptions.showStatsInline());
            setAvatar(actionable.getAccount().getAvatar(), status.getRebloggedAvatar(),
                    actionable.getAccount().getBot(), statusDisplayOptions);
            setReblogged(actionable.getReblogged());
            setFavourited(actionable.getFavourited());
            setBookmarked(actionable.getBookmarked());
            List<Attachment> attachments = actionable.getAttachments();
            boolean sensitive = actionable.getSensitive();
            if (statusDisplayOptions.mediaPreviewEnabled() && hasPreviewableAttachment(attachments)) {
                setMediaPreviews(attachments, sensitive, listener, status.isShowingContent(), statusDisplayOptions.useBlurhash());

                if (attachments.size() == 0) {
                    hideSensitiveMediaWarning();
                }
                // Hide the unused label.
                for (TextView mediaLabel : mediaLabels) {
                    mediaLabel.setVisibility(View.GONE);
                }
            } else {
                setMediaLabel(attachments, sensitive, listener, status.isShowingContent());
                // Hide all unused views.
                mediaPreview.setVisibility(View.GONE);
                hideSensitiveMediaWarning();
            }

            setupCard(status, status.isExpanded(), statusDisplayOptions.cardViewMode(), statusDisplayOptions, listener);

            setupButtons(listener, actionable.getAccount().getId(), status.getContent().toString(),
                    statusDisplayOptions);
            setRebloggingEnabled(actionable.rebloggingAllowed(), actionable.getVisibility());

            setSpoilerAndContent(status, statusDisplayOptions, listener);

            setupFilterPlaceholder(status, listener, statusDisplayOptions);

            setDescriptionForStatus(status, statusDisplayOptions);

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            itemView.setAccessibilityDelegate(null);
        } else {
            if (payloads instanceof List)
                for (Object item : (List<?>) payloads) {
                    if (Key.KEY_CREATED.equals(item)) {
                        setMetaData(status, statusDisplayOptions, listener);
                    }
                }

        }
    }

    private void setupFilterPlaceholder(@NonNull StatusViewData status, @NonNull StatusActionListener listener, StatusDisplayOptions displayOptions) {
        if (status.getFilterAction() != Filter.Action.WARN) {
            showFilteredPlaceholder(false);
            return;
        }

        // Shouldn't be necessary given the previous test against getFilterAction(),
        // but guards against a possible NPE. See the TODO in StatusViewData.filterAction
        // for more details.
        List<FilterResult> filterResults = status.getActionable().getFiltered();
        if (filterResults == null || filterResults.isEmpty()) {
            showFilteredPlaceholder(false);
            return;
        }

        Filter matchedFilter = null;

        for (FilterResult result : filterResults) {
            Filter filter = result.getFilter();
            if (filter.getAction() == Filter.Action.WARN) {
                matchedFilter = filter;
                break;
            }
        }

        // Guard against a possible NPE
        if (matchedFilter == null) {
            showFilteredPlaceholder(false);
            return;
        }

        showFilteredPlaceholder(true);

        filteredPlaceholderLabel.setText(itemView.getContext().getString(R.string.status_filter_placeholder_label_format, matchedFilter.getTitle()));
        filteredPlaceholderShowButton.setOnClickListener(view -> listener.clearWarningAction(getBindingAdapterPosition()));
    }

    protected static boolean hasPreviewableAttachment(@NonNull List<Attachment> attachments) {
        for (Attachment attachment : attachments) {
            if (attachment.getType() == Attachment.Type.AUDIO || attachment.getType() == Attachment.Type.UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private void setDescriptionForStatus(@NonNull StatusViewData status,
                                         @NonNull StatusDisplayOptions statusDisplayOptions) {
        Context context = itemView.getContext();
        Status actionable = status.getActionable();

        Poll poll = actionable.getPoll();
        CharSequence pollDescription = "";
        if (poll != null) {
            pollDescription = pollView.getPollDescription(
                PollViewData.Companion.from(poll),
                statusDisplayOptions,
                numberFormat,
                absoluteTimeFormatter
            );
        }

        String description = context.getString(R.string.description_status,
                actionable.getAccount().getDisplayName(),
                getContentWarningDescription(context, status),
                (TextUtils.isEmpty(status.getSpoilerText()) || !actionable.getSensitive() || status.isExpanded() ? status.getContent() : ""),
                getCreatedAtDescription(actionable.getCreatedAt(), statusDisplayOptions),
                actionable.getEditedAt() != null ? context.getString(R.string.description_post_edited) : "",
                getReblogDescription(context, status),
                status.getUsername(),
                actionable.getReblogged() ? context.getString(R.string.description_post_reblogged) : "",
                actionable.getFavourited() ? context.getString(R.string.description_post_favourited) : "",
                actionable.getBookmarked() ? context.getString(R.string.description_post_bookmarked) : "",
                getMediaDescription(context, status),
                getVisibilityDescription(context, actionable.getVisibility()),
                getFavsText(context, actionable.getFavouritesCount()),
                getReblogsText(context, actionable.getReblogsCount()),
                pollDescription
        );
        itemView.setContentDescription(description);
    }

    @NonNull
    private static CharSequence getReblogDescription(@NonNull Context context,
                                                     @NonNull StatusViewData status) {
        Status reblog = status.getRebloggingStatus();
        if (reblog != null) {
            return context
                    .getString(R.string.post_boosted_format, reblog.getAccount().getUsername());
        } else {
            return "";
        }
    }

    @NonNull
    private static CharSequence getMediaDescription(@NonNull Context context,
                                                    @NonNull StatusViewData status) {
        if (status.getActionable().getAttachments().isEmpty()) {
            return "";
        }
        StringBuilder mediaDescriptions = CollectionsKt.fold(
                status.getActionable().getAttachments(),
                new StringBuilder(),
                (builder, a) -> {
                    if (a.getDescription() == null) {
                        String placeholder =
                                context.getString(R.string.description_post_media_no_description_placeholder);
                        return builder.append(placeholder);
                    } else {
                        builder.append("; ");
                        return builder.append(a.getDescription());
                    }
                });
        return context.getString(R.string.description_post_media, mediaDescriptions);
    }

    @NonNull
    private static CharSequence getContentWarningDescription(@NonNull Context context,
                                                             @NonNull StatusViewData status) {
        if (!TextUtils.isEmpty(status.getSpoilerText())) {
            return context.getString(R.string.description_post_cw, status.getSpoilerText());
        } else {
            return "";
        }
    }

    @NonNull
    protected static CharSequence getVisibilityDescription(@NonNull Context context, @Nullable Status.Visibility visibility) {

        if (visibility == null) {
            return "";
        }

        int resource;
        switch (visibility) {
            case PUBLIC -> resource = R.string.description_visibility_public;
            case UNLISTED -> resource = R.string.description_visibility_unlisted;
            case PRIVATE -> resource = R.string.description_visibility_private;
            case DIRECT -> resource = R.string.description_visibility_direct;
            default -> {
                return "";
            }
        }
        return context.getString(resource);
    }

    @NonNull
    protected CharSequence getFavsText(@NonNull Context context, int count) {
        if (count > 0) {
            String countString = numberFormat.format(count);
            return HtmlCompat.fromHtml(context.getResources().getQuantityString(R.plurals.favs, count, countString), HtmlCompat.FROM_HTML_MODE_LEGACY);
        } else {
            return "";
        }
    }

    @NonNull
    protected CharSequence getReblogsText(@NonNull Context context, int count) {
        if (count > 0) {
            String countString = numberFormat.format(count);
            return HtmlCompat.fromHtml(context.getResources().getQuantityString(R.plurals.reblogs, count, countString), HtmlCompat.FROM_HTML_MODE_LEGACY);
        } else {
            return "";
        }
    }

    protected void setupCard(
        @NonNull final StatusViewData status,
        boolean expanded,
        @NonNull final CardViewMode cardViewMode,
        @NonNull final StatusDisplayOptions statusDisplayOptions,
        @NonNull final StatusActionListener listener
    ) {
        if (cardView == null) return;

        final Status actionable = status.getActionable();
        final Card card = actionable.getCard();

        if (cardViewMode != CardViewMode.NONE &&
            actionable.getAttachments().size() == 0 &&
            actionable.getPoll() == null &&
            card != null &&
            !TextUtils.isEmpty(card.getUrl()) &&
            (!actionable.getSensitive() || expanded) &&
            (!status.isCollapsible() || !status.isCollapsed())) {

            cardView.setVisibility(View.VISIBLE);
            PreviewCardView.OnClickListener cardListener = (PreviewCardView.Target target) -> {
                if (card.getKind().equals(PreviewCardKind.PHOTO) && !TextUtils.isEmpty(card.getEmbedUrl())) {
                    cardView.getContext().startActivity(ViewMediaActivity.newSingleImageIntent(cardView.getContext(), card.getEmbedUrl()));
                } else {
                    listener.onViewUrl(card.getUrl());
                }
            };

            cardView.bind(card, statusDisplayOptions, cardListener);
        } else {
            cardView.setVisibility(View.GONE);
        }
    }

    public void showStatusContent(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        avatar.setVisibility(visibility);
        avatarInset.setVisibility(visibility);
        displayName.setVisibility(visibility);
        username.setVisibility(visibility);
        metaInfo.setVisibility(visibility);
        contentWarningDescription.setVisibility(visibility);
        contentWarningButton.setVisibility(visibility);
        content.setVisibility(visibility);
        cardView.setVisibility(visibility);
        mediaContainer.setVisibility(visibility);
        pollView.setVisibility(visibility);
        replyButton.setVisibility(visibility);
        reblogButton.setVisibility(visibility);
        favouriteButton.setVisibility(visibility);
        bookmarkButton.setVisibility(visibility);
        moreButton.setVisibility(visibility);
    }

    public void showFilteredPlaceholder(boolean show) {
        if (statusContainer != null) {
            statusContainer.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (filteredPlaceholder != null) {
            filteredPlaceholder.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
