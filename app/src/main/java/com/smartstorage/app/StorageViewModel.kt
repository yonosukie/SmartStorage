package com.smartstorage.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartstorage.core.Inventory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class StorageViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as StorageApplication).repository
    val state = repository.state
    val busy = MutableStateFlow(false)
    val failure = MutableStateFlow<String?>(null)
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    init { reload() }
    fun reload() { viewModelScope.launch { try { repository.load(); failure.value = null } catch (e: Exception) { failure.value = e.message ?: "数据读取失败" } } }
    fun notify(message: String) { viewModelScope.launch { events.send(message) } }
    fun work(success: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { block(); success?.invoke() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { events.send(e.message ?: "操作失败，请重试") }
            finally { busy.value = false }
        }
    }
    fun update(success: (() -> Unit)? = null, change: (Inventory) -> Inventory) = work(success) { repository.update(change) }
}
