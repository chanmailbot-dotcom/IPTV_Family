package com.iptv.family.state

import com.iptv.family.shared.state.LibraryState

/**
 * El estado de la aplicacion es el mismo que el del escritorio y vive en
 * `shared`: ver [LibraryState].
 *
 * Aqui queda solo el nombre con el que lo conoce esta aplicacion. Antes esto era
 * una copia entera del fichero de escritorio, veinte funciones incluidas, y cada
 * arreglo habia que hacerlo dos veces.
 */
typealias AppState = LibraryState
