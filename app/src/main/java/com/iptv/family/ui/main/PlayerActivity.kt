package com.iptv.family.ui.main

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.iptv.family.R
import com.iptv.family.domain.model.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerActivity : androidx.appcompat.app.AppCompatActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var currentChannel: Channel? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        val channel = intent.getParcelableExtra<Channel>("channel")
        currentChannel = channel

        if (channel != null) {
            initPlayer(channel)
        }

        setupControls()
    }

    private fun initPlayer(channel: Channel) {
        val playerBuilder = com.iptv.family.player.IPTVPlayer.create(this)
        exoPlayer = playerBuilder.player
        playerView?.player = exoPlayer

        scope.launch {
            playerBuilder.prepare(channel)
            runOnUiThread {
                playerBuilder.play()
            }
        }
    }

    private fun setupControls() {
        // Lock button, quality selector, etc.
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
    }
}