package com.exozet.mcvideoeditor

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.annotation.IntRange
import com.exozet.videoeditor.EncodingConfig
import com.exozet.videoeditor.Progress
import io.reactivex.Observable
import java.io.File
import java.lang.Exception

object MediaCodecTranscoder {


    fun extractFramesFromVideo(
        context: Context,
        frameTimes: List<Double>,
        inputVideo: Uri,
        id: String,
        outputDir: Uri?,
        @IntRange(from = 1, to = 100) photoQuality: Int = 100
    ): Observable<Progress> {
        val mediaCodec = MediaCodecExtractImages()

        val internalStoragePath: String = context.filesDir.absolutePath
        val startTime = System.currentTimeMillis()

        val localSavePath = "${outputDir ?: "$internalStoragePath/postProcess/$id/$startTime/"}"

        //create new folder
        val file = File(localSavePath)
        if (!file.exists())
            file.mkdirs()

        return mediaCodec.extractMpegFrames(inputVideo,frameTimes, outputDir, photoQuality)
        }

    fun createVideoFromFrames(
        frameFolder: Uri,
        outputUri: Uri,
        config: MediaConfig,
        deleteFramesOnComplete: Boolean = true
    ): Observable<Progress> {

        return Observable.create{ emitter ->

            var items = File(frameFolder.path!!).listFiles().sorted()

            val startTime = System.currentTimeMillis()

            val mediaCodecCreateVideo = MediaCodecCreateVideo(config,object:
                MediaCodecCreateVideo.IBitmapToVideoEncoderCallback{
                override fun onEncodingComplete(outputFile: File?) {
                    Log.i("MediaCodecTranscoder", "successfully created ${outputFile?.absolutePath}")
                    emitter.onNext(Progress(100,null, Uri.parse(outputFile?.absolutePath),System.currentTimeMillis() - startTime))

                    if (deleteFramesOnComplete){
                        val deleteStatus = deleteFolder(frameFolder.path!!)
                        Log.i("MediaCodecTranscoder", "Delete temp frame save path status: $deleteStatus")
                    }
                    emitter.onComplete()
                }

                override fun onEncodingFail(e: Exception?) {
                    Log.i("MediaCodecTranscoder", "something goes wrong $e")
                    emitter.onError(Throwable(e))
                }
            })

            val firstFrame = BitmapFactory.decodeFile(items[0].absolutePath)

            mediaCodecCreateVideo.startEncoding(firstFrame.width, firstFrame.height, outputUri)

            items.forEachIndexed { index, item ->

                val progress = Progress((((index.toFloat())/(items.size-1.toFloat()))*100).toInt(),null, null,System.currentTimeMillis() - startTime)
                val bMap = BitmapFactory.decodeFile(item.absolutePath)

                Log.v("MediaCodecTranscoder", "on process ${progress}")

                emitter.onNext(progress)
                mediaCodecCreateVideo.queueFrame(bMap)

            }
            mediaCodecCreateVideo.stopEncoding()
        }
    }

    /**
     * Deletes directory path recursively.
     */
    private fun deleteFolder(path: String): Boolean = File(path).deleteRecursively()

}