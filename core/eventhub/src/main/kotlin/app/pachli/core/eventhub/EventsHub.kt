package app.pachli.core.eventhub

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

interface Event

@Singleton
class EventHub @Inject constructor() {

    private val sharedEventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val events: Flow<Event> = sharedEventFlow

    suspend fun dispatch(event: Event) {
        sharedEventFlow.emit(event)
    }
}
