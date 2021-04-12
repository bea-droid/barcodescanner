package com.scan.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.scan.barcodescanner.databinding.ActivityMainBinding
import java.util.concurrent.Executors

private const val CAMERA_PERMISSION_REQUEST_CODE = 1

@ExperimentalGetImage
class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    if (hasCameraPermission()) bindCameraUseCases() else requestPermission()
  }

  private fun hasCameraPermission() =
    ActivityCompat.checkSelfPermission(
      this,
      Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

  private fun requestPermission(){
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      bindCameraUseCases()
    } else {
      Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
    }

    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  private fun bindCameraUseCases() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

    cameraProviderFuture.addListener({
      val cameraProvider = cameraProviderFuture.get()

      // setting up the preview use case
      val previewUseCase = Preview.Builder()
        .build()
        .also {
          it.setSurfaceProvider(binding.cameraView.surfaceProvider)
        }

      // configure our MLKit BarcodeScanning client
      val options = BarcodeScannerOptions.Builder().setBarcodeFormats(
        Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_CODE_39,
        Barcode.FORMAT_CODE_93,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_QR_CODE,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_PDF417
      ).build()
      val scanner = BarcodeScanning.getClient(options)

      // setting up the analysis use case
      val analysisUseCase = ImageAnalysis.Builder()
        .build()

      // define the actual functionality of our analysis use case
      analysisUseCase.setAnalyzer(
        Executors.newSingleThreadExecutor(),
        { imageProxy ->
          processImageProxy(scanner, imageProxy)
        }
      )

      // configure to use the back camera
      val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

      try {
        cameraProvider.unbindAll() // try unbinding before we bind our new use cases
        cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase, analysisUseCase)
      } catch (illegalStateException: IllegalStateException) {
        Log.e(TAG, illegalStateException.message.orEmpty())
      } catch (illegalArgumentException: IllegalArgumentException) {
        Log.e(TAG, illegalArgumentException.message.orEmpty())
      }
    }, ContextCompat.getMainExecutor(this))
  }

  private fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy
  ) {

    imageProxy.image?.let { image ->
      val inputImage =
        InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)

      barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodeList ->
          val barcode = barcodeList.getOrNull(0)
          barcode?.rawValue?.let { value ->
              binding.bottomText.text = getString(R.string.barcode_value, value)
          }
        }
        .addOnFailureListener {
          // This failure will happen if the barcode scanning model fails to download from Google Play Services
          Log.e(TAG, it.message.orEmpty())
        }.addOnCompleteListener {
          // When the image is from CameraX analysis use case, must call image.close() on received
          // images when finished using them. Otherwise, new images may not be received or the camera
          // may stall.
          imageProxy.image?.close()
          imageProxy.close()
        }
    }
  }

  companion object {
    val TAG: String = MainActivity::class.java.simpleName
  }
}