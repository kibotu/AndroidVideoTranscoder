package com.exozet.transcoder.mcvideoeditor

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.annotation.IntRange
import com.exozet.transcoder.ffmpeg.Progress
import com.exozet.transcoder.ffmpeg.log
import io.reactivex.Observable
import java.io.File

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

        return mediaCodec.extractMpegFrames(inputVideo, frameTimes, outputDir, photoQuality)
    }

    fun createVideoFromFrames(
        frameFolder: Uri,
        outputUri: Uri,
        config: MediaConfig = MediaConfig(),
        deleteFramesOnComplete: Boolean = true
    ): Observable<Progress> {

        val shouldCancel =  MediaCodecExtractImages.Cancelable()

        return Observable.create<Progress> { emitter ->

            if (emitter.isDisposed)
                return@create


            val items = File(frameFolder.path!!).listFiles()?.sorted() ?: return@create

            val startTime = System.currentTimeMillis()

            val mediaCodecCreateVideo = MediaCodecCreateVideo(config, object :
                MediaCodecCreateVideo.IBitmapToVideoEncoderCallback {
                override fun onEncodingComplete(outputFile: File?) {
                    Log.i("MediaCodecTranscoder", "successfully created ${outputFile?.absolutePath}")
                    emitter.onNext(Progress(100, null, Uri.parse(outputFile?.absolutePath), System.currentTimeMillis() - startTime))

                    if (deleteFramesOnComplete) {
                        val deleteStatus = deleteFolder(frameFolder.path!!)
                        Log.i("MediaCodecTranscoder", "Delete temp frame save path status: $deleteStatus")
                    }
                    emitter.onComplete()
                }

                override fun onEncodingFail(e: Exception?) {
                    Log.i("MediaCodecTranscoder", "something went wrong $e")
                    emitter.onError(Throwable(e))
                }
            })

            val firstFrame = BitmapFactory.decodeFile(items.firstOrNull()?.absolutePath ?: return@create)

            mediaCodecCreateVideo.startEncoding(firstFrame.width, firstFrame.height, outputUri, shouldCancel)

            if (!firstFrame.isRecycled) firstFrame.recycle()

            items.forEachIndexed { index, item ->

                val progress = Progress((((index.toFloat()) / (items.size - 1.toFloat())) * 100).toInt(), null, null, System.currentTimeMillis() - startTime)
                val bMap = BitmapFactory.decodeFile(item.absolutePath)

                log("MediaCodecTranscoder  on process $progress")

                emitter.onNext(progress)
                mediaCodecCreateVideo.queueFrame(bMap)

            }
            mediaCodecCreateVideo.stopEncoding()
        }.doOnDispose {
            shouldCancel.cancel.set(true)
        }

    }

    /**
     * Deletes directory path recursively.
     */
    private fun deleteFolder(path: String): Boolean = File(path).deleteRecursively()
}