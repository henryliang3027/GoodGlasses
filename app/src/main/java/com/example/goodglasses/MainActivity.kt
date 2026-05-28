package com.example.goodglasses

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import com.htc.viveglass.sdk.ViveGlass
import com.htc.viveglass.sdk.ViveGlassKit
import com.htc.viveglass.sdk.simulator.ViveGlassSimulator
import com.example.goodglasses.ui.tab.CameraScreen
import com.example.goodglasses.ui.theme.AppColors
import com.example.goodglasses.util.DebugLogger
import com.example.goodglasses.util.Logger
import com.example.goodglasses.util.NoOpLogger

class MainActivity : AppCompatActivity() {

    val enableDebugLog = true

    var viveClientManager: ViveGlassKitManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.instance =
            if (enableDebugLog) DebugLogger()
            else NoOpLogger()

        enableEdgeToEdge()
        setContent {

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val glass = ViveGlass()
            val appContext = applicationContext
            val kit = ViveGlassKit(appContext)
            ViveGlassSimulator.create(appContext)
            val simulator = ViveGlassSimulator.instance()

            viveClientManager = ViveGlassKitManager(glass, kit, simulator, appContext, audioManager)
            val manager = viveClientManager ?: return@setContent
            SampleApp(manager)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        viveClientManager?.cleanup()
    }
}

@Composable
fun SampleApp(viveClientManager: ViveGlassKitManager) {
    Surface(
        color = AppColors.BgDarkPrimary,
        modifier = Modifier.fillMaxSize()
    ) {
        CameraScreen(viveClientManager)
    }
}
