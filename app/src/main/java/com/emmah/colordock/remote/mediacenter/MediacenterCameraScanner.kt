package com.emmah.colordock.remote.mediacenter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import com.emmah.colordock.remote.mediacenter.protocol.PairingCandidate
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/** Bundled ML Kit runs QR decoding on device; images are never saved or uploaded. */
class MediacenterCameraScanner(
    private val context: Context,
    private val preview: TextureView,
    onCandidate: (PairingCandidate) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private val resources = MediacenterScannerResources()
    private val closed = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)
    private val scanner =
        BarcodeScanning
            .getClient(
                BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
            ).also { resources.own(AutoCloseable(it::close)) }
    private val qr = QrScanSession(onCandidate, ::close)
    private var camera: CameraDevice? = null
    private var images: ImageReader? = null
    private var previewSurface: Surface? = null
    private var closingReader: ImageReader? = null
    private var rotation = 0

    fun start() {
        if (closed.get()) return
        if (preview.isAvailable) {
            open()
        } else {
            preview.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = open()

                    override fun onSurfaceTextureSizeChanged(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        close()
                        return true
                    }
                }
        }
    }

    private fun open() {
        if (closed.get() || camera != null) return
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail()
        } else {
            try {
                val manager = context.getSystemService(CameraManager::class.java)
                val setup = cameraSetup(manager) ?: error("No suitable camera")
                prepareOutputs(setup)
                manager.openCamera(
                    setup.id,
                    context.mainExecutor,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(device: CameraDevice) {
                            if (!resources.own(device)) return
                            camera = device
                            configure(device)
                        }

                        override fun onDisconnected(device: CameraDevice) {
                            device.close()
                            fail()
                        }

                        override fun onError(
                            device: CameraDevice,
                            error: Int,
                        ) {
                            device.close()
                            fail()
                        }
                    },
                )
            } catch (_: Exception) {
                fail()
            }
        }
    }

    private fun cameraSetup(manager: CameraManager): CameraSetup? {
        val id =
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: return null
        val characteristics = manager.getCameraCharacteristics(id)
        val sizes =
            characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888)
                .orEmpty()
        val size =
            sizes
                .filter { it.width <= MAX_WIDTH && it.height <= MAX_WIDTH }
                .maxByOrNull { it.width * it.height }
        return size?.let { CameraSetup(id, it, characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0) }
    }

    private fun prepareOutputs(setup: CameraSetup) {
        val display =
            when (preview.display?.rotation) {
                Surface.ROTATION_90 -> QUARTER_TURN
                Surface.ROTATION_180 -> HALF_TURN
                Surface.ROTATION_270 -> THREE_QUARTER_TURN
                else -> 0
            }
        rotation = (setup.orientation - display + FULL_TURN) % FULL_TURN
        val texture = checkNotNull(preview.surfaceTexture)
        texture.setDefaultBufferSize(setup.size.width, setup.size.height)
        previewSurface = Surface(texture).also { surface -> resources.own(AutoCloseable(surface::release)) }
        images =
            ImageReader
                .newInstance(setup.size.width, setup.size.height, ImageFormat.YUV_420_888, MAX_IMAGES)
                .also { reader ->
                    resources.own(AutoCloseable { if (processing.get()) closingReader = reader else reader.close() })
                    reader.setOnImageAvailableListener({ decode(it) }, Handler(Looper.getMainLooper()))
                }
    }

    private fun configure(device: CameraDevice) {
        val surfaces = listOfNotNull(previewSurface, images?.surface)
        if (closed.get() || surfaces.size != OUTPUT_COUNT) {
            fail()
            return
        }
        try {
            device.createCaptureSession(
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    surfaces.map(::OutputConfiguration),
                    context.mainExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(value: CameraCaptureSession) {
                            if (!resources.own(value)) return
                            try {
                                val request =
                                    device
                                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                        .apply {
                                            surfaces.forEach(::addTarget)
                                            set(
                                                CaptureRequest.CONTROL_AF_MODE,
                                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                                            )
                                        }.build()
                                value.setRepeatingRequest(request, null, null)
                            } catch (_: Exception) {
                                fail()
                            }
                        }

                        override fun onConfigureFailed(value: CameraCaptureSession) {
                            value.close()
                            fail()
                        }
                    },
                ),
            )
        } catch (_: Exception) {
            fail()
        }
    }

    private fun decode(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        if (closed.get() || !processing.compareAndSet(false, true)) {
            image.close()
            return
        }
        try {
            scanner
                .process(InputImage.fromMediaImage(image, rotation))
                .addOnSuccessListener(context.mainExecutor) { values ->
                    if (!closed.get()) {
                        values.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let(
                            qr::receive,
                        )
                    }
                }.addOnFailureListener(context.mainExecutor) { if (!closed.get()) fail() }
                .addOnCompleteListener(context.mainExecutor) {
                    image.close()
                    processing.set(false)
                    closingReader?.close()
                    closingReader =
                        null
                }
        } catch (_: Exception) {
            image.close()
            processing.set(false)
            fail()
        }
    }

    private fun fail() {
        if (!closed.get()) {
            close()
            onError("Camera unavailable. You can pair manually.")
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            preview.surfaceTextureListener = null
            qr.close()
            resources.close()
            camera = null
            images = null
            previewSurface = null
        }
    }

    private class CameraSetup(
        val id: String,
        val size: android.util.Size,
        val orientation: Int,
    )

    private companion object {
        const val MAX_WIDTH = 1280
        const val MAX_IMAGES = 2
        const val OUTPUT_COUNT = 2
        const val QUARTER_TURN = 90
        const val HALF_TURN = 180
        const val THREE_QUARTER_TURN = 270
        const val FULL_TURN = 360
    }
}
