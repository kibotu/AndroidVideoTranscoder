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

        val times = listOf("0.002","0.102","0.202","0.502","0.702","1.002","1.202","1.502","1.702","2.052","3.402")

        make_video.setOnClickListener {
            ffMpegTranscoder.createVideo(path,"test.mp4","${path}/output_${System.currentTimeMillis()}.mp4",2,18,1,times)
        }


    }
}
