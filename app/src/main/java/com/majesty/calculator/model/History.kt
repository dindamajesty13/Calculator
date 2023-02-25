package com.majesty.calculator.model

import android.graphics.Bitmap

class History internal constructor(
    var input: String,
    var result: String,
    var image: Bitmap,
    var encrypted: Boolean
)