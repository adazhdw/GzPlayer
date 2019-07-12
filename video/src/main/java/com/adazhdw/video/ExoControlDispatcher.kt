package com.adazhdw.video

import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.Player

class ExoControlDispatcher :ControlDispatcher {

    override fun dispatchSetPlayWhenReady(player: Player, playWhenReady: Boolean): Boolean {
        player.playWhenReady = playWhenReady
        return true
    }

    override fun dispatchSeekTo(player: Player, windowIndex: Int, positionMs: Long): Boolean {
        player.seekTo(windowIndex, positionMs)
        return true
    }

    override fun dispatchSetRepeatMode(player: Player, @Player.RepeatMode repeatMode: Int): Boolean {
        player.repeatMode = repeatMode
        return true
    }

    override fun dispatchSetShuffleModeEnabled(player: Player, shuffleModeEnabled: Boolean): Boolean {
        player.shuffleModeEnabled = shuffleModeEnabled
        return true
    }

    override fun dispatchStop(player: Player, reset: Boolean): Boolean {
        player.stop(reset)
        return true
    }

}