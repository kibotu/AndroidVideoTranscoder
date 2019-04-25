package com.exozet.videoeditor

import android.content.Context
import android.util.Log
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import java.io.File

class FFMpegTranscoder(val context: Context) : IFFMpegTranscoder {

    // todo : cache folder possible?
    val internalStoragePath = context.filesDir.path

    private val TAG = FFMpegTranscoder::class.java.simpleName

    var ffmpeg = FFmpeg.getInstance(context)

    override fun extractFramesFromVideo(inputPath: String, fileName: String, outputPath: String) {

        val savePath = "$internalStoragePath/postProcess/${System.currentTimeMillis()}/"
        val saveName = fileName.substring(0, fileName.lastIndexOf("."))

        File(savePath).mkdirs()

        val cmd = arrayOf("-i", "$inputPath/$fileName", "-vf", "fps=2","-qscale:v", "2", "$savePath$saveName%03d.jpg")

        ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
            override fun onFailure(result: String?) {
                Log.d(TAG, "FAIL with output : $result")
            }

            override fun onSuccess(result: String?) {
                Log.d(TAG, "SUCCESS with output : $result")

                //create video from frames
                createVideoFromFrames(savePath, saveName, outputPath)
            }

            override fun onProgress(progress: String?) {
                Log.d(TAG, "Started command : ffmpeg $cmd")
                Log.d(TAG, "progress : $progress")
            }

            override fun onStart() {
                Log.d(TAG, "Started command : ffmpeg $cmd")
            }

            override fun onFinish() {
                Log.d(TAG, "Finished command : ffmpeg $cmd")
            }
        })
    }

    override fun createVideoFromFrames(savePath: String, saveName: String, outputPath: String) {

        val cmd = arrayOf("-framerate", "3", "-i", "$savePath/$saveName%03d.jpg","-crf", "18", "-pix_fmt", "yuv420p", outputPath)

        ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
            override fun onFailure(result: String?) {
                Log.d(TAG, "FAIL create video with output : $result")
            }

            override fun onSuccess(result: String?) {
                Log.d(TAG, "SUCCESS create video with output : $result")
            }

            override fun onProgress(progress: String?) {
                Log.d(TAG, "Started command create video : ffmpeg $cmd")
                Log.d(TAG, "progress create video : $progress")
            }

            override fun onStart() {
                Log.d(TAG, "Started command create video : ffmpeg $cmd")
            }

            override fun onFinish() {
                Log.d(TAG, "Finished command create video: ffmpeg $cmd")
            }
        })

    }



}