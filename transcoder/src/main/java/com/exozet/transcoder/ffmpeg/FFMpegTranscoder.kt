package com.exozet.transcoder.ffmpeg

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.IntRange
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import io.reactivex.Observable
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil


object FFMpegTranscoder {

    /**
     * true if FFmpeg is supported.
     *
     * @param context application context
     */
    fun isSupported(context: Context): Boolean = Config.getSupportedCameraIds(context).isNotEmpty()

    /**
     * @param context application context
     * @param frameTimes list of ms of the requested frames at source video - example "1.023"</pre>
     * @param inputVideo Uri of the source video
     * @param id unique output folder id
     * @param outputDir optional - output directory, if not provided internal storage will be used
     * @param photoQuality quality of extracted frames - Effective range for JPEG is 2-31 with 31 being the worst quality
     */
    fun extractFramesFromVideo(context: Context, frameTimes: List<String>, inputVideo: Uri, id: String, outputDir: Uri?, @IntRange(from = 1, to = 31) photoQuality: Int = 5): Observable<Progress> {

        val internalStoragePath: String = context.filesDir.absolutePath

        return Observable.create<Progress> { emitter ->

            if (emitter.isDisposed) {
                return@create
            }

            val percent = AtomicInteger()

            val total = frameTimes.size

            val startTime = System.currentTimeMillis()

            val localSavePath = "${outputDir ?: "$internalStoragePath/postProcess/$id/$startTime/"}"

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

            Config.enableStatisticsCallback { newStatistics ->
                percent.set(
                    ceil((100.0 * newStatistics.videoFrameNumber / total)).coerceIn(
                        0.0,
                        100.0
                    ).toInt()
                )
                emitter.onNext(
                    Progress(
                        uri = Uri.fromFile(file),
                        message = "",
                        progress = percent.get(),
                        duration = System.currentTimeMillis() - startTime
                    )
                )
            }
            val rc: Int = FFmpeg.execute(cmd)

            if (rc == Config.RETURN_CODE_SUCCESS) {
                emitter.onNext(
                    Progress(
                        uri = Uri.fromFile(file),
                        message = "Finished ${Arrays.toString(cmd)}",
                        progress = percent.get(),
                        duration = System.currentTimeMillis() - startTime
                    )
                )
                emitter.onComplete()
            } else if (rc == Config.RETURN_CODE_CANCEL) {
                emitter.onError(Throwable(result))
                //delete failed process folder
                deleteFolder(localSavePath)
            } else {
                emitter.onError(
                    Throwable(
                        String.format(
                            "Command execution failed with rc=%d and the output below.",
                            rc
                        )
                    )
                )
                Config.printLastCommandOutput(Log.INFO)
            }

        }.doOnDispose {
            FFmpeg.cancel()
        }
    }

    /**
     * Stream copies and adds few more idr frames.
     *
     * @param context application context
     * @param inputVideo input video
     * @param outputUri output video
     */
    fun transcode(inputVideo: Uri, outputUri: Uri): Observable<Progress> {

        return Observable.create<Progress> { emitter ->

            if (emitter.isDisposed) {
                return@create
            }

            val percent = AtomicInteger()

            val startTime = System.currentTimeMillis()

            // ffmpeg -i example_walkaround.mov -vf vidstabdetect=shakiness=10:accuracy=15 -f null -
            // ffmpeg -i example_walkaround.mov -vf vidstabtransform=smoothing=30:input="transforms.trf" example_walkaround_stabilized.mp4

            val cmd = mutableListOf<String>().apply {
                add("-y")
                add("-threads"); add("${Runtime.getRuntime().availableProcessors()}")
                add("-i"); add("${inputVideo.path}")
                add("-vf"); add("deshake") // https://www.ffmpeg.org/ffmpeg-filters.html#deshake

                add("${outputUri.path}")

            }.toTypedArray()

            Config.enableStatisticsCallback {
                emitter.onNext(Progress(uri = outputUri, message = "", progress = percent.get(), duration = System.currentTimeMillis() - startTime))
            }
            val rc: Int = FFmpeg.execute(cmd)

            if (rc == Config.RETURN_CODE_SUCCESS) {
                emitter.onNext(Progress(uri = outputUri, message = "Finished ${Arrays.toString(cmd)}", progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                emitter.onComplete()

            } else if (rc == Config.RETURN_CODE_CANCEL) {
                emitter.onError(Throwable(String.format("Command execution failed with rc=%d and the output below.", rc)))
                //delete failed process folder
                deleteFolder(outputUri.path!!)

            } else {
                emitter.onError(Throwable(String.format("Command execution failed with rc=%d and the output below.", rc)))
                Config.printLastCommandOutput(Log.INFO)
            }

            /*task = ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {

                override fun onFailure(result: String?) {
                    emitter.onError(Throwable(result))
                    //delete failed process folder
                    deleteFolder(outputUri.path!!)
                }

                override fun onSuccess(result: String?) {
                    emitter.onNext(Progress(uri = outputUri, message = result))
                }

                override fun onProgress(progress: String?) {

                    // get total video duration by parsing 'progress', e.g.:
                    // Duration: 00:01:03.35, start: 0.000000, bitrate: 4296 kb/s
                    // check against time, e.g.:
                    // frame=  165 fps= 10 q=29.0 size=    3584kB time=00:00:05.68 bitrate=5161.0kbits/s speed=0.343x
                    // where time/duration = percent

                    emitter.onNext(Progress(uri = outputUri, message = progress?.trimMargin(), progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                }

                override fun onStart() {
                    emitter.onNext(Progress(uri = outputUri, message = "Starting ${Arrays.toString(cmd)}", progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                }

                override fun onFinish() {
                    emitter.onNext(Progress(uri = outputUri, message = "Finished ${Arrays.toString(cmd)}", progress = percent.get(), duration = System.currentTimeMillis() - startTime))
                    emitter.onComplete()
                }
            })*/
        }.doOnDispose {
            FFmpeg.cancel()
        }
    }

    /**
     * Merges a sequence of images into a video. Returns a stream with [Progress].
     *
     * @param context application context
     * @param frameFolder extracted frames directory
     * @param outputUri video output directory
     * @param [EncodingConfig] Encoding configurations.
     * @param deleteFramesOnComplete removes image sequence directory after successful completion.
     */
    fun createVideoFromFrames(frameFolder: Uri, outputUri: Uri, config: EncodingConfig, deleteFramesOnComplete: Boolean = true): Observable<Progress> {
        return Observable.create<Progress> { emitter ->

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
             * -movflags +faststart: Allows video to playback before it is completely downloaded in
             *  the case of progressive download viewing. Useful if you are hosting the video,
             *  otherwise superfluous if uploading to a video service like YouTube.
             */
            val cmd = mutableListOf<String>().apply {
                add("-y")

                config.sourceFrameRate?.let {
                    add("-framerate"); add("${config.sourceFrameRate}")
                }

                add("-threads"); add("${Runtime.getRuntime().availableProcessors()}")

                add("-i"); add("${frameFolder.path}/image_%03d.jpg")

                add("-r"); add("${config.outputFrameRate}")

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

                add("-movflags"); add("+faststart")

                add("${outputUri.path}")

            }.toTypedArray()

            Config.enableStatisticsCallback { newStatistics ->
                percent.set(ceil((100.0 * newStatistics.videoFrameNumber / total)).coerceIn(0.0, 100.0).toInt())
                emitter.onNext(Progress(uri = outputUri, message = "", progress = percent.get(), duration = System.currentTimeMillis() - startTime))
            }
            val rc: Int = FFmpeg.execute(cmd)

            if (rc == Config.RETURN_CODE_SUCCESS) {
                emitter.onNext(Progress(uri = outputUri, message = "Finished ${Arrays.toString(cmd)}", progress = percent.get(), duration = System.currentTimeMillis() - startTime))

                if (deleteFramesOnComplete) {
                    val deleteStatus = deleteFolder(frameFolder.path!!)
                    log("Delete temp frame save path status: $deleteStatus")
                }
                emitter.onComplete()

            } else if (rc == Config.RETURN_CODE_CANCEL) {
                emitter.onError(Throwable(String.format("Command execution failed with rc=%d and the output below.", rc)))
                //delete failed process folder
                deleteFolder(outputUri.path!!)

            } else {
                emitter.onError(Throwable(String.format("Command execution failed with rc=%d and the output below.", rc)))
                Config.printLastCommandOutput(Log.INFO)
            }

        }.doOnDispose {
            FFmpeg.cancel()
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