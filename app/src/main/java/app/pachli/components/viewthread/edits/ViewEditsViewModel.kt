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

package app.pachli.components.viewthread.edits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pachli.components.viewthread.edits.PachliTagHandler.Companion.DELETED_TEXT_EL
import app.pachli.components.viewthread.edits.PachliTagHandler.Companion.INSERTED_TEXT_EL
import app.pachli.core.network.model.StatusEdit
import app.pachli.core.network.retrofit.MastodonApi
import com.github.michaelbull.result.getOrElse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.pageseeder.diffx.api.LoadingException
import org.pageseeder.diffx.api.Operator
import org.pageseeder.diffx.config.DiffConfig
import org.pageseeder.diffx.config.TextGranularity
import org.pageseeder.diffx.config.WhiteSpaceProcessing
import org.pageseeder.diffx.core.OptimisticXMLProcessor
import org.pageseeder.diffx.format.XMLDiffOutput
import org.pageseeder.diffx.load.SAXLoader
import org.pageseeder.diffx.token.XMLToken
import org.pageseeder.diffx.token.XMLTokenType
import org.pageseeder.diffx.token.impl.SpaceToken
import org.pageseeder.diffx.xml.NamespaceSet
import org.pageseeder.xmlwriter.XML.NamespaceAware
import org.pageseeder.xmlwriter.XMLStringWriter

@HiltViewModel
class ViewEditsViewModel @Inject constructor(private val api: MastodonApi) : ViewModel() {

    private val _uiState: MutableStateFlow<EditsUiState> = MutableStateFlow(EditsUiState.Initial)
    val uiState: StateFlow<EditsUiState> = _uiState.asStateFlow()

    /** The API call to fetch edit history returned less than two items */
    object MissingEditsException : Exception()

    fun loadEdits(statusId: String, force: Boolean = false, refreshing: Boolean = false) {
        if (!force && _uiState.value !is EditsUiState.Initial) return

        if (refreshing) {
            _uiState.value = EditsUiState.Refreshing
        } else {
            _uiState.value = EditsUiState.Loading
        }

        viewModelScope.launch {
            val edits = api.statusEdits(statusId).getOrElse {
                _uiState.value = EditsUiState.Error(it.throwable)
                return@launch
            }.body

            // `edits` might have fewer than the minimum number of entries because of
            // https://github.com/mastodon/mastodon/issues/25398.
            if (edits.size < 2) {
                _uiState.value = EditsUiState.Error(MissingEditsException)
                return@launch
            }

            // Diff each status' content against the previous version, producing new
            // content with additional `ins` or `del` elements marking inserted or
            // deleted content.
            //
            // This can be CPU intensive depending on the number of edits and the size
            // of each, so don't run this on Dispatchers.Main.
            viewModelScope.launch(Dispatchers.Default) {
                val sortedEdits = edits
                    // The XMLLoader expects the outermost content to be enclosed
                    // in block elements. Multiple block elements without a single
                    // root are ok ("<p>...</p> <p>...</p>"), but content without
                    // a block element ("<a ...> some text") is not. Guard against
                    // this be wrapping everything in a div.
                    // https://github.com/tuskyapp/Tusky/issues/4253
                    .map { it.copy(content = "<div>${it.content}</div>") }
                    .sortedBy { it.createdAt }
                    .reversed()
                    .toMutableList()

                SAXLoader.setXMLReaderClass("org.xmlpull.v1.sax2.Driver")
                val loader = SAXLoader()
                loader.config = DiffConfig(
                    false,
                    WhiteSpaceProcessing.PRESERVE,
                    TextGranularity.SPACE_WORD,
                )
                val processor = OptimisticXMLProcessor()
                processor.setCoalesce(true)
                val spoilerDiff = HtmlDiffOutput()
                val contentDiff = HtmlDiffOutput()

                try {
                    var currentSpoilerText = loader.load("<div>${sortedEdits[0].spoilerText}</div>")
                    var previousSpoilerText = loader.load("<div>${sortedEdits[1].spoilerText}</div>")

                    // The XML processor expects `br` to be closed
                    var currentContent =
                        loader.load(sortedEdits[0].content.replace("<br>", "<br/>"))
                    var previousContent =
                        loader.load(sortedEdits[1].content.replace("<br>", "<br/>"))

                    for (i in 1 until sortedEdits.size) {
                        processor.diff(previousSpoilerText, currentSpoilerText, spoilerDiff)
                        processor.diff(previousContent, currentContent, contentDiff)
                        sortedEdits[i - 1] = sortedEdits[i - 1].copy(
                            spoilerText = spoilerDiff.xml.toString().removePrefix("<div/>"),
                            content = contentDiff.xml.toString(),
                        )

                        if (i < sortedEdits.size - 1) {
                            currentSpoilerText = previousSpoilerText
                            previousSpoilerText = loader.load(sortedEdits[i + 1].spoilerText)
                            currentContent = previousContent
                            previousContent = loader.load(
                                sortedEdits[i + 1].content.replace("<br>", "<br/>"),
                            )
                        }
                    }
                    _uiState.value = EditsUiState.Success(sortedEdits)
                } catch (_: LoadingException) {
                    // Something failed parsing the XML from the server. Rather than
                    // show an error just return the sorted edits so the user can at
                    // least visually scan the differences.
                    _uiState.value = EditsUiState.Success(sortedEdits)
                }
            }
        }
    }
}

sealed interface EditsUiState {
    data object Initial : EditsUiState
    data object Loading : EditsUiState

    // "Refreshing" state is necessary, otherwise a refresh state transition is Success -> Success,
    // and state flows don't emit repeated states, so the UI never updates.
    data object Refreshing : EditsUiState
    class Error(val throwable: Throwable) : EditsUiState
    data class Success(
        val edits: List<StatusEdit>,
    ) : EditsUiState
}

/**
 * Add elements wrapping inserted or deleted content.
 */
class HtmlDiffOutput : XMLDiffOutput {
    /** XML Output */
    lateinit var xml: XMLStringWriter
        private set

    override fun start() {
        xml = XMLStringWriter(NamespaceAware.Yes)
    }

    override fun handle(operator: Operator, token: XMLToken) {
        if (operator.isEdit) {
            handleEdit(operator, token)
        } else {
            token.toXML(xml)
        }
    }

    override fun end() {
        xml.flush()
    }

    override fun setWriteXMLDeclaration(show: Boolean) {
        // This space intentionally left blank
    }

    override fun setNamespaces(namespaces: NamespaceSet?) {
        // This space intentionally left blank
    }

    private fun handleEdit(operator: Operator, token: XMLToken) {
        if (token == SpaceToken.NEW_LINE) {
            if (operator == Operator.INS) {
                token.toXML(xml)
            }
            return
        }
        when (token.type) {
            XMLTokenType.START_ELEMENT -> token.toXML(xml)
            XMLTokenType.END_ELEMENT -> token.toXML(xml)
            XMLTokenType.TEXT -> {
                // wrap the characters in a <pachli-ins/pachli-del> element
                when (operator) {
                    Operator.DEL -> DELETED_TEXT_EL
                    Operator.INS -> INSERTED_TEXT_EL
                    else -> null
                }?.let {
                    xml.openElement(it, false)
                }
                token.toXML(xml)
                xml.closeElement()
            }
            else -> {
                // Only include inserted content
                if (operator === Operator.INS) {
                    token.toXML(xml)
                }
            }
        }
    }
}
