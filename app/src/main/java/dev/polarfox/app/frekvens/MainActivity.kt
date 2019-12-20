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
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.support.v4.app.ActivityCompat
import kotlin.math.*
import android.text.InputFilter
import android.widget.*
import dev.polarfox.app.frekvens.R
import android.widget.CompoundButton
import android.util.Log

import android.Manifest
import android.media.MediaRecorder
import android.os.Environment
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule

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
    var fr = 10000.0                      // frequency
    var ph = 0.0                        // phase
    var mix = 0f

//    var buffsize = AudioTrack.getMinBufferSize(
//        sr, AudioFormat.CHANNEL_OUT_MONO,
//        AudioFormat.ENCODING_PCM_16BIT
//    )
    var buffsize = 64

    var samples = ShortArray(buffsize)
    // create an audiotrack object
    // <init>(streamType: Int, sampleRateInHz: Int, channelConfig: Int, audioFormat: Int, bufferSizeInBytes: Int, mode: Int)
    var audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC, sr,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        buffsize,
        AudioTrack.MODE_STREAM
    )

    /* Recorder */
    private var output: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private var state: Boolean = false
    private var recordingStopped: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        /* Recorder */
        output = Environment.getExternalStorageDirectory().absolutePath + "/recording.wav"
        Log.i("absolutePath",Environment.getExternalStorageDirectory().absolutePath)
        mediaRecorder = MediaRecorder()

        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder?.setOutputFile(output)

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

        /* Recorder */
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions,0)
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
        Toast.makeText(this, "Will be there soon in a future release", Toast.LENGTH_LONG).show()
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
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions,0)
        }

        if (!isRunning) {
            isRunning = true
            //button?.text = "Pause"
            button?.text = "Play"
            startRecording()
            val timer = Timer("schedule", true)
            timer.schedule(50){
                Log.i("Delay","50ms, for compensating HW delay")
                play()
            }
            timer.schedule(150){
                Log.i("Delay","150ms, we need to cut down rest[2~140ms]")
                stopRecording()
            }
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
                Log.i("Play","Start playing")
                if(isRunning){
                    fillblock()
                    isRunning = false
                }
                audioTrack.stop()
            }
            var orfr = fr
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
                    /* Frequency Sweep
                    * 100Hz / 32samples -> 800Hz sweep
                    * 100Hz / 16samples -> 1600Hz sweep
                    * 100Hz / 8samples -> 3200Hz sweep
                    * */
                    if (i%16==0){
                        fr+=1000.0
                    }
                    //ph += twopi * fr / sr
                    ph = twopi * fr * i / sr
                }
                audioTrack.write(samples, 0, buffsize)

                // return to original frequency
                fr = orfr
                Log.i("Play","Finish playing")
                val timer = Timer("schedule", true)
            }

        }
        t?.start()
    }

    private fun startRecording(){
        try {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            state = true
            Log.i("Record","start recording")
            Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording(){
        if(state){
            mediaRecorder?.stop()
            mediaRecorder?.release()
            state = false
            Log.i("Record","stop recording")
        }else{
            //Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
    }
}
