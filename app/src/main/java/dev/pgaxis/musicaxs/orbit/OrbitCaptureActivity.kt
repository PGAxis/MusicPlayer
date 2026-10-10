package dev.pgaxis.musicaxs.orbit

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import dev.pgaxis.musicaxs.R

class OrbitCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.orbit_capture_layout)
        return findViewById(R.id.zxing_barcode_scanner)
    }
}