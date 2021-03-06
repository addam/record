package addam.record

import android.graphics.Paint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Switch
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.concurrent.thread
import kotlin.math.round

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun record(switch: View) {
        switch as Switch
        if (switch.isChecked) {
            thread {
                val recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)
                assert(recorder.state != 0)
                recorder.startRecording()
                val buffer = ByteArray(BUFFER_SIZE)
                while (switch.isChecked) {
                    recorder.read(buffer, 0, BUFFER_SIZE)
                    val wave = unpack(buffer)
                    Fft.autocorrelation(wave)
                    render(wave)
                }
                recorder.release()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun render(corr: DoubleArray) {
        val points = FloatArray(corr.size * 2)
        val width = surfaceView.width
        val height = surfaceView.height
        for (i in corr.indices) {
            points[2 * i] = i * width / corr.size.toFloat()
            points[2 * i + 1] = (corr[i] * height / corr[0]).toFloat()
        }
        runOnUiThread {
            val surface = surfaceView.holder.surface
            val canvas = surface.lockHardwareCanvas()
            canvas.drawRGB(255, 255, 255)
            canvas.drawLines(points, Paint())
            surface.unlockCanvasAndPost(canvas)
        }
        runOnUiThread {
            val peak = findPeak(corr)
            var frequency = SAMPLING_RATE_IN_HZ / peak
            frequency = round(10 * frequency) / 10
            textView.text = "f = ${frequency} Hz"
        }
    }

    companion object {
        fun unpack(bytes: ByteArray): DoubleArray {
            val samples = DoubleArray(bytes.size / 2)
            for (i in samples.indices) {
                samples[i] = bytes[2 * i + 1] + bytes[2 * i] / 256.0 - 127.5
            }
            return samples
        }

        fun findPeak(wave: DoubleArray): Double {
            var i = 0
            while (wave[i + 1] < wave[i]) i++
            while (wave[i+1] > wave[i]) i++
            return i.toDouble() // todo: interpolate
        }

        fun nearestPowerOfTwo(x: Int): Int {
            val result = Integer.highestOneBit(x)
            return if (x == result) result else result shl 1
        }

        private var SAMPLING_RATE_IN_HZ = 44100
        private var CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private var AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private var BUFFER_SIZE_FACTOR = 2
        private val BUFFER_SIZE = nearestPowerOfTwo(AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR)
    }
}
