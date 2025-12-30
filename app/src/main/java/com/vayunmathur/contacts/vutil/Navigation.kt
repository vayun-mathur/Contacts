package com.vayunmathur.contacts.vutil

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.collections.forEachIndexed
import kotlin.collections.lastIndex
import kotlin.collections.toTypedArray
import kotlin.to


// The Registry that holds the events
class NavResultRegistry {
    private val _results = Channel<Pair<String, Any>>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    fun dispatchResult(key: String, result: Any) {
        _results.trySend(key to result)
    }
}

// The Composable helper (The "ResultEffect" you saw)
@Composable
inline fun <reified T> ResultEffect(key: String, crossinline onResult: (T) -> Unit) {
    val registry = LocalNavResultRegistry.current
    LaunchedEffect(registry) {
        registry.results.collect { (k, result) ->
            if (k == key && result is T) {
                onResult(result)
            }
        }
    }
}

// Make it available everywhere via CompositionLocal
val LocalNavResultRegistry = staticCompositionLocalOf<NavResultRegistry> {
    error("No NavResultRegistry provided")
}

fun <T: NavKey> NavBackStack<T>.pop() {
    removeAt(lastIndex)
}

fun <T: NavKey> NavBackStack<T>.reset(vararg keys: T) {
    // set values
    clear()
    while(size > keys.size) {
        pop()
    }
    keys.forEachIndexed { idx, key ->
        if(size <= idx) {
            add(key)
        } else
            set(idx, key)
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T: NavKey> MainNavigation(backStack: NavBackStack<T>, entryProvider:  EntryProviderScope<T>.() -> Unit) {
    val resultRegistry = remember { NavResultRegistry() }
    val listDetailSceneStrategy = rememberListDetailSceneStrategy<T>()
    Scaffold(contentWindowInsets = WindowInsets.displayCutout
    ) { paddingValues ->
        CompositionLocalProvider(LocalNavResultRegistry provides resultRegistry) {
            NavDisplay(
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
                sceneStrategy = DialogSceneStrategy<T>().then(listDetailSceneStrategy),
                backStack = backStack, entryProvider = entryProvider {
                    entryProvider()
                })
        }
    }
}

@Composable
fun <T: NavKey> rememberNavBackStack(vararg elements: T): NavBackStack<T> {
    return rememberSerializable(
        serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer())
    ) {
        NavBackStack(*elements)
    }
}

@Composable
inline fun <reified T: NavKey> rememberNavBackStack(elements: List<T>): NavBackStack<T> {
    return rememberNavBackStack(*elements.toTypedArray())
}