package com.autoscroll.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.autoscroll.app.databinding.ActivityMainBinding

/**
 * Main control-panel activity.
 *
 * Provides:
 * - Permission check / request buttons (Accessibility, Overlay)
 * - Sliders for tuning engine parameters
 * - Status indicator reflecting whether scrolling is active
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AutoScrollViewModel

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissionStates()
        }

    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AutoScrollViewModel::class.java]

        setupPermissionButtons()
        setupSliders()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    // ---------- Permission handling ----------

    private fun setupPermissionButtons() {
        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnEnableOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }

        binding.btnStartOverlay.setOnClickListener {
            if (isAccessibilityServiceEnabled() && Settings.canDrawOverlays(this)) {
                val intent = Intent(this, FloatingButtonService::class.java)
                ContextCompat.startForegroundService(this, intent)
                Toast.makeText(this, "Floating button launched", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshPermissionStates() {
        val a11yEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)

        binding.btnEnableAccessibility.isEnabled = !a11yEnabled
        binding.btnEnableAccessibility.text = if (a11yEnabled) "✓ Accessibility Enabled" else getString(R.string.btn_enable_accessibility)

        binding.btnEnableOverlay.isEnabled = !overlayEnabled
        binding.btnEnableOverlay.text = if (overlayEnabled) "✓ Overlay Enabled" else getString(R.string.btn_enable_overlay)

        binding.btnStartOverlay.isEnabled = a11yEnabled && overlayEnabled
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${AutoScrollService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
    }

    // ---------- Sliders ----------

    private fun setupSliders() {
        binding.sliderBaseDelay.addOnChangeListener { _, value, _ ->
            viewModel.setBaseDelay(value)
        }
        binding.sliderJitter.addOnChangeListener { _, value, _ ->
            viewModel.setJitterMultiplier(value)
        }
        binding.sliderSwipeLength.addOnChangeListener { _, value, _ ->
            viewModel.setSwipeLengthVariance(value)
        }
        binding.sliderSwipeDuration.addOnChangeListener { _, value, _ ->
            viewModel.setSwipeDuration(value)
        }
    }

    // ---------- Observe ViewModel ----------

    private fun observeViewModel() {
        viewModel.baseDelay.observe(this) { value ->
            binding.tvBaseDelayValue.text = "${value.toInt()} ms"
        }
        viewModel.jitterMultiplier.observe(this) { value ->
            binding.tvJitterValue.text = "${"%.2f".format(value)}x"
        }
        viewModel.swipeLengthVariance.observe(this) { value ->
            binding.tvSwipeLengthValue.text = "${value.toInt()} px"
        }
        viewModel.swipeDuration.observe(this) { value ->
            binding.tvSwipeDurationValue.text = "${value.toInt()} ms"
        }
        viewModel.isScrolling.observe(this) { running ->
            if (running) {
                binding.tvStatus.text = getString(R.string.status_running)
                binding.statusIndicator.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.status_running)
            } else {
                val hasService = isAccessibilityServiceEnabled()
                binding.tvStatus.text = if (hasService)
                    getString(R.string.status_idle)
                else
                    getString(R.string.status_no_service)
                binding.statusIndicator.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.status_idle)
            }
        }
    }
}
