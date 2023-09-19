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

package app.pachli.interfaces;

import androidx.annotation.NonNull;

public interface AccountActionListener {
    void onViewAccount(@NonNull String id);
    void onMute(final boolean mute, @NonNull final String id, final int position, final boolean notifications);
    void onBlock(final boolean block, @NonNull final String id, final int position);
    void onRespondToFollowRequest(final boolean accept, @NonNull final String id, final int position);
}
