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

    //extract frames from video
    override fun createVideo(inputPath: String, fileName: String, outputPath: String, photoQuality: Int, videoQuality: Int, fps: Int, frameTimes: List<String>) {

        val savePath = "$internalStoragePath/postProcess/${System.currentTimeMillis()}/"
        val saveName = fileName.substring(0, fileName.lastIndexOf("."))

        File(savePath).mkdirs()

        var selectedTimePoints = "select='"

        frameTimes.forEach {
            selectedTimePoints += "lt(prev_pts*TB\\,$it)*gte(pts*TB\\,$it)+"
        }

        selectedTimePoints = selectedTimePoints.substring(0, selectedTimePoints.length - 1)
        selectedTimePoints += "'"

        /**
         * -i : input
         * -vf : filter_graph set video filters
         * -filter:v : video filter for gived parameters - like requested frame times
         * -qscale:v :quality parameter
         * -vsync : drop : This allows to work around any non-monotonic time-stamp errors //not sure how it totally works
         */
        val cmd = arrayOf("-i", "$inputPath/$fileName", "-qscale:v", "$photoQuality", "-filter:v", selectedTimePoints, "-vsync", "drop", "$savePath${saveName}_%03d.jpg")

        Log.d(TAG, "list = $selectedTimePoints")

        ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
            override fun onFailure(result: String?) {
                Log.d(TAG, "FAIL with output : $result")
            }

            override fun onSuccess(result: String?) {
                Log.d(TAG, "SUCCESS with output : $result")

                //create video from frames
                createVideoFromFrames(savePath, saveName, outputPath, videoQuality, fps)
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

    private fun createVideoFromFrames(savePath: String, saveName: String, outputPath: String, videoQuality: Int, fps: Int) {

        /**
         * -i : input
         * -framerate : frame rate of the video
         * -crf quality of the output video
         * -pix_fmt pixel format
         */
        val cmd = arrayOf("-framerate", "$fps", "-i", "$savePath${saveName}_%03d.jpg", "-crf", "$videoQuality", "-pix_fmt", "yuv420p", outputPath)

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