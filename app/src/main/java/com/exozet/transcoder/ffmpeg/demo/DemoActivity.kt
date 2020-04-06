package com.exozet.transcoder.ffmpeg.demo

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.exozet.android.core.extensions.onClick
import com.exozet.android.core.extensions.parseExternalStorageFile
import com.exozet.transcoder.ffmpeg.Progress
import com.exozet.transcoder.mcvideoeditor.MediaCodecTranscoder
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_demo.*
import net.kibotu.logger.LogcatLogger
import net.kibotu.logger.Logger
import net.kibotu.logger.Logger.logv

class DemoActivity : FragmentActivity() {

    var subscription: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscription = CompositeDisposable()
        setContentView(R.layout.activity_demo)

        Logger.addLogger(LogcatLogger())

        val frameFolder = "Download/process/".parseExternalStorageFile()
        val inputVideo = "Download/walkaround.mp4".parseExternalStorageFile()
        val outputVideo = "Download/output_${System.currentTimeMillis()}.mp4".parseExternalStorageFile()

        val increment = 63f / 120f
        val times = (0..120).map {
            increment * it.toDouble()
        }

        extract_frames.onClick {

            var progress: Progress? = null

            MediaCodecTranscoder.extractFramesFromVideo(
                context = this,
                frameTimes = times,
                inputVideo = inputVideo,
                id = "loremipsum",
                outputDir = frameFolder,
                photoQuality = 100
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        logv { "extractFramesFromVideo onNext $it" }
                        progress = it

                        extract_frames_progress.progress = it.progress
                    },
                    {
                        logv { "extractFramesFromVideo onError ${it.localizedMessage}" }
                    },
                    { logv { "extractFramesFromVideo onComplete $progress" } }
                ).addTo(subscription)
        }

        merge_frames.onClick {

            var progress: Progress? = null

            MediaCodecTranscoder.createVideoFromFrames(
                frameFolder = frameFolder,
                outputUri = outputVideo,
                deleteFramesOnComplete = true
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        logv { "createVideoFromFrames onNext $it" }
                        progress = it

                        merge_frames_progress.progress = it.progress
                    },
                    {
                        logv { "createVideoFromFrames onError ${it.localizedMessage}" }
                    },
                    { logv { "createVideoFromFrames onComplete $progress" } }
                ).addTo(subscription)
        }

        cancel.onClick {
            dispose()
        }

        (0 until 10).toList().subList(0,10)
    }

    override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    private fun dispose() {
        if (!subscription.isDisposed)
            subscription.dispose()
        subscription = CompositeDisposable()
    }
}