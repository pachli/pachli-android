/*
 * Copyright 2023 Pachli Association
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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor

@Suppress("EnumEntryName")
enum class FlavorDimension {
    color
}

// The content for the app can either come from local static data which is useful for demo
// purposes, or from a production backend server which supplies up-to-date, real content.
// These two product flavors reflect this behaviour.
@Suppress("EnumEntryName")
enum class PachliFlavor(
    val dimension: FlavorDimension,
    val applicationIdSuffix: String? = null,
    val versionNameSuffix: String? = null,
    val appName: String = "Pachli",
    val customLogoUrl: String = "",
    val customInstance: String = "",
    val supportAccountUrl: String = "https://mastodon.social/@Pachli"
) {
    blue(FlavorDimension.color),
    orange(
        FlavorDimension.color,
        applicationIdSuffix = ".current",
        //versionNameSuffix = "+${gitSha}"
    )
}

fun configureFlavors(
    commonExtension: CommonExtension<*, *, *, *, *>,
    flavorConfigurationBlock: ProductFlavor.(flavor: PachliFlavor) -> Unit = {}
) {
    commonExtension.apply {
        flavorDimensions += FlavorDimension.color.name
        productFlavors {
            PachliFlavor.values().forEach {
                create(it.name) {
                    dimension = it.dimension.name
                    flavorConfigurationBlock(this, it)
                    if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                        it.applicationIdSuffix?.let {
                            applicationIdSuffix = it
                        }
                        it.versionNameSuffix?.let {
                            versionNameSuffix = it
                        }
                    }
                    resValue("string", "app_name", it.appName)
                    buildConfigField("String", "CUSTOM_LOGO_URL", "\"${it.customLogoUrl}\"")
                    buildConfigField("String", "CUSTOM_INSTANCE", "\"${it.customInstance}\"")
                    buildConfigField("String", "SUPPORT_ACCOUNT_URL", "\"${it.supportAccountUrl}\"")
                }
            }
        }
    }
}
