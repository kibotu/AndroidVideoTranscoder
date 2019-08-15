package com.exozet.videoeditor

import android.net.Uri

data class MetaData(
    var progress: Int = 0,
    var message: String? = null,
    var uri: Uri? = null,
    var duration: Long = 0L
)