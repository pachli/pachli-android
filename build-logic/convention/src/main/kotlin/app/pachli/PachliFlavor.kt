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
    color,
    store
}

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
        appName = "Pachli Current",
    ),
    fdroid(FlavorDimension.store),
    github(FlavorDimension.store),
    google(FlavorDimension.store)
}

fun configureFlavors(
    commonExtension: CommonExtension<*, *, *, *, *>,
    flavorConfigurationBlock: ProductFlavor.(flavor: PachliFlavor) -> Unit = {}
) {
    commonExtension.apply {
        flavorDimensions += FlavorDimension.color.name
        flavorDimensions += FlavorDimension.store.name
        productFlavors {
            PachliFlavor.values().forEach { flavor ->
                create(flavor.name) {
                    dimension = flavor.dimension.name
                    flavorConfigurationBlock(this, flavor)
                    if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                        flavor.applicationIdSuffix?.let {
                            applicationIdSuffix = it
                        }
                        flavor.versionNameSuffix?.let {
                            versionNameSuffix = it
                        }
                    }
                    resValue("string", "app_name", flavor.appName)
                    buildConfigField("String", "CUSTOM_LOGO_URL", "\"${flavor.customLogoUrl}\"")
                    buildConfigField("String", "CUSTOM_INSTANCE", "\"${flavor.customInstance}\"")
                    buildConfigField("String", "SUPPORT_ACCOUNT_URL", "\"${flavor.supportAccountUrl}\"")
                }
            }
        }
    }
}
