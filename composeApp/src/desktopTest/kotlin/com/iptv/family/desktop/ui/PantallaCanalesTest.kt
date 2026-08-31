package com.iptv.family.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.iptv.family.desktop.state.newInMemoryAppState
import com.iptv.family.desktop.ui.screens.ChannelsScreen
import com.iptv.family.shared.i18n.Textos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Rule

/**
 * Pruebas de la pantalla de canales del escritorio.
 *
 * La que importa es la del ancho: el arreglo de la ventana estrecha se comprobo
 * a mano, con capturas, y a mano no se vuelve a comprobar nunca. Antes de ese
 * arreglo, a 700 px los nombres de canal quedaban en «C» y a 560 el texto
 * «Buscar canal…» se escribia en vertical, una letra por linea. Esto lo fija.
 */
class PantallaCanalesTest {

    @get:Rule
    val compose = createComposeRule()

    private val m3u = """
        #EXTM3U
        #EXTINF:-1 group-title="Deportes",Canal de deportes
        http://ejemplo/1.ts
        #EXTINF:-1 group-title="Noticias",Canal de noticias
        http://ejemplo/2.ts
    """.trimIndent()

    @BeforeTest
    fun idiomaFijo() {
        // Las pruebas comparan textos: si el idioma dependiera del sistema donde
        // corren, pasarian aqui y fallarian en el CI (o al reves).
        Textos.usar("es")
    }

    @AfterTest
    fun restaurar() = Textos.usar("es")

    /** Estado con canales de verdad, sin red: el M3U se lee de un almacen en memoria. */
    private fun estadoConCanales() = newInMemoryAppState().also { estado ->
        runBlocking { estado.addM3uFile("Pruebas", m3u) }
    }

    @Test
    fun `en una ventana ancha las categorias son una columna`() {
        val estado = estadoConCanales()

        compose.setContent {
            Box(Modifier.size(width = 1400.dp, height = 900.dp)) {
                ChannelsScreen(
                    appState = estado,
                    scope = CoroutineScope(Dispatchers.Main),
                    onPlay = { _, _ -> },
                    onGoHome = {},
                )
            }
        }

        compose.onNodeWithText("Categorías").assertIsDisplayed()
        compose.onNodeWithText("Canal de deportes").assertIsDisplayed()
    }

    @Test
    fun `en una ventana estrecha las categorias pasan a un desplegable`() {
        val estado = estadoConCanales()

        compose.setContent {
            Box(Modifier.size(width = 560.dp, height = 800.dp)) {
                ChannelsScreen(
                    appState = estado,
                    scope = CoroutineScope(Dispatchers.Main),
                    onPlay = { _, _ -> },
                    onGoHome = {},
                )
            }
        }

        // La columna desaparece y aparece el desplegable...
        compose.onNodeWithText("Categorías").assertDoesNotExist()
        compose.onNodeWithContentDescription("Elegir categoría").assertIsDisplayed()
        // ...pero los canales se siguen viendo, que es de lo que iba todo esto:
        // antes, a este ancho, no cabia ni un nombre.
        compose.onNodeWithText("Canal de deportes").assertIsDisplayed()
    }

    @Test
    fun `el buscador y los filtros de tipo estan en los dos anchos`() {
        val estado = estadoConCanales()

        compose.setContent {
            Box(Modifier.size(width = 560.dp, height = 800.dp)) {
                ChannelsScreen(
                    appState = estado,
                    scope = CoroutineScope(Dispatchers.Main),
                    onPlay = { _, _ -> },
                    onGoHome = {},
                )
            }
        }

        compose.onNodeWithText("Buscar canal…").assertIsDisplayed()
        compose.onNodeWithText("Películas").assertIsDisplayed()
        compose.onNodeWithText("Series").assertIsDisplayed()
    }
}
