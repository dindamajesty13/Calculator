package com.majesty.calculator.main

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKeys
import com.majesty.calculator.*
import com.majesty.calculator.adapter.HistoryAdapter
import com.majesty.calculator.database.ArithmeticResultContract
import com.majesty.calculator.database.ArithmeticResultDbHelper
import com.majesty.calculator.databinding.ActivityHistoryBinding
import com.majesty.calculator.model.History
import java.io.File


class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private var dbHelper: ArithmeticResultDbHelper? = null
    private lateinit var historyList: ArrayList<History>
    private lateinit var masterKey: MasterKey

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyGenParameterSpec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MasterKeys.AES256_GCM_SPEC
        } else {
            TODO("VERSION.SDK_INT < M")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            masterKey = MasterKey.Builder(applicationContext)
                .setKeyGenParameterSpec(keyGenParameterSpec)
                .build()
        }

        dbHelper = ArithmeticResultDbHelper(this)

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = getString(R.string.app_name)

        setSupportActionBar(binding.toolbar)

        getAllHistoryData()
    }

    @SuppressLint("Recycle")
    private fun getAllHistoryData() {
        val db = dbHelper!!.readableDatabase
        val cursor = db.rawQuery("select * from " + ArithmeticResultContract.ArithmeticEntry.TABLE_NAME, null)
        historyList = ArrayList()

        if (cursor.moveToFirst()) {
            do {
                val input =
                    cursor.getString(cursor.getColumnIndexOrThrow(ArithmeticResultContract.ArithmeticEntry.COLUMN_NAME_TITLE))
                val result =
                    cursor.getString(cursor.getColumnIndexOrThrow(ArithmeticResultContract.ArithmeticEntry.COLUMN_NAME_SUBTITLE))
                 val image = cursor.getString(cursor.getColumnIndexOrThrow(ArithmeticResultContract.ArithmeticEntry.COLUMN_IMAGE_FILE_NAME))

                val file = File(filesDir, image)
                var bitmap: Bitmap? = null
                var encrypted = false

                if (file.exists()) {
                    val encryptedFile = EncryptedFile.Builder(
                        applicationContext,
                        file,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    bitmap = if (image.contains("UnEncrypted")) {
                        encrypted = false
                        BitmapFactory.decodeStream(file.inputStream())
                    }else {
                        encrypted = true
                        val inputStream = encryptedFile.openFileInput()
                        BitmapFactory.decodeStream(inputStream)
                    }
                }

                val history = History(input, result, bitmap!!, encrypted)
                historyList.add(history)

            } while (cursor.moveToNext())
        }

        db.close()

        binding.rvHistory.isNestedScrollingEnabled = false
        binding.rvHistory.apply {
            // set a LinearLayoutManager to handle Android
            // RecyclerView behavior
            layoutManager = LinearLayoutManager(context)
            // set the custom adapter to the RecyclerView
            adapter = HistoryAdapter(context, historyList)
        }
    }
}