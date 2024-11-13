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

package app.pachli.core.network.di.test

import app.pachli.core.network.di.MastodonApiModule
import app.pachli.core.network.model.Configuration
import app.pachli.core.network.model.Contact
import app.pachli.core.network.model.InstanceV2
import app.pachli.core.network.model.InstanceV2Polls
import app.pachli.core.network.model.InstanceV2Statuses
import app.pachli.core.network.model.MediaAttachments
import app.pachli.core.network.model.Registrations
import app.pachli.core.network.model.Thumbnail
import app.pachli.core.network.model.Usage
import app.pachli.core.network.model.Users
import app.pachli.core.network.retrofit.MastodonApi
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.mock
import org.mockito.stubbing.Answer

/**
 * Provides an empty mock. Use like:
 *
 * ```kotlin
 * @Inject
 * lateinit var mastodonApi: MastodonApi
 *
 * // ...
 *
 * @Before
 * fun setup() {
 *     hilt.inject()
 *
 *     reset(mastodonApi)
 *     mastodonApi.stub {
 *         onBlocking { someFunction() } doReturn SomeValue
 *         // ...
 *     }
 *
 *     // ...
 * }
 * ```
 */
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [MastodonApiModule::class],
)
@Module
object FakeMastodonApiModule {
    @Provides
    @Singleton
    fun providesApi(): MastodonApi = mock(defaultAnswer = ThrowingAnswer)
}

/**
 * Mockito [Answer] that throws if the method is called.
 *
 * The exception message includes information about the method that was called,
 * the call site, and tries to show the specific entry in the call stack for the
 * call.
 *
 * Attach this as the default answer to any mocks where you expect all relevant
 * methods to be stubbed during the test. Any methods that are not stubbed will
 * throw an [AssertionError].
 */
object ThrowingAnswer : Answer<Any> {
    override fun answer(invocation: InvocationOnMock): Any {
        // The method is called as part of the stubbing process. E.g., if you have code
        // like:
        //
        // mastodonApi.stub {
        //   onBlocking { someFunction() } doReturn success(emptyList())
        // }
        //
        // then `someFunction()` will be called.
        //
        // That has to be handled specially here, otherwise the exception is thrown
        // as part of the stubbing process.
        //
        // To determine whether this call is during the stubbing process get the
        // current call stack and look for a call to org.mockito.kotlin.KStubbing.onBlocking.
        // If that's somewhere in the call stack then this is a call during the stubbing
        // process and Unit should be returned.
        val callstack = Thread.currentThread().getStackTrace()
        val isDuringStubbing = callstack.firstOrNull {
            it.className == "org.mockito.kotlin.KStubbing" && it.methodName == "onBlocking"
        } != null
        if (isDuringStubbing) return Unit

        val methodName = invocation.method.name
        val className = invocation.method.declaringClass.getSimpleName()
        val stackIndexOfCall = callstack.indexOfFirst { it.methodName == methodName }

        val message = buildString {
            append("$className.$methodName was not stubbed, but was called.\n\nCall looks like:\n\n$invocation")

            if (stackIndexOfCall != -1) {
                append("\n\nProbable call site: ")
                append(callstack[stackIndexOfCall + 1])
            }
        }

        throw AssertionError(message)
    }
}

/**
 * An [InstanceV2] tests can use as the return value from [MastodonApi.getInstanceV2].
 */
val DEFAULT_INSTANCE_V2 = InstanceV2(
    domain = "domain.example",
    title = "Test server",
    version = "4.3.0",
    description = "Test description",
    usage = Usage(users = Users()),
    thumbnail = Thumbnail(
        url = "https://example.com/thumbnail",
        blurhash = null,
        versions = null,
    ),
    languages = emptyList(),
    configuration = Configuration(
        statuses = InstanceV2Statuses(),
        mediaAttachments = MediaAttachments(),
        polls = InstanceV2Polls(),
    ),
    registrations = Registrations(
        enabled = false,
        approvalRequired = false,
        message = null,
    ),
    contact = Contact(),
)
