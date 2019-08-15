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

class FFMpegTranscoder(context: Context) : IFFMpegTranscoder {

    private val internalStoragePath: String = context.filesDir.absolutePath

    override fun isSupported(context: Context): Boolean = FFmpeg.getInstance(context) != null

    override fun transcode(context: Context, inputUri: Uri, outputUri: Uri, carId: String, maxrate: Int, bufsize: Int): Observable<MetaData> {

        val ffmpeg = FFmpeg.getInstance(context)

        var task: FFtask? = null

        val observable = Observable.create<MetaData> { emitter ->

            val currentTime = System.currentTimeMillis()

            if (emitter.isDisposed) {
                return@create
            }

            /**
             * -i : input
             * -vf : filter_graph set video filters
             * -filter:v : video filter for given parameters - like requested frame times
             * -qscale:v :quality parameter
             * -vsync : drop : This allows to work around any non-monotonic time-stamp errors //not sure how it totally works - if we set it to 0 it skips duplicate frames I guess
             */
            //  ffmpeg -i example_walkaround.mov -c:v libx264 -profile:v baseline -level 3.0 -x264opts keyint=10:min-keyint=10 -g 10 -movflags +faststart+rtphint -maxrate:v 3000k -bufsize:v 3500k walkaround-quick.mp4
            val cmd = arrayOf(
                "-i", inputUri.toString(),
                "-c:v", "libx264",
                "-profile:v", "baseline",
                "-level", "3.0",
                "-x264opts", "keyint=10:min-keyint=10",
                "-g", "10",
                "-movflags", "+faststart+rtphint",
                "-maxrate:v", "${maxrate}k",
                "-bufsize:v", "${bufsize}k",
                "$outputUri"
            )

            task = ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {

                override fun onFailure(result: String?) {
                    loge("FAIL with output : $result")

                    emitter.onError(Throwable(result))
                }

                override fun onSuccess(result: String?) {
                    log("SUCCESS with output : $result")

                    emitter.onNext(MetaData(uri = outputUri, message = "onSuccess: $result"))
                }

                override fun onProgress(progress: String?) {
                    log("progress : $progress")
                    emitter.onNext(MetaData(message = progress))
                }

                override fun onStart() {
                    log("Started command : ffmpeg $cmd")
                    //todo: enable
                    //emitter.onNext(MetaData(message = "Started Command $cmd"))
                }

                override fun onFinish() {
                    log("Finished command : ffmpeg $cmd")
                    emitter.onComplete()
                }
            })

        }.doOnDispose {
            if (task?.killRunningProcess() == false)
                task?.sendQuitSignal()
        }

        return observable
    }

    override fun extractFramesFromVideo(context: Context, frameTimes: List<String>, inputVideo: Uri, id: String, outputDir: Uri?, @IntRange(from = 1, to = 31) photoQuality: Int): Observable<MetaData> {

        val ffmpeg = FFmpeg.getInstance(context)

        var task: FFtask? = null


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
     * https://ffmpeg.org/ffmpeg-all.html
     *
     * https://video.stackexchange.com/a/24684
     *
     * The frames in your H.264 video are grouped into units called [GOP]s (Group Of Pictures). Inside these GOPs frames are classified into three types:
     *
     * [I-frame]: frame that stores the whole picture
     * [P-frame]: frame that stores only the changes between the current picture and previous ones
     * [B-frame]: frame that stores differences with previous or future pictures
     *
     * Additionally, I-frames can be classified as IDR frames and non-IDR frames.
     *
     * The difference is that frames following an IDR frame cannot reference any frame that comes before the IDR frame, while in the case of a non-IDR frame there are no limitations.
     *
     * Every GOP starts with an I-frame, also called a keyframe, but may contain more than one. To create further confusion,
     * a GOP can start with an IDR frame or with a non-IDR frame. This means that frames in the GOP can sometimes refer to previous GOPs (in this case the GOP is said to be "open"),
     * and sometimes not (in this case it's closed).
     *
     * It's common to see the structure of a GOP represented as in this example: [IBBBPBBBPBBBI]. Here the length of the the GOP is 12 frames, with 3 B-frames between each P-frame.
     *
     */
    data class EncodingConfig(

        /**
         * [keyInt] specifies the maximum length of the GOP, so the maximum interval between each keyframe,
         * which remember that can be either an IDR frame or a non-IDR frame.
         * I'm not completely sure but I think that by default ffmpeg will require every I-frame to be an IDR frame,
         * so in practice you can use the terms IDR frame and I-frame interchangeably
         */
        val keyInt: Int = 10,
        /**
         * min-keyint specifies the minimum length of the GOP.
         * This is because the encoder might decide that it makes sense to add a keyframe before the keyint value, so you can put a limit.
         */
        val minKeyInt: Int = 10,

        /**
         * gop size
         */
        val gopValue: Int? = null,

        /**
         * crf
         * Set the quality/size tradeoff for constant-quality (no bitrate target) and constrained-quality (with maximum bitrate target) modes.
         * Valid range is 0 to 63, higher numbers indicating lower quality and smaller output size. Only used if set; by default only the bitrate target is used.
         *
         * 0 for lossless, 23 is default in ffmpeg
         *
         * reasonable value 18
         */
        @IntRange(from = 0, to = 63)
        val videoQuality: Int? = null,
        /**
         * -r Frame rate of the video.
         * https://lists.ffmpeg.org/pipermail/ffmpeg-user/2013-July/016273.html
         */
        val sourceFrameRate: Int? = null,

        /**
         * pix_fmts
         * A ’|’-separated list of pixel format names, such as "pix_fmts=yuv420p|monow|rgb24".
         */
        val pixelFormat: PixelFormat = PixelFormat.yuv420p,

        /**
         * -preset type
         * Configuration preset. This does some automatic settings based on the general type of the image.
         *
         * https://trac.ffmpeg.org/wiki/Encode/H.264
         */
        val preset: Preset? = null,
        /**
         * -c:v Video codec.
         */
        val encoding: Encoding = Encoding.libx264,

        /**
         * https://trac.ffmpeg.org/wiki/Limiting%20the%20output%20bitrate
         * specifies a maximum tolerance. this is only used in conjunction with bufsize
         *
         * reasonable value for full hd = 8 * 1024
         */
        val maxrate: Int? = null,

        /**
         * specifies the decoder buffer size, which determines the variability of the output bitrate
         *
         * reasonable value for full hd = 8 * 1024
         */
        val bufsize: Int? = null
    )

    override fun createVideoFromFrames(context: Context, frameFolder: Uri, outputUri: Uri, config: EncodingConfig, deleteFramesOnComplete: Boolean): Observable<MetaData> {

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

    override fun changeKeyframeInterval() {
    }

    private fun deleteFolder(path: String): Boolean {
        val someDir = File(path)
        return someDir.deleteRecursively()
    }

    override fun deleteAllProcessFiles(): Boolean {
        val localSavePath = "$internalStoragePath/postProcess/"
        return deleteFolder(localSavePath)
    }


    override fun deleteExtractedFrameFolder(folderUri: Uri): Boolean {
        return if (folderUri.path?.contains("postProcess") == true) {
            deleteFolder(folderUri.path!!)
        } else {
            false
        }
    }
}