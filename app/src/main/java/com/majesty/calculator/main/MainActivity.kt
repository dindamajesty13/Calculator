package com.majesty.calculator.main

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKeys
import com.bumptech.glide.Glide
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.majesty.calculator.database.ArithmeticResultContract
import com.majesty.calculator.database.ArithmeticResultDbHelper
import com.majesty.calculator.BuildConfig
import com.majesty.calculator.R
import com.majesty.calculator.databinding.ActivityMainBinding
import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.tokenizer.UnknownFunctionOrVariableException
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var latestTmpUri: Uri? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var dbHelper: ArithmeticResultDbHelper? = null
    private var input: String = ""
    private var result: String = ""
    private lateinit var imageUri: Uri
    private var fileStorage: Boolean = false
    private lateinit var masterKey: MasterKey
    private var imageFileName: String = ""
    var progressDialog: ProgressDialog? = null
    private val takeImageResult = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) {
            latestTmpUri?.let { uri ->
                imageUri = uri
                processingImage(uri)
            }
        }
    }
    private val selectImageFromGalleryResult = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            imageUri = uri
            processingImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dbHelper = ArithmeticResultDbHelper(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = getString(R.string.app_name)

        setSupportActionBar(binding.toolbar)

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

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.fileStorage -> {
                    fileStorage = true
                    binding.fab.setOnClickListener {
                        buildAlertDialog("File Storage", "Are you sure want to use file storage?")
                    }
                }
                R.id.databaseStorage -> {
                    fileStorage = false
                    binding.fab.setOnClickListener {
                        buildAlertDialog(
                            "Database Storage",
                            "Are you sure want to use database storage?"
                        )
                    }
                }
            }
        }

        if(binding.fileStorage.isChecked){
            fileStorage = true
            binding.fab.setOnClickListener {
                buildAlertDialog("File Storage", "Are you sure want to use file storage?")
            }
        }else {
            fileStorage = false
            binding.fab.setOnClickListener {
                buildAlertDialog(
                    "Database Storage",
                    "Are you sure want to use database storage?"
                )
            }
        }
    }

    private fun buildAlertDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setIcon(android.R.drawable.ic_dialog_alert)

        builder.setPositiveButton("Yes"){ _, _ ->
            if (BuildConfig.IS_FILESYSTEM){
                if (checkPermission()) selectImageFromGallery() else requestPermission()
            }else {
                onClickRequestPermission()
            }
        }

        builder.setNegativeButton("No"){ _, _ ->
            Toast.makeText(applicationContext,"Cancel input!",Toast.LENGTH_LONG).show()
        }

        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.show()
    }

    @SuppressLint("SimpleDateFormat")
    private fun saveEncryptedImage(imageUri: Uri) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        imageFileName = "Encrypted_$timeStamp"
        val encryptedFile = EncryptedFile.Builder(
            applicationContext,
            File(filesDir, imageFileName),
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        val imageInputStream = contentResolver.openInputStream(imageUri)
        writeFile(encryptedFile.openFileOutput(), imageInputStream)
    }

    @SuppressLint("SimpleDateFormat")
    private fun saveUnencryptedImage(imageUri: Uri) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        imageFileName = "UnEncrypted_$timeStamp"
        val imageInputStream = contentResolver.openInputStream(imageUri)

        writeFile(openFileOutput(imageFileName, Context.MODE_PRIVATE), imageInputStream)
    }

    private fun writeFile(outputStream: FileOutputStream, inputStream: InputStream?) {
        outputStream.use { output ->
            inputStream.use { input ->
                input?.let {
                    val buffer =
                        ByteArray(4 * 1024) // buffer size
                    while (true) {
                        val byteCount = input.read(buffer)
                        if (byteCount < 0) break
                        output.write(buffer, 0, byteCount)
                    }
                    output.flush()
                }
            }
        }
    }

    private fun processingImage(uri: Uri) {
        progressDialog = ProgressDialog(this@MainActivity)
        progressDialog!!.setTitle("Processing Image...")
        progressDialog!!.show()
        Glide.with(applicationContext).load(uri).into(binding.imageView)
        var image: InputImage? = null
        try {
            image = InputImage.fromFilePath(applicationContext, uri)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        image?.let {
            recognizer.process(it)
                .addOnSuccessListener { visionText ->
                    try {
                        input = visionText.textBlocks.first().lines[0].text
                        val output = input.split("[/*\\-+]".toRegex())
                        if (output.size == 2) {
                            binding.textPreview.text = input
                            result =
                                ExpressionBuilder(visionText.textBlocks.first().lines[0].text).build()
                                    .evaluate()
                                    .toString()
                            binding.resultPreview.text = result
                            if (fileStorage) {
                                saveEncryptedImage(imageUri)
                            } else {
                                saveUnencryptedImage(imageUri)
                            }
                            insertToDatabase(input, result, imageFileName)
                        }else {
                            Toast.makeText(
                                applicationContext,
                                "only support very simple 2 argument operations (i.e. 2+2, 3-1, etc)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }catch (e: NoSuchElementException){
                        binding.resultPreview.text =
                            getString(R.string.no_aritmatic_function_found)
                    }catch (unknownException: UnknownFunctionOrVariableException) {
                        binding.resultPreview.text =
                            getString(R.string.no_aritmatic_function_found)
                    } catch (formatException: NumberFormatException) {
                        binding.resultPreview.text = getString(R.string.wrong_number_format)
                    } catch (illegalException: IllegalArgumentException) {
                        binding.resultPreview.text = getString(R.string.wrong_number_format)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun takeImage() {
        lifecycleScope.launchWhenStarted {
            getTmpFileUri().let { uri ->
                latestTmpUri = uri
                takeImageResult.launch(uri)
            }
        }
    }

    private fun selectImageFromGallery() = selectImageFromGalleryResult.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    private fun getTmpFileUri(): Uri {
        val storageDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES)
        val tmpFile = File.createTempFile("tmp_image_file", ".png", storageDir).apply {
            createNewFile()
            deleteOnExit()
        }

        return FileProvider.getUriForFile(
            Objects.requireNonNull(applicationContext),
            BuildConfig.APPLICATION_ID + ".provider", tmpFile)
    }

    private fun requestPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            try {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                val uri = Uri.fromParts("package", this.packageName, null)
                intent.data = uri
                storageActivityResultLauncher.launch(intent)
            }
            catch (e: Exception){
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                storageActivityResultLauncher.launch(intent)
            }
        }
        else{
            //Android is below 11(R)
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }

    private val storageActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            if (Environment.isExternalStorageManager()){
                //Manage External Storage Permission is granted
                selectImageFromGallery()
            }
            else{
                //Manage External Storage Permission is denied....
                Toast.makeText(applicationContext, "Manage External Storage Permission is denied....", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermission(): Boolean{
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            Environment.isExternalStorageManager()
        }
        else{
            //Android is below 11(R)
            val write = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val read = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE){
            if (grantResults.isNotEmpty()){
                //check each permission if granted or not
                val write = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val read = grantResults[1] == PackageManager.PERMISSION_GRANTED
                if (write && read){
                    //External Storage Permission granted
                    selectImageFromGallery()
                }
                else{
                    //External Storage Permission denied...
                    Toast.makeText(applicationContext, "Manage External Storage Permission is denied....", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                takeImage()
            } else {
                onClickRequestPermission()
            }
        }

    private fun onClickRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                takeImage()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.CAMERA
            ) -> {
                requestPermissionLauncher.launch(
                    android.Manifest.permission.CAMERA
                )
            }

            else -> {
                requestPermissionLauncher.launch(
                    android.Manifest.permission.CAMERA
                )
            }
        }
    }

    private fun insertToDatabase(input: String, result: String, imageFileName: String){
        val db = dbHelper!!.writableDatabase

        val values = ContentValues().apply {
            put(ArithmeticResultContract.ArithmeticEntry.COLUMN_NAME_TITLE, input)
            put(ArithmeticResultContract.ArithmeticEntry.COLUMN_NAME_SUBTITLE, result)
            put(ArithmeticResultContract.ArithmeticEntry.COLUMN_IMAGE_FILE_NAME, imageFileName)
        }

        db?.insert(ArithmeticResultContract.ArithmeticEntry.TABLE_NAME, null, values)
        db.close()
        progressDialog!!.dismiss()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.history -> startActivity(Intent(this, HistoryActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }
}