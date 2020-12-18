package com.saloavalos.mimemorama

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.saloavalos.mimemorama.models.BoardSize
import com.saloavalos.mimemorama.utils.*
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CreateActivity"
        // Request code used for intent to select multiplem images
        private const val PICK_PHOTO_CODE = 88
        private const val READ_EXTERNAL_PHOTOS_CODE = 54
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14
    }

    private lateinit var rv_ImagePicker: RecyclerView
    private lateinit var et_GameName: EditText
    private lateinit var btn_Save: Button
    private lateinit var pb_Uploading: ProgressBar

    private lateinit var adapter: ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1
    // save the resource url, to know where is the image that is located on the phone (a list)
    private val chosenImageUris = mutableListOf<Uri>()
    private val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        // Hooks
        rv_ImagePicker = findViewById(R.id.rv_ImagePicker)
        et_GameName = findViewById(R.id.et_GameName)
        btn_Save = findViewById(R.id.btn_Save)
        pb_Uploading = findViewById(R.id.pb_Uploading)

        // to add a back button at the top left
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        // ? is to only call title attribute if supportActionBar is not null
        supportActionBar?.title = "Choose pics (0 / ${numImagesRequired})"

        btn_Save.setOnClickListener{
            saveDataToFirebase()
        }

        // restrict the maximum length to 14 characters in the input
        et_GameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        et_GameName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // every time the user makes changes to the EditText we check if we should enable the button
                btn_Save.isEnabled = shouldEnableSelected()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        adapter = ImagePickerAdapter(this, chosenImageUris, boardSize, object: ImagePickerAdapter.ImageClickListener {
            override fun onPlaceholderClicked() {
                if (isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)) {
                    launchIntentForPhotos()
                } else {
                    requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)
                }
            }

        })
        rv_ImagePicker.adapter = adapter
        rv_ImagePicker.setHasFixedSize(true)
        rv_ImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        if (requestCode == READ_EXTERNAL_PHOTOS_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchIntentForPhotos()
            } else {
                Toast.makeText(this, "In order to create a custom game, you need to provide access to your photos", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // in all three of these cases we should log a warning and return early ()
        if (requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Did not get data back from the launched activity, user likely cancel flow")
            return
        }
        // to get just 1 photo
        val selectedUri = data.data
        // to get multiple photos
        val clipData = data.clipData
        if (clipData != null) {
            Log.i(TAG, "clipData numImages ${clipData.itemCount}: $clipData ")
            for (i in 0 until clipData.itemCount) {
                val clipItem = clipData.getItemAt(i)
                if (chosenImageUris.size < numImagesRequired) {
                    chosenImageUris.add(clipItem.uri)
                }
            }
        } else if (selectedUri != null) {
            Log.i(TAG, "data: ${selectedUri}")
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Chooose pic (${chosenImageUris.size} / $numImagesRequired)"
        btn_Save.isEnabled = shouldEnableSelected()
    }

    private fun saveDataToFirebase() {
        Log.i(TAG, "saveDataToFirebase")
        // Disable this btn so the user doesn't try to click it again
        btn_Save.isEnabled = false
        val customGameName = et_GameName.text.toString().trim()
        // Check that we're not overwriting someone else's data
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if (document != null && document.data != null) {
                AlertDialog.Builder(this)
                    .setTitle("Name taken")
                    .setMessage("A game already exists with the name '$customGameName'. Please choose another")
                    .setPositiveButton("OK", null)
                    .show()
                // if we get a failure, we enable the btn again
                btn_Save.isEnabled = true
            } else {
                handleImageUploading(customGameName)
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Encountered error while saving memory game", exception)
            Toast.makeText(this, "Encountered error while saving memory game", Toast.LENGTH_SHORT).show()
            btn_Save.isEnabled = true
        }
    }

    private fun handleImageUploading(gameName: String) {
        pb_Uploading.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()
        for ((index, photoUri) in chosenImageUris.withIndex()) {
            // get image with reduced size
            val imageByteArray = getImageByteArray(photoUri)
            // ex: images/spider-memorama/1604578426354-1.jpg
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            // where we are gonna save this photo in the db
            val photoReference = storage.reference.child(filePath)
            // upload the image
            photoReference.putBytes(imageByteArray)
                .continueWithTask { photoUploadTask ->
                    Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                    // get the download url
                    photoReference.downloadUrl
                }.addOnCompleteListener { downloadUrlTask ->
                    // if it didn't succeed
                    if (!downloadUrlTask.isSuccessful) {
                        Log.e(TAG, "Exception with Firebase storage", downloadUrlTask.exception)
                        Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                        didEncounterError = true
                        return@addOnCompleteListener
                    }
                    // if some other image has failed to upload
                    if (didEncounterError) {
                        pb_Uploading.visibility = View.GONE
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downloadUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    // Update the progress of the progress bar, it calculates how many left from the total
                    pb_Uploading.progress = uploadedImageUrls.size * 100 / chosenImageUris.size
                    Log.i(TAG, "Finished uploading $photoUri, num uploaded ${uploadedImageUrls.size} ")
                    // To know when all the images have been uploaded
                    // if the size of uploaded image urls is equal to the number of images that the user has picked
                    if (uploadedImageUrls.size == chosenImageUris.size) {
                        handleAllImagesUploaded(gameName, uploadedImageUrls)
                    }
                }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        db.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener{ gameCreationTask ->
                pb_Uploading.visibility = View.GONE
                if (!gameCreationTask.isSuccessful) {
                    Log.e(TAG, "Exception with game creation", gameCreationTask.exception)
                    Toast.makeText(this, "Failed game creation", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                Log.i(TAG, "Successfully created game")
                // show an alert dialog to communicate the game was created, and navigate to the main activity
                AlertDialog.Builder(this)
                    .setTitle("Upload complete! Let's play your game '$gameName'")
                    .setPositiveButton("OK") { _, _ ->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }.show()
            }
    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        // is at least Android 9 Pie
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        Log.i(TAG, "Original width ${originalBitmap.width} and  height ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)
        Log.i(TAG, "Scaled width ${scaledBitmap.width} and  height ${scaledBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        // 60 is the compression quality of the image
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    private fun shouldEnableSelected(): Boolean {
        // Check if we should enable the save button or not
        // if user hasn't picked enough images for the memory game size
        if (chosenImageUris.size != numImagesRequired) {
            return false
        }
        // if input is blank or too short
        if (et_GameName.text.isBlank() || et_GameName.text.length < MIN_GAME_NAME_LENGTH) {
            return false
        }
        return true
    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        // Specify what kind of files we are interesting in
        intent.type = "image/*"
        // if the app where the user want to select the images from allow to select multiple images, we want to him to select multiple images
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        // "Choose pics" is the title that shows up when the user has multiple apps on their phone that can service this request
        startActivityForResult(Intent.createChooser(intent, "Choose pics"), PICK_PHOTO_CODE)
    }
}
