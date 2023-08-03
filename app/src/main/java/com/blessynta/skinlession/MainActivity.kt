package com.blessynta.skinlession

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.blessynta.skinlession.api.ApiConfig
import com.blessynta.skinlession.api.ApiService
import com.blessynta.skinlession.response.PredictResponse
import com.bumptech.glide.Glide
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.w3c.dom.Text
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var apiService: ApiService
    private lateinit var imageView: ImageView
    private lateinit var confidenceTextView: TextView
    private lateinit var predictionTextView: TextView
    private lateinit var selectImageButton: Button
    private lateinit var captureImageButton: Button
    private lateinit var uploadImageButton: Button
    private lateinit var finishButton: Button
    private lateinit var tvDescription: TextView
    private lateinit var tvReason: TextView
    private lateinit var tvDisease: TextView
    private lateinit var tvDescriptionBold: TextView
    private lateinit var tvReasonBold: TextView
    private lateinit var tvDiseaseBold: TextView

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val IMAGE_CAPTURE_REQUEST_CODE = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiService = ApiConfig.getPredictApiService()
        imageView = findViewById(R.id.image_view)
        confidenceTextView = findViewById(R.id.confidenceTextView)
        predictionTextView = findViewById(R.id.predictionTextView)
        selectImageButton = findViewById(R.id.button_select_image)
        captureImageButton = findViewById(R.id.button_capture_image)
        uploadImageButton = findViewById(R.id.button_upload_image)
        finishButton = findViewById(R.id.button_finish)
        tvDescription = findViewById(R.id.tv_description)
        tvReason = findViewById(R.id.tv_reason)
        tvDisease = findViewById(R.id.disease)
        tvDiseaseBold = findViewById(R.id.disease_bold)
        tvReasonBold = findViewById(R.id.reason_bold)
        tvDescriptionBold = findViewById(R.id.tv_description_bold)

        selectImageButton.setOnClickListener {
            checkAndRequestPermissions()
        }

        captureImageButton.setOnClickListener {
            checkAndRequestCameraPermission()
        }


        uploadImageButton.setOnClickListener {
            uploadImageAndMakeRequest()
        }

        finishButton.setOnClickListener{
            doneDetection()
        }
    }

    private fun doneDetection() {
        selectImageButton.visibility = View.VISIBLE
        captureImageButton.visibility = View.VISIBLE
        uploadImageButton.visibility = View.VISIBLE
        finishButton.visibility = View.GONE
        tvDescriptionBold.visibility = View.GONE
        tvDiseaseBold.visibility = View.GONE
        tvReasonBold.visibility = View.GONE
        confidenceTextView.text = ""
        predictionTextView.text = ""
        tvDescription.text = ""
        tvReason.text = ""
        tvDisease.text = ""

    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val grantedPermissions = mutableListOf<String>()

        for (permission in requiredPermissions) {
            val permissionResult = ContextCompat.checkSelfPermission(this, permission)
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                grantedPermissions.add(permission)
            }
        }

        if (grantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                grantedPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            openImagePicker()
        }
    }

    private fun checkAndRequestCameraPermission() {
        val cameraPermission = Manifest.permission.CAMERA
        val cameraPermissionResult = ContextCompat.checkSelfPermission(this, cameraPermission)
        if (cameraPermissionResult != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(cameraPermission),
                PERMISSION_REQUEST_CODE
            )
        } else {
            openCamera()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_CAPTURE_REQUEST_CODE)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, IMAGE_CAPTURE_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                when (permissions[0]) {
                    Manifest.permission.READ_EXTERNAL_STORAGE -> openImagePicker()
                    Manifest.permission.CAMERA -> openCamera()
                }
            } else {
                showToast("Permission Denied")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK) {
            val thumbnailBitmap = data?.extras?.get("data") as? Bitmap
            val bitmap = thumbnailBitmap ?: getBitmapFromUri(data?.data)
            bitmap?.let {
                displaySelectedImage(bitmap)
            } ?: showToast("Failed to load image")
        }
    }

    private fun getBitmapFromUri(uri: android.net.Uri?): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri!!)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun displaySelectedImage(bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
    }

    private fun uploadImageAndMakeRequest() {
        val bitmap = imageView.drawable.toBitmap()
        val imageFile = bitmapToFile(bitmap)
        val requestBody = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestBody)

        val call: Call<PredictResponse> = apiService.predict(imagePart)
        call.enqueue(object : Callback<PredictResponse> {
            override fun onResponse(
                call: Call<PredictResponse>,
                response: Response<PredictResponse>
            ) {
                if (response.isSuccessful) {
                    val predictResponse = response.body()
                    // Process the predictResponse
                    handlePredictResponse(predictResponse)
                } else {
                    // Handle error response
                    showToast("Error: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<PredictResponse>, t: Throwable) {
                // Handle network or API call failure
                Log.e("","API Call Failed: ${t.message}")
            }
        })
    }

    // Function to apply bold style to the specified text
    fun setBoldText(textView: TextView, text: String) {
        val spannableStringBuilder = SpannableStringBuilder(text)
        spannableStringBuilder.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            text.length,
            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        textView.text = spannableStringBuilder
    }



    private fun handlePredictResponse(predictResponse: PredictResponse?) {
        if (predictResponse != null) {
            val confidenceCnn = predictResponse.confidenceCnn
            val predictionCnn = predictResponse.predictionCnn

            confidenceTextView.text = "Tingkat Kemiripan: $confidenceCnn"

            when (predictionCnn) {
                "Vesikular" -> {
                    tvDescription.text = "Peninggian yang berisi cairan dengan ukuran kurang dari atau sama dengan 0,5 cm. Lesi ini dapat tersusun secara linear, bergerombol atau tersebar."
                    tvReason.text = "-\tEksim atau Dermatitis\n" +
                            "-\tVirus varicella-zoster\n" +
                            "-\tInfeksi pada mulut, tangan dan kaki\n" +
                            "-\tInfeksi Fungal dan Bakteri\n" +
                            "-\tLuka bakar\n"
                    tvDisease.text = "-\tRuam Vesikular\n" +
                            "-\tCacar air (Chickenpox)\n" +
                            "-\tCacar ular (Shingles)\n" +
                            "-\tHerpes Simplex\n"
                }
                "Papula" -> {
                    tvDescription.text = "Peninggian padat  berdiameter kurang dari 0,5 cm dengan permukaan yang berbentuk bulat atau flat-topped. Warnanya bervariasi, seperti merah, kekuningan, putih dan hitam."
                    tvReason.text = "-\tInfeksi kulit\n" +
                            "-\tInfeksi jamur\n" +
                            "-\tVirus varicella-zoster\n" +
                            "-\tEksim atau Dermatitis\n"
                    tvDisease.text = "-\tCacar air\n" +
                            "-\tJerawat Papula\n" +
                            "-\tHerpes Zoster\n"
                }
                "Urtikaria" -> {
                    tvDescription.text = "Berwarna merah muda hingga kemerahan yang dikelilingi oleh makula eritem yang peninggiannya berbentuk seperti plateau, edema dan bersifat mudah pudar atau menghilang dengan ukuran yang bervariasi."
                    tvReason.text = "-\tAlergi (obat-obatan, tanaman, makanan)\n" +
                            "-\tTungau\n" +
                            "-\tGigitan serangga\n" +
                            "-\tStres\n" +
                            "-\tPakaian ketat\n" +
                            "-\tOlahraga (keringat)\n"
                    tvDisease.text = "-\tBiduran/Hives\n"
                }
                else -> {
                    tvDescription.text = "Perubahan warna tanpa peninggian pada kulit yang dapat berbentuk oval, bulat atau ireguler"
                    tvReason.text = "-\tHiperpigmentasi\n" +
                            "-\tHipopigmentasi\n" +
                            "-\tDepigmentasi\n"
                    tvDisease.text = "-\tMelasma\n" +
                            "-\tVitiligo\n" +
                            "-\tTinea Vercolor (Panu)\n" +
                            "-\tBintik matahari (Freckles)\n" +
                            "-\tBintik penuaan\n" +
                            "-\tBekas luka\n"
                }
            }

            predictionTextView.text = "Jenis: $predictionCnn"

            captureImageButton.visibility = View.GONE
            selectImageButton.visibility = View.GONE
            uploadImageButton.visibility = View.GONE
            finishButton.visibility = View.VISIBLE
            tvDescriptionBold.visibility = View.VISIBLE
            tvDiseaseBold.visibility = View.VISIBLE
            tvReasonBold.visibility = View.VISIBLE

            showToast("Terdeteksi")
        } else {
            showToast("Error: Empty response")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun bitmapToFile(bitmap: Bitmap): File {
        val file = File(cacheDir, "temp_image.jpg")
        file.createNewFile()

        // Convert bitmap to byte array
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        // Write byte array to file
        val fileOutputStream = FileOutputStream(file)
        fileOutputStream.write(byteArray)
        fileOutputStream.flush()
        fileOutputStream.close()

        return file
    }
}