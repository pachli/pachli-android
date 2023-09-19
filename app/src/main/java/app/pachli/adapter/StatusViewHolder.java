/* Copyright 2017 Andrew Dawson
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
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package app.pachli.adapter;

import android.content.Context;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import app.pachli.R;
import app.pachli.entity.Emoji;
import app.pachli.entity.Filter;
import app.pachli.entity.Status;
import app.pachli.interfaces.StatusActionListener;
import app.pachli.util.CustomEmojiHelper;
import app.pachli.util.NumberUtils;
import app.pachli.util.SmartLengthInputFilter;
import app.pachli.util.StatusDisplayOptions;
import app.pachli.util.StringUtils;
import app.pachli.viewdata.StatusViewData;
import at.connyduck.sparkbutton.helpers.Utils;

public class StatusViewHolder extends StatusBaseViewHolder {
    private static final InputFilter[] COLLAPSE_INPUT_FILTER = new InputFilter[]{SmartLengthInputFilter.INSTANCE};
    private static final InputFilter[] NO_INPUT_FILTER = new InputFilter[0];

    private final TextView statusInfo;
    private final Button contentCollapseButton;
    private final TextView favouritedCountLabel;
    private final TextView reblogsCountLabel;

    public StatusViewHolder(@NonNull View itemView) {
        super(itemView);
        statusInfo = itemView.findViewById(R.id.status_info);
        contentCollapseButton = itemView.findViewById(R.id.button_toggle_content);
        favouritedCountLabel = itemView.findViewById(R.id.status_favourites_count);
        reblogsCountLabel = itemView.findViewById(R.id.status_insets);
    }

    @Override
    public void setupWithStatus(@NonNull StatusViewData status,
                                @NonNull final StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions,
                                @Nullable Object payloads) {
        if (payloads == null) {

            boolean sensitive = !TextUtils.isEmpty(status.getActionable().getSpoilerText());
            boolean expanded = status.isExpanded();

            setupCollapsedState(sensitive, expanded, status, listener);

            Status reblogging = status.getRebloggingStatus();
            if (reblogging == null || status.getFilterAction() == Filter.Action.WARN) {
                hideStatusInfo();
            } else {
                String rebloggedByDisplayName = reblogging.getAccount().getName();
                setRebloggedByDisplayName(rebloggedByDisplayName,
                        reblogging.getAccount().getEmojis(), statusDisplayOptions);
                statusInfo.setOnClickListener(v -> listener.onOpenReblog(getBindingAdapterPosition()));
            }

        }

        reblogsCountLabel.setVisibility(statusDisplayOptions.showStatsInline() ? View.VISIBLE : View.INVISIBLE);
        favouritedCountLabel.setVisibility(statusDisplayOptions.showStatsInline() ? View.VISIBLE : View.INVISIBLE);
        setFavouritedCount(status.getActionable().getFavouritesCount());
        setReblogsCount(status.getActionable().getReblogsCount());

        super.setupWithStatus(status, listener, statusDisplayOptions, payloads);
    }

    private void setRebloggedByDisplayName(@NonNull final CharSequence name,
                                           final List<Emoji> accountEmoji,
                                           @NonNull final StatusDisplayOptions statusDisplayOptions) {
        Context context = statusInfo.getContext();
        CharSequence wrappedName = StringUtils.unicodeWrap(name);
        CharSequence boostedText = context.getString(R.string.post_boosted_format, wrappedName);
        CharSequence emojifiedText = CustomEmojiHelper.emojify(
                boostedText, accountEmoji, statusInfo, statusDisplayOptions.animateEmojis()
        );
        statusInfo.setText(emojifiedText);
        statusInfo.setVisibility(View.VISIBLE);
    }

    // don't use this on the same ViewHolder as setRebloggedByDisplayName, will cause recycling issues as paddings are changed
    protected void setPollInfo(final boolean ownPoll) {
        statusInfo.setText(ownPoll ? R.string.poll_ended_created : R.string.poll_ended_voted);
        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0);
        statusInfo.setCompoundDrawablePadding(Utils.dpToPx(statusInfo.getContext(), 10));
        statusInfo.setPaddingRelative(Utils.dpToPx(statusInfo.getContext(), 28), 0, 0, 0);
        statusInfo.setVisibility(View.VISIBLE);
    }

    protected void setReblogsCount(int reblogsCount) {
        reblogsCountLabel.setText(NumberUtils.formatNumber(reblogsCount, 1000));
    }

    protected void setFavouritedCount(int favouritedCount) {
        favouritedCountLabel.setText(NumberUtils.formatNumber(favouritedCount, 1000));
    }

    protected void hideStatusInfo() {
        statusInfo.setVisibility(View.GONE);
    }

    private void setupCollapsedState(boolean sensitive,
                                     boolean expanded,
                                     @NonNull final StatusViewData status,
                                     @NonNull final StatusActionListener listener) {
        /* input filter for TextViews have to be set before text */
        if (status.isCollapsible() && (!sensitive || expanded)) {
            contentCollapseButton.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION)
                    listener.onContentCollapsedChange(!status.isCollapsed(), position);
            });

            contentCollapseButton.setVisibility(View.VISIBLE);
            if (status.isCollapsed()) {
                contentCollapseButton.setText(R.string.post_content_warning_show_more);
                content.setFilters(COLLAPSE_INPUT_FILTER);
            } else {
                contentCollapseButton.setText(R.string.post_content_warning_show_less);
                content.setFilters(NO_INPUT_FILTER);
            }
        } else {
            contentCollapseButton.setVisibility(View.GONE);
            content.setFilters(NO_INPUT_FILTER);
        }
    }

    public void showStatusContent(boolean show) {
        super.showStatusContent(show);
        contentCollapseButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void toggleExpandedState(boolean sensitive,
                                       boolean expanded,
                                       @NonNull StatusViewData status,
                                       @NonNull StatusDisplayOptions statusDisplayOptions,
                                       @NonNull final StatusActionListener listener) {

        setupCollapsedState(sensitive, expanded, status, listener);

        super.toggleExpandedState(sensitive, expanded, status, statusDisplayOptions, listener);
    }
}
