package com.exozet.videoeditor.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.exozet.videoeditor.FFMpegTranscoder
import kotlinx.android.synthetic.main.activity_main.*
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.os.Environment.getExternalStoragePublicDirectory

class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.java.simpleName


    lateinit var ffMpegTranscoder: FFMpegTranscoder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val path = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).absolutePath

        // init FFMpeg object

        init_ffmpeg.setOnClickListener {
            ffMpegTranscoder = FFMpegTranscoder(this)
        }

        make_video.setOnClickListener {
            ffMpegTranscoder.extractFramesFromVideo(path,"SampleVideo_1280x720_10mb.mp4","${path}/output${System.currentTimeMillis()}.mp4")
        }


    }
}
