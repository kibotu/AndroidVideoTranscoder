package com.exozet.videoeditor.demo

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.os.Environment.getExternalStoragePublicDirectory
import androidx.appcompat.app.AppCompatActivity
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.extensions.parseExternalStorageFile
import com.exozet.android.core.extensions.parseFile
import com.exozet.android.core.extensions.show
import com.exozet.videoeditor.FFMpegTranscoder
import com.exozet.videoeditor.ThreadType
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import net.kibotu.logger.LogcatLogger
import net.kibotu.logger.Logger
import net.kibotu.logger.Logger.logv
import net.kibotu.logger.Logger.logw
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    var subscription: CompositeDisposable = CompositeDisposable()

    lateinit var frameUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Logger.addLogger(LogcatLogger())

        checkWriteExternalStoragePermission()
    }

    override fun onDestroy() {
        if (!subscription.isDisposed) {
            subscription.dispose()
        }
        super.onDestroy()
    }

    // region location permission

    protected fun checkWriteExternalStoragePermission() {
        RxPermissions(this)
            .requestEachCombined(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .subscribe({
                if (it.granted)
                    onWritePermissionGranted()
            }, {
                logw { "permission $it" }
            })
            .addTo(subscription)
    }

    protected fun onWritePermissionGranted() {

        val downloadPath = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).absolutePath

        val ffMpegTranscoder = FFMpegTranscoder(this)

        init_ffmpeg.text = "FFmpeg is ${if (ffMpegTranscoder.isSupported(this)) "" else "not"} supported."

        extractFrames(ffMpegTranscoder, downloadPath)

        mergeFrames(ffMpegTranscoder, downloadPath)

        delete_folder.setOnClickListener {
            logv { "delete folder = ${ffMpegTranscoder.deleteExtractedFrameFolder(frameUri)}" }
        }

        delete_all.setOnClickListener {
            logv { "delete all = ${ffMpegTranscoder.deleteAllProcessFiles()}" }
        }
    }

    private fun mergeFrames(ffMpegTranscoder: FFMpegTranscoder, downloadPath: String) {
        make_video.setOnClickListener {

            //            progress.visibility = View.VISIBLE

            ffMpegTranscoder.createVideoFromFrames(
                context = this,
                outputUri = "$downloadPath/output_${System.currentTimeMillis()}.mp4".parseFile(),
                frameFolder = frameUri,
                outputFps = 24,
                threadType = ThreadType.OPTIMAL
            )
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    //                    progress.visibility = View.GONE

                    logv { "createVideoFromFrames $it" }

                }, {
                    logv { "creating video fails ${it.message}" }
                    it.printStackTrace()
                }, { logv { "createVideoFromFrames on complete " } })
                .addTo(subscription)
        }
    }

    private fun extractFrames(ffMpegTranscoder: FFMpegTranscoder, downloadPath: String) {

        val uri = "Download/walkaround.mp4".parseExternalStorageFile()
        logv { "uri=${uri.assetFileExists}" }

        extract_frames.setOnClickListener {

            logv { "extractFramesFromVideo $uri" }

            val increment = 63f / 360f

            val times = (0..360).map {
                increment * it.toDouble()
            }

            output.text = ""

            ffMpegTranscoder.extractFramesFromVideo(this, uri, "11113", 5, times.map { it.toString() }, output = downloadPath)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    extract_frames_progress.show()

                    logv { "extract frames $it" }

                    output.text = "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${output.text}"

                    if (it.uri?.toString().isNotNullOrEmpty()) {
                        frameUri = it.uri!!
                    }

                    extract_frames_progress.progress = it.progress

                }, {
                    logv { "extracting frames fail ${it.message}" }

                    it.printStackTrace()
                }, {
                    logv { "extractFramesFromVideo on complete" }
                })
                .addTo(subscription)
        }
    }

    // endregion

    val times = listOf(
        "0.002",
        "0.102",
        "0.202",
        "0.502",
        "0.702",
        "0.752",
        "0.802",
        "0.852",
        "0.902",
        "0.952",
        "0.982",
        "1.002",
        "1.102",
        "1.202",
        "1.502",
        "1.702",
        "1.752",
        "1.802",
        "1.852",
        "1.902",
        "1.952",
        "1.982",
        "2.002",
        "2.102",
        "2.202",
        "2.502",
        "2.702",
        "2.752",
        "2.802",
        "2.852",
        "2.902",
        "2.952",
        "2.982",
        "3.002",
        "3.102",
        "3.202",
        "3.502",
        "3.702",
        "3.752",
        "3.802",
        "3.852",
        "3.902",
        "3.952",
        "3.982",
        "4.002",
        "4.102",
        "4.202",
        "4.502",
        "4.702",
        "4.752",
        "4.802",
        "4.852",
        "4.902",
        "4.952",
        "4.982",
        "5.002",
        "5.102",
        "5.202",
        "5.502",
        "5.702",
        "5.752",
        "5.802",
        "5.852",
        "5.902",
        "5.952",
        "5.982",
        "6.002",
        "6.102",
        "6.202",
        "6.502",
        "6.702",
        "6.752",
        "6.802",
        "6.852",
        "6.902",
        "6.952",
        "6.982",
        "7.002",
        "7.102",
        "7.202",
        "7.502",
        "7.702",
        "7.752",
        "7.802",
        "7.852",
        "7.902",
        "7.952",
        "7.982",
        "8.002",
        "8.102",
        "8.202",
        "8.502",
        "8.702",
        "8.752",
        "8.802",
        "8.852",
        "8.902",
        "8.952",
        "8.982",
        "9.002",
        "9.102",
        "9.202",
        "9.502",
        "9.702",
        "9.752",
        "9.802",
        "9.852",
        "9.902",
        "9.952",
        "9.982",
        "10.002",
        "10.102",
        "10.202",
        "10.502",
        "10.702",
        "10.752",
        "10.802",
        "10.852",
        "10.902",
        "10.952",
        "10.982",
        "11.002",
        "11.102",
        "11.202",
        "11.502",
        "11.702",
        "11.752",
        "11.802",
        "11.852",
        "11.902",
        "11.952",
        "11.982",
        "12.002",
        "12.102",
        "12.202",
        "12.502",
        "12.702",
        "12.752",
        "12.802",
        "12.852",
        "12.902",
        "12.952",
        "12.982",
        "13.002",
        "13.102",
        "13.202",
        "13.502",
        "13.702",
        "13.752",
        "13.802",
        "13.852",
        "13.902",
        "13.952",
        "13.982",
        "14.002",
        "14.102",
        "14.202",
        "14.502",
        "14.702",
        "14.752",
        "14.802",
        "14.852",
        "14.902",
        "14.952",
        "14.982",
        "15.002",
        "15.102",
        "15.202",
        "15.502",
        "15.702",
        "15.752",
        "15.802",
        "15.852",
        "15.902",
        "15.952",
        "15.982",
        "16.002",
        "16.102",
        "16.202",
        "16.502",
        "16.702",
        "16.752",
        "16.802",
        "16.852",
        "16.902",
        "16.952",
        "16.982",
        "17.002",
        "17.102",
        "17.202",
        "17.502",
        "17.702",
        "17.752",
        "17.802",
        "17.852",
        "17.902",
        "17.952",
        "17.982",
        "18.002",
        "18.102",
        "18.202",
        "18.502",
        "18.702",
        "18.752",
        "18.802",
        "18.852",
        "18.902",
        "18.952",
        "18.982"
    )

    val smallTimes = listOf(
        "0.002",
        "0.102",
        "0.202",
        "0.502",
        "0.702",
        "0.752",
        "0.802",
        "0.852",
        "0.902",
        "0.952",
        "0.982",
        "1.002",
        "1.102",
        "1.202",
        "1.502",
        "1.702",
        "1.752",
        "1.802",
        "1.852",
        "1.902",
        "1.952",
        "1.982",
        "2.002",
        "2.102",
        "2.202",
        "2.502",
        "2.702",
        "2.752",
        "2.802",
        "2.852",
        "2.902",
        "2.952",
        "2.982",
        "3.002",
        "3.102",
        "3.202",
        "3.502",
        "3.702",
        "3.752",
        "3.802",
        "3.852",
        "3.902",
        "3.952",
        "3.982",
        "4.002",
        "4.102",
        "4.202",
        "4.502",
        "4.702",
        "4.752",
        "4.802",
        "4.852",
        "4.902",
        "4.952",
        "4.982",
        "5.002",
        "5.102",
        "5.202",
        "5.502",
        "5.702",
        "5.752",
        "5.802",
        "5.852",
        "5.902",
        "5.952",
        "5.982"
    )

    val Uri.assetFileExists: Boolean
        get() {
            val mg = resources.assets
            var `is`: InputStream? = null
            return try {
                `is` = mg.open(this.toString())
                true
            } catch (ex: IOException) {
                false
            } finally {
                `is`?.close()
            }
        }
}