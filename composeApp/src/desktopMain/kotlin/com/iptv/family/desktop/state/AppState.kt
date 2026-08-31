package com.iptv.family.desktop.state

import com.iptv.family.shared.data.repository.LibraryRepository
import com.iptv.family.shared.data.store.KeyValueStore
import com.iptv.family.shared.state.LibraryState

/**
 * El estado de la aplicacion es el mismo que el de Android y vive en `shared`:
 * ver [LibraryState]. Aqui queda solo el nombre con el que lo conoce esta
 * aplicacion.
 */
typealias AppState = LibraryState

/** Estado sobre un almacen en memoria, para vistas previas y pruebas. */
fun newInMemoryAppState(): AppState {
    val store = object : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun write(key: String, value: String) { map[key] = value }
        override fun read(key: String): String? = map[key]
        override fun delete(key: String) { map.remove(key) }
    }
    return LibraryState(LibraryRepository(store))
}
