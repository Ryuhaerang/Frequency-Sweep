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
import kotlin.experimental.and
import android.widget.SeekBar.OnSeekBarChangeListener
import android.R.attr.track
import java.util.Collections.frequency
import android.R.attr.start
import kotlin.math.*
import android.text.InputFilter
import android.widget.*
import dev.polarfox.app.frekvens.R
import android.widget.CompoundButton

class MainActivity : AppCompatActivity() {

    private var frequencyBar: SeekBar? = null
    private var frequencyLabel: TextView? = null
    private var valueGoal: EditText? = null
    private var button: Button? = null
    private var modeSwitch: Switch? = null
    // --- variables for logarithmic slider ---
    // position will be between 0 and 100
    val minp = 0
    val maxp = 1000
    // The result should be between 100 an 10000000
    val minv = ln(30.0)
    val maxv = ln(18000.0)
    // --- variables for the sound synthesis ---
    var sawMode = false
    var t: Thread? = null
    var isRunning = false
    val sr = 44100                   // maximum frequency
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
        valueGoal = findViewById(R.id.goalText)
        valueGoal?.filters = arrayOf<InputFilter>(InputFilterMinMax("1", "18000"))
        frequencyBar?.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

                // calculate adjustment factor
                val scale = (maxv - minv) / (maxp - minp)

                fr = exp(minv + scale * (progress - minp))

                frequencyLabel?.text = floor(fr).toInt().toString() + " Hz"
            }
        })
        modeSwitch = findViewById(R.id.modeSwitch)
        modeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            this.sawMode = isChecked
        }
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

    fun onDown(view: View) {
        val value = floor(this.fr).toInt() - 1;
        setValue(value)
    }

    fun onUp(view: View) {
        val value = floor(this.fr).toInt() + 1;
        setValue(value)
    }

    fun setValue(value: Int) {
        if (value in 30..18000) {
            val scale = (maxv-minv) / (maxp-minp)
            frequencyBar?.progress = round((ln(value * 1.0)-minv) / scale + minp).toInt();
            this.fr = value * 1.0
            frequencyLabel?.text = fr.toInt().toString() + " Hz"
        }
    }

    fun onSetTap(view: View) {
        val value = Integer.valueOf(this.valueGoal?.text.toString())
        this.setValue(value)
    }

    fun onPlayPauseTap(view: View) {
        if (!isRunning) {
            isRunning = true
            button?.text = "Pause"
            play()
        } else {
            isRunning = false
            button?.text = "Play"
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

                    if (sawMode) {
                        // sawtooth wave
                        val saw1 = (amp * sin(ph)).toShort()
                        val saw2 = (saw1 - 0.5 * amp * sin(2*ph)).toShort()
                        val saw3 = (saw2 - (1/3.0) * amp * sin(3*ph)).toShort()
                        val saw4 = (saw3 - (1/4.0) * amp * sin(4*ph)).toShort()
                        samples[i] = saw4
                    } else {
                        // sine wave
                        samples[i] = (amp * sin(ph)).toShort()
                    }

                    ph += twopi * fr / sr

                }
                audioTrack.write(samples, 0, buffsize)
            }
        }
        t?.start()
    }

}
