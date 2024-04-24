/*
 * Copyright 2024 Pachli Association
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

package app.pachli.core.network.util

import android.content.Context
import app.pachli.core.network.R
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import okhttp3.tls.HandshakeCertificates

// Devices running Android 7 (API 23) do not trust the Let's Encrypt certificate and
// will refuse to connect. These functions provide certificates and a trust manager
// that contain the Let's Encrypt certificates and are used when configuring OkHttp
// and handling LoginWebViewActivity SSL errors.
//
// See https://github.com/pachli/pachli-android/issues/638#issuecomment-2071935438
// for the background.

/**
 * @return [HandshakeCertificates] containing the platform's trusted certificates and
 *     the extra certificates in `values/raw`.
 */
fun localHandshakeCertificates(context: Context): HandshakeCertificates {
    val certFactory = CertificateFactory.getInstance("X.509")
    return HandshakeCertificates.Builder()
        .addPlatformTrustedCertificates()
        .addTrustedCertificate(certFactory.generateCertificate(context.resources.openRawResource(R.raw.isrg_root_x1_cross_signed)) as X509Certificate)
        .addTrustedCertificate(certFactory.generateCertificate(context.resources.openRawResource(R.raw.isrg_root_x2)) as X509Certificate)
        .addTrustedCertificate(certFactory.generateCertificate(context.resources.openRawResource(R.raw.isrg_root_x2_cross_signed)) as X509Certificate)
        .addTrustedCertificate(certFactory.generateCertificate(context.resources.openRawResource(R.raw.isrgrootx1)) as X509Certificate)
        .build()
}

/**
 * @return An [X509TrustManager] configured with certificates loaded from
 *     localCertHandshakeCertificates].
 */
// Exists so that LoginWebViewActivity does not depend on HandshakeCertificates
// (on okHttp type), but X509TrustManager, a javax type.
fun localTrustManager(context: Context): X509TrustManager = localHandshakeCertificates(context).trustManager
