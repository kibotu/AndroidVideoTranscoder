package com.exozet.videoeditor

import android.content.Context
import android.net.Uri
import androidx.annotation.IntRange
import io.reactivex.Observable
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import nl.bravobit.ffmpeg.FFtask
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

object FFMpegTranscoder {

    /**
     * true if FFmpeg is supported.
     *
     * @param context application context
     */
    fun isSupported(context: Context): Boolean = FFmpeg.getInstance(context) != null

    /**
     * @param context application context
     * @param frameTimes list of ms of the requested frames at source video - example "1.023"</pre>
     * @param inputVideo Uri of the source video
     * @param id unique output folder id
     * @param outputDir optional - output directory, if not provided internal storage will be used
     * @param photoQuality quality of extracted frames - Effective range for JPEG is 2-31 with 31 being the worst quality
     */
    fun extractFramesFromVideo(context: Context, frameTimes: List<String>, inputVideo: Uri, id: String, outputDir: Uri?, @IntRange(from = 1, to = 31) photoQuality: Int = 5): Observable<MetaData> {

        val ffmpeg = FFmpeg.getInstance(context)

        var task: FFtask? = null

        val internalStoragePath: String = context.filesDir.absolutePath

        return Observable.create<MetaData> { emitter ->

            if (emitter.isDisposed) {
                return@create
            }

            val percent = AtomicInteger()

            val total = frameTimes.size

            val startTime = System.currentTimeMillis()

            val localSavePath = "${outputDir ?: internalStoragePath}/postProcess/$id/$startTime/"

            //create new folder
            val file = File(localSavePath)
            if (!file.exists())
                file.mkdirs()

            // https://superuser.com/a/1330042
            val result = frameTimes.joinToString(separator = "+") {
                "lt(prev_pts*TB\\,$it)*gte(pts*TB\\,$it)"
            }

            /**
             * -i : input
             * -vf : filter_graph set video filters
             * -filter:v : video filter for given parameters - like requested frame times
             * -qscale:v :quality parameter [1,31]
             * -vsync : drop : This allows to work around any non-monotonic time-stamp errors //not sure how it totally works - if we set it to 0 it skips duplicate frames I guess
             */
            val cmd = arrayOf(
                "-threads", "${Runtime.getRuntime().availableProcessors()}",
                "-i", inputVideo.toString(),
                "-qscale:v", "$photoQuality",
                "-filter:v", "select='$result'",
                "-vsync", "0",
                "${localSavePath}image_%03d.jpg"
            )

            task = ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {

                override fun onFailure(result: String?) {
                    emitter.onError(Throwable(result))
                    //delete failed process folder
                    deleteFolder(localSavePath)
                }

                override fun onSuccess(result: String?) {
                    emitter.onNext(MetaData(uri = Uri.fromFile(file)))
                }

                override fun onProgress(progress: String?) {
                    // the 2nd parameter is the currently finished frame index
                    val currentFrame = progress?.split(" ")
                        ?.filterNot { it.isBlank() }
                        ?.take(2)
                        ?.lastOrNull()
                        ?.toIntOrNull()

                    // therefore we can can compute the current progress
                    if (currentFrame != null)
                        percent.set((100f * currentFrame / total).roundToInt())

                    emitter.onNext(MetaData(message = progress?.trimMargin(), progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                }

                override fun onStart() {
                    emitter.onNext(MetaData(message = "Starting ${Arrays.toString(cmd)}", progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                }

                override fun onFinish() {
                    emitter.onNext(MetaData(message = "Finished ${Arrays.toString(cmd)}", progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                    emitter.onComplete()
                }
            })

        }.doOnDispose {
            if (task?.killRunningProcess() == false)
                task?.sendQuitSignal()
        }
    }

    /**
     * Merges a sequence of images into a video. Returns a stream with [MetaData].
     *
     * @param context application context
     * @param frameFolder extracted frames directory
     * @param outputUri video output directory
     * @param [EncodingConfig] Encoding configurations.
     * @param deleteFramesOnComplete removes image sequence directory after successful completion.
     */
    fun createVideoFromFrames(context: Context, frameFolder: Uri, outputUri: Uri, config: EncodingConfig, deleteFramesOnComplete: Boolean = true): Observable<MetaData> {

        val ffmpeg = FFmpeg.getInstance(context)

        var task: FFtask? = null

        return Observable.create<MetaData> { emitter ->

            if (emitter.isDisposed) {
                return@create
            }

            val percent = AtomicInteger()

            val total = try {
                File(frameFolder.path).listFiles().size
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }

            val startTime = System.currentTimeMillis()

            /**
             * -y overrides output file
             * -r set source frame rate
             * -threads sets threads
             * -i input
             * -c:v encoding, here: libx264
             * -x264opts .h264 settings, we extract frames by a fixed gop size, (10 would mean out of 30 fps video, we extract 3 frames per second)
             * no-scenecut means no extra i-frames, since we never change scene during recording
             * -g fixed gop size; different than dynamic gop size which can be set with keyInt (max gop size interval) and min-keyint (min gop size interval)
             * -crf sets video quality
             * -maxrate:v sets max bit-rate
             * -bufsize:v sets buffer size (manages average bitrate)
             * -pix_fmt sets pixel format
             * -preset sets ffmpeg encoding pre-sets, most likely will not end with good results
             */
            val cmd = mutableListOf<String>().apply {
                add("-y")

                config.sourceFrameRate?.let {
                    add("-r"); add("${config.sourceFrameRate}")
                }

                add("-threads"); add("${Runtime.getRuntime().availableProcessors()}")
                add("-i"); add("${frameFolder.path}/image_%03d.jpg")
                add("-c:v"); add("${config.encoding}")
                add("-x264opts"); add("keyint=${config.keyInt}:min-keyint=${config.minKeyInt}:no-scenecut")

                config.gopValue?.let {
                    add("-g"); add("${config.gopValue}")
                }

                config.videoQuality?.let {
                    add("-crf"); add("${config.videoQuality}")
                }

                config.maxrate?.let {
                    add("-maxrate:v"); add("${config.maxrate}k")
                }

                config.bufsize?.let {
                    add("-bufsize:v"); add("${config.bufsize}k")
                }

                add("-pix_fmt"); add("${config.pixelFormat}")

                config.preset?.let {
                    add("-preset"); add("${config.preset}")
                }
                add("${outputUri.path}")

            }.toTypedArray()

            task = ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {

                override fun onFailure(result: String?) {
                    emitter.onError(Throwable(result))
                    //delete failed process folder
                    deleteFolder(outputUri.path!!)
                }

                override fun onSuccess(result: String?) {
                    emitter.onNext(MetaData(uri = outputUri, message = result))
                }

                override fun onProgress(progress: String?) {
                    // the 2nd parameter is the currently finished frame index
                    val currentFrame = progress?.split(" ")
                        ?.filterNot { it.isBlank() }
                        ?.take(2)
                        ?.lastOrNull()
                        ?.toIntOrNull()

                    // therefore we can can compute the current progress
                    if (currentFrame != null)
                        percent.set((100f * currentFrame / total).roundToInt())

                    emitter.onNext(MetaData(message = progress?.trimMargin(), progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                }

                override fun onStart() {
                    emitter.onNext(MetaData(message = "Starting ${Arrays.toString(cmd)}", progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                }

                override fun onFinish() {
                    emitter.onNext(MetaData(message = "Finished ${Arrays.toString(cmd)}", progress = percent.get(), duration = System.currentTimeMillis() - startTime))

                    if (deleteFramesOnComplete) {
                        val deleteStatus = deleteFolder(frameFolder.path!!)
                        log("Delete temp frame save path status: $deleteStatus")
                    }

                    emitter.onComplete()
                }
            })
        }.doOnDispose {
            if (task?.killRunningProcess() == false)
                task?.sendQuitSignal()
        }
    }


    /**
     * Deletes directory path recursively.
     */
    private fun deleteFolder(path: String): Boolean = File(path).deleteRecursively()

    /**
     * Deletes all post processing images.
     */
    fun deleteAllProcessFiles(context: Context): Boolean = deleteFolder("${context.filesDir.absolutePath}/postProcess/")

    /**
     * Deletes extracted frames directory.
     */
    fun deleteExtractedFrameFolder(folderUri: Uri): Boolean = if (folderUri.path?.contains("postProcess") == true) {
        deleteFolder(folderUri.path!!)
    } else {
        false
    }
}