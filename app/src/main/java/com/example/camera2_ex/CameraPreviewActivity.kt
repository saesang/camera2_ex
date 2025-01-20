package com.example.camera2_ex

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camera2_ex.databinding.ActivityCameraPreviewBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.nio.ByteBuffer

@AndroidEntryPoint
class CameraPreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraPreviewBinding

    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var cameraManager: CameraManager   // 카메라 장치를 제어하는 데 사용되는 CameraManager
    private lateinit var cameraDevice: CameraDevice // 카메라 장치를 나타내는 CameraDevice
    private lateinit var captureRequestBuilder: CaptureRequest.Builder  // 캡처 요청을 생성하는 데 사용되는 객체
    private lateinit var cameraCaptureSession: CameraCaptureSession // 카메라와 연결된 세션 객체 (캡처 작업을 관리)
    private lateinit var imageReader: ImageReader   // 사진 데이터 저장 객체
    private lateinit var photoFile: File    // 사진 데이터를 저장하는 파일 객체
    private var cameraId: String = ""   // 사용 중인 카메라 ID (예: 후면 또는 전면 카메라)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_camera_preview)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
        setBinding()

        with(binding) {
            cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

            // previewCamera의 SurfaceTextureListener 설정
            previewCamera.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    // TextureView가 준비되면 카메라 설정
                    // 권한 확인 후 카메라 오픈 및 프리뷰
                    if (isCameraPermissionGranted()) {
                        setupCamera()
                    } else {
                        requestCameraPermission()
                    }
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    // TextureView 크기가 변경될 때 호출
                }
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    // TextureView가 파괴될 때 호출. true를 반환하여 SurfaceTexture를 해제
                    return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    // TextureView가 업데이트될 때 호출
                }
            }

            btnCapture.setOnClickListener {
                takePicture()
            }

            btnChangeCamera.setOnClickListener {
                switchCamera()
            }
        }
    }

    // 카메라 권한 확인
    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA  // Manifest.permission.CAMERA로 하면 안됨
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 카메라 권한 요청
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    // 권한 요청 결과 처리 -> requestPermissions() 호출에 따른 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 허용된 경우 카메라 설정
                setupCamera()
            } else {
                // 권한이 거부된 경우 사용자에게 알림
                Toast.makeText(this, "카메라 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 카메라 세팅 시작
    private fun setupCamera() {
        // 사용 가능한 모든 카메라 ID 확인
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)

            // 후면 카메라 선택
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                cameraId = id
                break
            }
        }
        // 카메라 오픈
        openCamera()
    }

    // 카메라 오픈
    private fun openCamera() {
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    // 카메라 성공적 오픈 시
                    cameraDevice = camera
                    setupImageReader()
                    startPreview() // 프리뷰 시작
                }

                override fun onDisconnected(camera: CameraDevice) {
                    // 카메라 연결이 끊어졌을 때 호출
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    // 카메라를 열다가 오류가 발생했을 때 호출
                    camera.close()
                }
            }, null)
        } catch (e: SecurityException) {
            // 카메라 권한이 없을 경우 예외 처리
            Log.e("CameraPreviewActivity", "카메라 권한이 필요합니다.")
        } catch (e: Exception) {
            // 기타 예외 처리
            Log.e("CameraPreviewActivity", "카메라 설정 실패: ${e.message}")
        }
    }

    // 카메라 프리뷰 시작
    private fun startPreview() {
        val surfaceTexture = binding.previewCamera.surfaceTexture
        // 프리뷰 크기 설정(해상도 설정)
        surfaceTexture?.setDefaultBufferSize(1920, 1080)

        // TextureView에서 가져온 SurfaceTexture로 Surface 객체 생성
        val surface = Surface(surfaceTexture)

        // 카메라 요청 생성 (TEMPLATE_PREVIEW: 프리뷰용 설정)
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface) // 프리뷰 출력을 Surface에 연결

        // 카메라 캡처 세션 생성
        cameraDevice.createCaptureSession(
            listOf(surface, imageReader.surface), // 세션이 사용할 출력 대상
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    // 세션이 성공적으로 구성되었을 때 호출
                    cameraCaptureSession = session

                    // 자동 모드 설정 (노출, 초점 등)
                    captureRequestBuilder.set(
                        CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO
                    )

                    // 프리뷰를 반복적으로 요청하여 화면에 표시 -> 무조건 필요
                    session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // 세션 구성 실패 시 호출
                    Log.e("CameraPreviewActivity", "카메라 세션 구성 실패")
                }
            }, null
        )
    }

    // ImageReader 초기화
    private fun setupImageReader() {
        // ImageReader 생성
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            // 이미지가 캡처되었을 때 호출
            val image = reader.acquireLatestImage()
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // 이미지 저장
            saveImage(bytes)
            image.close()
        }, null)
    }

    // 사진 캡처
    private fun takePicture() {
        val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.addTarget(imageReader.surface)
        captureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        playCaptureAnimation()

        cameraCaptureSession.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
            }
        }, null)
    }

    // 캡쳐 모션
    private fun playCaptureAnimation() {
        // 흰색 깜빡임 효과를 위한 뷰 추가
        val flashView = View(this).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
        }
        val decorView = window.decorView as ViewGroup
        decorView.addView(flashView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 애니메이션: 투명도 0 -> 0.5 -> 0
        flashView.animate()
            .alpha(0.5f)
            .setDuration(50) // 흰색으로 바뀌는 시간
            .withEndAction {
                flashView.animate()
                    .alpha(0f)
                    .setDuration(100) // 흰색이 사라지는 시간
                    .withEndAction {
                        decorView.removeView(flashView) // 애니메이션 종료 후 뷰 제거
                    }
            }
    }

    // 사진 데이터 파일에 저장
    private fun saveImage(bytes: ByteArray) {
        // 앱 내 저장 경로
//        val picturesDir = getExternalFilesDir(null)?.absolutePath
//        photoFile = File("$picturesDir/photo.jpg")
//        FileOutputStream(photoFile).use { output ->
//            output.write(bytes)
//            Log.d("CameraPreviewActivity", "사진 저장 경로: ${photoFile.absolutePath}")

        // 갤러리 저장 경로
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "photo_${System.currentTimeMillis()}.jpg") // 파일 이름
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg") // 파일 타입
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyCameraApp") // 저장 경로
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { outputStream ->
                outputStream?.write(bytes)
                Log.d("CameraPreviewActivity", "이미지 저장 경로: $uri")
            }
        } else {
            Log.e("CameraPreviewActivity", "이미지 저장 실패")
        }
    }

    // 카메라 전환
    private fun switchCamera() {
        // 현재 카메라 종료
        cameraDevice.close()
        cameraCaptureSession.close()

        // 현재 후면 카메라 방향이면 전면 카메라로 전환
        cameraId = if (cameraId == getCameraId(CameraCharacteristics.LENS_FACING_BACK)) {
            getCameraId(CameraCharacteristics.LENS_FACING_FRONT)
        } else {    // 현재 전면 카메라 방향이면 후면 카메라로 전환
            getCameraId(CameraCharacteristics.LENS_FACING_BACK)
        }

        // 새 카메라 오픈
        openCamera()
    }

    // cameraId 가져오기
    private fun getCameraId(lensFacing: Int): String {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing) {
                return cameraId
            }
        }
        throw IllegalArgumentException("카메라 ID를 찾을 수 없습니다.")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraDevice.close()
    }

    private fun setBinding() {
        binding = ActivityCameraPreviewBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
    }
}