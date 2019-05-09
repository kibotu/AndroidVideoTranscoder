package com.exozet.videoeditor

import android.net.Uri

data class MetaData(
            var progress: String? = null,
            var message: String? = null,
            var uri: Uri? = null
    )