package com.majesty.calculator.database

import android.provider.BaseColumns

object ArithmeticResultContract {
    object ArithmeticEntry : BaseColumns {
        const val TABLE_NAME = "arithmetic"
        const val COLUMN_NAME_TITLE = "input"
        const val COLUMN_NAME_SUBTITLE = "result"
        const val COLUMN_IMAGE_FILE_NAME = "image"
    }
}