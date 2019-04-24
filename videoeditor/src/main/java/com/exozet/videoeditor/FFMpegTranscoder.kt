package com.exozet.videoeditor

import android.content.Context
import android.util.Log
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.FFmpeg
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException
import java.io.File

class FFMpegTranscoder(val context: Context) : IFFMpegTranscoder {

    // todo : cache folder possible?
    val internalStoragePath = context.filesDir.path

    private val TAG = FFMpegTranscoder::class.java.simpleName

    var ffmpeg = FFmpeg.getInstance(context)

    init {
        loadFFMpegBinary()
    }

    override fun extractFramesFromVideo(inputPath: String, fileName: String, outputPath: String) {

        val savePath = "$internalStoragePath/postProcess/${System.currentTimeMillis()}/"
        val saveName = fileName.substring(0, fileName.lastIndexOf("."))

        File(savePath).mkdirs()

        val cmd = arrayOf("-i", "$inputPath/$fileName", "-vf", "scale=1280:-1,fps=2", "$savePath$saveName%03d.jpg")

        execFFmpegBinary(cmd, object : ExecuteBinaryResponseHandler() {
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

        val cmd = arrayOf("-framerate", "3", "-i", "$savePath/$saveName%03d.jpg", "-pix_fmt", "yuv420p", outputPath)

        execFFmpegBinary(cmd, object : ExecuteBinaryResponseHandler() {
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

    private fun loadFFMpegBinary() {
        try {
            ffmpeg.loadBinary(object : LoadBinaryResponseHandler() {
                override fun onFailure() {
                    Log.e(TAG, "FAILED load binary")
                }

                override fun onSuccess() {
                    Log.d(TAG, "SUCCESS load binary")
                    super.onSuccess()
                }
            })
        } catch (e: FFmpegNotSupportedException) {
            loge("NOT Supported exception ${e.message}")
        }

    }

    private fun execFFmpegBinary(command: Array<String>, processListener: ExecuteBinaryResponseHandler) {
        try {
            ffmpeg.execute(command, processListener)
        } catch (e: FFmpegCommandAlreadyRunningException) {
            // do nothing for now
        }

    }


}