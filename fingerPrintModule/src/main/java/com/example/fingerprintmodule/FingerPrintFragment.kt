package com.example.fingerprintmodule

import ai.tech5.sdk.abis.T5AirSnap.CaptureStatus
import ai.tech5.sdk.abis.T5AirSnap.NistPosCode
import ai.tech5.sdk.abis.T5AirSnap.SgmRectImage
import ai.tech5.sdk.abis.T5AirSnap.StandardErrorCodes
import ai.tech5.sdk.abis.T5AirSnap.T5AirSnap
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.fingerprintmodule.databinding.FragmentFingerPrintBinding
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.atan


class FingerPrintFragment : Fragment() {
    //    private var param1: String? = null
//    private var param2: String? = null
    private var _binding: FragmentFingerPrintBinding? = null
    private val binding get() = _binding!!
    private var t5AirSnap: T5AirSnap? = null
    private var camera: Camera? = null
    private var positionCode:Int = setPositionCodeIndex(0)
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
//            param1 = it.getString(ARG_PARAM1)
//            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentFingerPrintBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setScreenBrightnessFull()

        if (allPermissionsGranted()) {
            initScanningSdk()
        } else {
            requestPermissions()
        }
        initScanningSdk()

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle Permission granted/rejected
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                requireContext(), "Permission request denied", Toast.LENGTH_SHORT
            ).show()
        } else {
            initScanningSdk()
        }
    }

    private fun initScanningSdk() {
        t5AirSnap = T5AirSnap(requireContext())
        createCacheFile()


        t5AirSnap?.apply {
            val cacheFile = File(requireContext().cacheDir, cacheFileName)
            setCacheDir(cacheFile.absolutePath)
            setCaptureId("replace")//TODO 1
            setDeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE)
            setSaveSdkLogFlag(false)

            val resultCode = initSdk(developersToken)



            if (resultCode != StandardErrorCodes.SE_OK) {
                t5AirSnap = null
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                return
            }

            setPositionCode(positionCode)//TODO 2
            setLivenessCheck(false)
            setOrientationCheck(false)
            setDetectorThreshold(0.9f)
            setSaveFramesFlag(true)
            setPropDenoiseFlag(false)
            Toast.makeText(requireContext(), "yes sir", Toast.LENGTH_LONG).show()

            binding.viewFinder.post {
                startCamera()
            }
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            val rotation = binding.viewFinder.display.rotation
            // Preview
            val preview =
                Preview.Builder().setTargetResolution(Size(1080, 1920)).setTargetRotation(rotation)
                    .build().also {
                        it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                    }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA


            val imageAnalyzer =
                ImageAnalysis.Builder().setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(1080, 1920)).setTargetRotation(rotation).build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FingerAnalyzer(this))
                    }



            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

                val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                for (cameraId in manager.cameraIdList) {
                    //CameraCharacteristics characteristics
                    val mCameraInfo = manager.getCameraCharacteristics(cameraId)

                    // We don't use a front facing camera in this sample.
                    val facing = mCameraInfo.get(CameraCharacteristics.LENS_FACING)
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue
                    }
                    val exposureCompensationRange =
                        mCameraInfo.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)!!
                    val exposureCompensation = exposureCompensationRange.lower / 2
                    val cameraControl: CameraControl = camera!!.cameraControl
                    cameraControl.setExposureCompensationIndex(exposureCompensation)
                    var zoomRatio = 1.0f
                    val cameraInfo: CameraInfo = camera!!.cameraInfo
                    val zoomState = cameraInfo.zoomState.value
                    if (zoomState != null) {
                        zoomRatio = zoomState.maxZoomRatio


                        /*
                    float targetZoomRatio = ((m_positionCode == NistPosCode.POS_CODE_PL_R_4F) ||
                                             (m_positionCode == NistPosCode.POS_CODE_PL_L_4F) ||
                                             (m_positionCode == NistPosCode.POS_CODE_L_AND_R_THUMBS)) ?
                                            1.5f : 2.0f;
                    */

                        // ph2
                        val targetZoomRatio = 2.0f
                        if (zoomRatio > targetZoomRatio) zoomRatio =
                            targetZoomRatio else if (zoomRatio < 1.0f) zoomRatio = 1.0f
                    }

                    cameraControl.setZoomRatio(zoomRatio)
                    val sensorSize =
                        mCameraInfo.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) //SENSOR_INFO_PHYSICAL_SIZE
                    val sensor = mCameraInfo.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)


                    // Since sensor size doesn't actually match capture size and because it is
                    // reporting an extremely wide aspect ratio, this FoV is bogus
                    val focalLengths =
                        mCameraInfo.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (focalLengths!!.isNotEmpty()) {
                        val focalLength = focalLengths[0]
                        val fov = 2 * atan((sensorSize!!.width / (2 * focalLength)).toDouble())
                    }
                    t5AirSnap?.initCameraParameters(
                        sensorSize!!.width, sensorSize.height, focalLengths[0], zoomRatio
                    )
                    break
                }

                autoFocus()
                toggleFlash(true)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

            @SuppressLint("RestrictedApi") val size: Size? =
                imageAnalyzer.attachedSurfaceResolution



                initBorder(size?.height, size?.width)

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun initBorder(width: Int?, height: Int?) {
        try {
            val bitmap = Bitmap.createBitmap(
                binding.ivTransparentView.width,
                binding.ivTransparentView.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.FILL
            canvas.drawColor(resources.getColor(R.color.colorPrimaryTrans))
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
           t5AirSnap!!.initBorder(
                width!!, height!!,
                binding.graphicOverlay.width, binding.graphicOverlay.height
            )


            val borderRectLeft:java.lang.Integer = Integer(0)
            val borderRectTop :java.lang.Integer = Integer(0)
            val borderRectRight : java.lang.Integer = Integer(0)
            val borderRectBottom :java.lang.Integer = Integer(0)



            t5AirSnap!!.getBorderRectangle(
                borderRectLeft as Int, borderRectTop as Int,
                borderRectRight as Int, borderRectBottom as Int
            )
            val borderRect = Rect(
                borderRectLeft, borderRectTop,
                borderRectRight, borderRectBottom
            )
            binding.graphicOverlay.init(borderRect)

            canvas.drawRect(borderRect, paint)
            binding.ivTransparentView.setImageBitmap(bitmap)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    internal class FingerAnalyzer(private val fingerPrintFragment: FingerPrintFragment) : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            fingerPrintFragment.analyzeImage(imageProxy)
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun analyzeImage(imageProxy: ImageProxy) {
        try {

            val currentTime = System.currentTimeMillis()
            val previewImage = imageProxy.image
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rects = ArrayList<SgmRectImage>()
            val captureStatus: Int = t5AirSnap!!.analyzeImage(
                previewImage, rotationDegrees, rects
            )
            if (!showEllipses) {
                rects.clear()
            }
            if (captureStatus == CaptureStatus.bestFrameChosen) {

                cameraProvider?.unbindAll()

                activity?.runOnUiThread {
                    binding.graphicOverlay.drawBorderAndBoundBoxes(Color.GREEN, rects)
                    Toast.makeText(requireContext(), "Loading", Toast.LENGTH_LONG).show()
                }

                setStatus("")
                var result = StandardErrorCodes.SE_OK
                val reverseFlag = false/*
                result = m_cellSdk.checkReverse(reverseFlag);

                Logger.addToLog(TAG, "Reverse check result: " + result +
                                     ", reverse flag: " + reverseFlag, m_logFile);

                if ((result == SE_OK) && reverseFlag)
                {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Reversed", Toast.LENGTH_LONG).show();
                    });
                }

                if ((m_positionCode == NistPosCode.POS_CODE_R_THUMB) ||
                    (m_positionCode == NistPosCode.POS_CODE_L_THUMB))
                {
                    Boolean wrongThumb = new Boolean(false);

                    result = m_cellSdk.checkThumb(wrongThumb);

                    Logger.addToLog(TAG, "Thumb check result: " + result +
                                    ", wrong thumb flag: " + wrongThumb, m_logFile);

                    if ((result == SE_OK) && wrongThumb)
                    {
                        runOnUiThread(() -> {
                            Toast.makeText(this,
                                           (m_positionCode == NistPosCode.POS_CODE_R_THUMB) ?
                                           "Wrong thumb (left instead of right)" :
                                           "Wrong thumb (right instead of left)",
                                           Toast.LENGTH_LONG).show();
                        });
                    }
                }
*/
                val previewWidth = previewImage!!.width
                val previewHeight = previewImage.height
                val previewImageBuffer = ByteArray(previewWidth * previewHeight)
                val segmentedRects = ArrayList<SgmRectImage>()
                val livenessScore = FloatArray(4)
                for (i in 0..3) livenessScore[i] = 0.0f
                result = t5AirSnap!!.getSegmentedFingers(
                    previewImageBuffer, 0, 0, segmentedRects, livenessScore
                )
                var msg = "Segmentation result: $result, liveness score: "
                for (i in 0 until if (segmentedRects.size > 4) 4 else segmentedRects.size) msg =
                    msg + " #[" + i + "] : " + livenessScore[i]

                if (livenessCheck) {
                    val livenessScoreThreshold = 0.5f
                    for (i in 0 until if (segmentedRects.size > 4) 4 else segmentedRects.size) {
                        if (livenessScore[i] < livenessScoreThreshold) {

                        }
                    }
                }
                if (createTemplates) {
                    //createTemplates(currentTime, segmentedRects)
                } else {
                    // ph2
                    //getNistQualityValues(segmentedRects);
                    getFingerprintQualityValues(segmentedRects)
                }
//                val fingerprintFilePaths = ArrayList<String>()
//                saveFingerprints(
//                    currentTime,
//                    previewImageBuffer,
//                    previewWidth,
//                    previewHeight,
//                    segmentedRects,
//                    fingerprintFilePaths
//                )
//                val resultFilePath: String = saveResultBitmap(
//                    currentTime,
//                    previewImageBuffer,
//                    previewWidth,
//                    previewHeight,
//                    segmentedRects,
//                    livenessScore,
//                    reverseFlag
//                )
//                hideProgress()
//                runOnUiThread(Runnable {
//                    val intent = Intent()
//                    intent.putExtra("resultFilePath", resultFilePath)
//                    for (i in fingerprintFilePaths.indices) {
//                        intent.putExtra(
//                            "fingerprintFilePath$i", fingerprintFilePaths[i]
//                        )
//                    }
//                    setResult(AppCompatActivity.RESULT_OK, intent)
//                    finish()
//                })
            }
            updateStatus(captureStatus, rects)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()

        } finally {
            imageProxy.close()
        }
    }

    private fun updateStatus(captureStatus: Int, rects: java.util.ArrayList<SgmRectImage>) {

        if (captureStatus == CaptureStatus.frameSkipped) {
            return
        }
        if (captureStatus == CaptureStatus.tooFewFingers) {
            setStatus("Frame " + getCaptureObjectName())
        } else if (captureStatus == CaptureStatus.tooManyFingers) {
            val expectedFingerCount =
                if (positionCode == NistPosCode.POS_CODE_PL_R_4F || positionCode == NistPosCode.POS_CODE_PL_L_4F) 4 else if (positionCode == NistPosCode.POS_CODE_L_AND_R_THUMBS || positionCode == NistPosCode.POS_CODE_R_INDEX_MIDDLE || positionCode == NistPosCode.POS_CODE_L_INDEX_MIDDLE) 2 else 1
            setStatus(
                "More than " + expectedFingerCount + if (expectedFingerCount == 1) " finger" else " fingers"
            )
        } else if (captureStatus == CaptureStatus.wrongAngle) {
            val wrongAngleStatus =
                if ((((positionCode == NistPosCode.POS_CODE_L_AND_R_THUMBS) || (positionCode == NistPosCode.POS_CODE_R_THUMB) || (positionCode == NistPosCode.POS_CODE_L_THUMB)))) ("Hold " + getCaptureObjectName() + " vertically") else ("Hold " + getCaptureObjectName() + " horizontally")
            setStatus(wrongAngleStatus)
        } else if (captureStatus == CaptureStatus.tooFar) {
            setStatus("Please bring your hand closer")
        } else if (captureStatus == CaptureStatus.tooClose) {
            setStatus("Please move your hand further")
        } else if (captureStatus == CaptureStatus.lowFocus) {
            setStatus("Low focus. Try to move hand")
        } else if (captureStatus == CaptureStatus.goodFocus) {
            setStatus("Hold your hand steady")
        } else if (captureStatus == CaptureStatus.bestFrameChosen) {
            setStatus("")
        }

       
        val color =
            if ((((captureStatus == CaptureStatus.tooFewFingers) || (captureStatus == CaptureStatus.tooManyFingers) || (captureStatus == CaptureStatus.wrongHand)))) 0xFFFF2020 else if ((((captureStatus == CaptureStatus.wrongAngle) || (captureStatus == CaptureStatus.tooFar) || (captureStatus == CaptureStatus.tooClose) || (captureStatus == CaptureStatus.lowFocus)))) 0xFFDDDD00 else 0xFF00FF00
        Log.d(TAG, "updateStatus: $color")

        activity?.runOnUiThread {
            binding?.graphicOverlay?.drawBorderAndBoundBoxes(color.toInt(), rects)
        }
    }

    private fun getCaptureObjectName(): String? {
        if (positionCode == NistPosCode.POS_CODE_R_INDEX_MIDDLE || positionCode == NistPosCode.POS_CODE_L_INDEX_MIDDLE) {
            return (if (positionCode == NistPosCode.POS_CODE_R_INDEX_MIDDLE) "Right" else "Left") + " index and middle fingers"
        }
        if (positionCode == NistPosCode.POS_CODE_PL_R_4F || positionCode == NistPosCode.POS_CODE_PL_L_4F) {
            return (if (positionCode == NistPosCode.POS_CODE_PL_R_4F) "Right" else "Left") + " 4 fingers"
        }
        if (positionCode == NistPosCode.POS_CODE_L_AND_R_THUMBS) {
            return "Thumbs"
        }
        if (positionCode == NistPosCode.POS_CODE_R_THUMB || positionCode == NistPosCode.POS_CODE_L_THUMB) {
            return (if (positionCode == NistPosCode.POS_CODE_R_THUMB) "Right" else "Left") + " thumb"
        }
        return if (positionCode == NistPosCode.POS_CODE_R_INDEX_F) "Right index finger" else if (positionCode == NistPosCode.POS_CODE_R_MIDDLE_F) "Right middle finger" else if (positionCode == NistPosCode.POS_CODE_R_RING_F) "Right ring finger" else if (positionCode == NistPosCode.POS_CODE_R_LITTLE_F) "Right little finger" else if (positionCode == NistPosCode.POS_CODE_L_INDEX_F) "Left index finger" else if (positionCode == NistPosCode.POS_CODE_L_MIDDLE_F) "Left middle finger" else if (positionCode == NistPosCode.POS_CODE_L_RING_F) "Left ring finger" else if (positionCode == NistPosCode.POS_CODE_L_LITTLE_F) "Left little finger" else "finger"
    }

    private fun getFingerprintQualityValues(segmentedRects: ArrayList<SgmRectImage>) {

    }

    private fun setStatus(msg: String?) {

        if (msg != null && msg != "") {
            binding.txtStatus.setText(msg)
        } else {
            binding.txtStatus.setText("")
        }

    }

    private fun autoFocus() {
        try {
            camera?.let {
                val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                    binding.viewFinder.width.toFloat(), binding.viewFinder.height.toFloat()
                )
                val centerWidth: Int = binding.viewFinder.width / 2
                val centreHeight: Int = binding.viewFinder.height / 2
                val autoFocusPoint =
                    factory.createPoint(centerWidth.toFloat(), centreHeight.toFloat())
                val builder = FocusMeteringAction.Builder(
                    autoFocusPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                )
                builder.setAutoCancelDuration(1, TimeUnit.SECONDS)
                it.cameraControl.startFocusAndMetering(builder.build())
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun setPositionCodeIndex(positionCodeIndex: Int):Int{
        when (positionCodeIndex) {
            1 -> {
               return NistPosCode.POS_CODE_PL_R_4F
            }

            2 -> {
             return NistPosCode.POS_CODE_L_THUMB
            }

            3 -> {
             return   NistPosCode.POS_CODE_R_THUMB
            }

            4 -> {
                
                 return   NistPosCode.POS_CODE_L_AND_R_THUMBS
            }

            5 -> {
            
               return     NistPosCode.POS_CODE_L_INDEX_F
            }

            6 -> {
           
                  return  NistPosCode.POS_CODE_R_INDEX_F
            }

            7 -> {
           
                  return  NistPosCode.POS_CODE_L_INDEX_MIDDLE
            }

            8 -> {
            
                 return   NistPosCode.POS_CODE_R_INDEX_MIDDLE
            }

            0 -> {
                return NistPosCode.POS_CODE_PL_L_4F
            }

            else -> {
              return  NistPosCode.POS_CODE_PL_L_4F
            }
        }
    }

    private fun toggleFlash(enable: Boolean) {

        camera?.let {
            val cameraInfo: CameraInfo = it.cameraInfo
            if (it.cameraInfo.hasFlashUnit() && cameraInfo.torchState.value != null) {
                it.cameraControl.enableTorch(enable)
            }
        }

    }

    private fun createCacheFile(): Boolean {
        return try {
            File.createTempFile(cacheFileName, ".txt", requireContext().cacheDir)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    companion object {

        const val cacheFileName = "log.txt"
        const val developersToken = ""
        const val TAG = "FingerPrintFragment"
        const val showEllipses = true
        const val livenessCheck = false
        const val createTemplates = false

        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        @JvmStatic
        fun newInstance(param1: String, param2: String) = FingerPrintFragment().apply {
            arguments = Bundle().apply {
//                    putString(ARG_PARAM1, param1)
//                    putString(ARG_PARAM2, param2)
            }
        }
    }

    private fun setScreenBrightnessFull() {
        activity?.let {
            it.window?.let { window ->
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val params: WindowManager.LayoutParams = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                window.attributes = params
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}