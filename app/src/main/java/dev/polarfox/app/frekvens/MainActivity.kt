package dev.polarfox.app.frekvens

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity;
import android.view.Menu
import android.view.MenuItem
import android.view.View

import kotlinx.android.synthetic.main.activity_main.*
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.Handler
import android.provider.MediaStore
import android.widget.SeekBar
import kotlin.experimental.and
import android.widget.Toast
import android.widget.SeekBar.OnSeekBarChangeListener
import android.R.attr.track
import java.util.Collections.frequency
import android.R.attr.start
import android.widget.Button
import android.widget.TextView
import kotlin.math.*


class MainActivity : AppCompatActivity() {

    private var frequencyBar: SeekBar? = null
    private var frequencyLabel: TextView? = null
    private var button: Button? = null
    // --- variables for the sound synthesis ---
    var t: Thread? = null
    var isRunning = false
    val sr = 44100                   // maximum frequency
    //val twopi = 8.0 * atan(1.0)      // atan(1) is Pi/4
    val twopi = 2 * Math.PI
    var amp = 10000                        // amplitude
    var fr = 440.0                      // frequency
    var ph = 0.0                        // phase
    var mix = 0f
    var buffsize = AudioTrack.getMinBufferSize(
        sr, AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    var samples = ShortArray(buffsize)
    // create an audiotrack object
    var audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC, sr,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        buffsize,
        AudioTrack.MODE_STREAM
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        frequencyBar = findViewById(R.id.seekBar)
        frequencyLabel = findViewById(R.id.textView)
        button = findViewById(R.id.playPause)
        frequencyBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // position will be between 0 and 100
                val minp = 0
                val maxp = 1000

                // The result should be between 100 an 10000000
                val minv = ln(30.0)
                val maxv = ln(18000.0)

                // calculate adjustment factor
                val scale = (maxv-minv) / (maxp-minp)

                fr = exp(minv + scale * (progress-minp))

                frequencyLabel!!.text = floor(fr).toInt().toString() + " Hz"
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun onPlayPauseTap(view: View) {
        if (!isRunning) {
            isRunning = true
            button!!.text = "Pause"
            play()
        } else {
            isRunning = false
            button!!.text = "Play"
        }
    }

    fun play() {
        // opening a thread for sound synthesis
        t = object : Thread() {

            override fun run() {
                // set process priority
                priority = Thread.MAX_PRIORITY

                // start audio
                audioTrack.play()
                // synthesis loop
                while (isRunning) {
                    fillblock()
                }
                audioTrack.stop()
            }

            /**
             * This methods fills the buffer
             */
            fun fillblock() {
                for (i in 0 until buffsize) {

                    val saw = (amp * (ph % (sr / fr)) / (sr / fr) - 1).toShort()
                    val sine = (amp * sin(ph)).toShort()
                    val mixed = (mix * saw + (1 - mix) * sine).roundToInt().toShort()

                    samples[i] = sine
                    ph += twopi * fr / sr

                }
                audioTrack.write(samples, 0, buffsize)
            }
        }
        t!!.start()
    }

}
