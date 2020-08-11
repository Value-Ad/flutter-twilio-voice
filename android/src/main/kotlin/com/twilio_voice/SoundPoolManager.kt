package com.twilio_voice

import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build

class SoundPoolManager private constructor(context: Context) {

    companion object {
        private var instance: SoundPoolManager? = null

        fun getInstance(context: Context): SoundPoolManager? {
            if (instance == null) {
                instance = SoundPoolManager(context)
            }
            return instance
        }
    }

    private var playing = false
    private var loaded = false
    private var playingCalled = false
    private val volume: Float
    private var soundPool: SoundPool? = null
    private val ringingSoundId: Int
    private var ringingStreamId = 0
    private val disconnectSoundId: Int

    fun playRinging() {
        if (loaded && !playing) {
            ringingStreamId = soundPool!!.play(ringingSoundId, volume, volume, 1, -1, 1f)
            playing = true
        } else {
            playingCalled = true
        }
    }

    fun stopRinging() {
        if (playing) {
            soundPool!!.stop(ringingStreamId)
            playing = false
        }
    }

    fun playDisconnect() {
        if (loaded && !playing) {
            soundPool!!.play(disconnectSoundId, volume, volume, 1, 0, 1f)
            playing = false
        }
    }

    fun release() {
        if (soundPool != null) {
            soundPool!!.unload(ringingSoundId)
            soundPool!!.unload(disconnectSoundId)
            soundPool!!.release()
            soundPool = null
        }
        instance = null
    }

    init {
        // AudioManager audio settings for adjusting the volume
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val actualVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        volume = actualVolume / maxVolume

        // Load the sounds
        val maxStreams = 1
        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SoundPool.Builder()
                    .setMaxStreams(maxStreams)
                    .build()
        } else {
            SoundPool(maxStreams, AudioManager.STREAM_MUSIC, 0)
        }
        soundPool!!.setOnLoadCompleteListener { soundPool, sampleId, status ->
            loaded = true
            if (playingCalled) {
                playRinging()
                playingCalled = false
            }
        }
        ringingSoundId = soundPool!!.load(context, R.raw.incoming, 1)
        disconnectSoundId = soundPool!!.load(context, R.raw.disconnect, 1)
    }
}