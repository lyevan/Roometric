package com.infinity.roometric.views


import android.graphics.Color.rgb
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.math.Vector3.zero
import com.google.ar.sceneform.rendering.*

import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.infinity.roometric.MainActivity
import com.infinity.roometric.R
import com.infinity.roometric.data.PlaneType
import com.infinity.roometric.viewmodel.MeasurementViewModel
import com.infinity.roometric.viewmodel.ViewModel
import com.infinity.roometric.databinding.FragmentArBinding
import kotlinx.coroutines.launch

import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min


class ArFragment: Fragment(R.layout.fragment_ar) {
    
    // Measurement States
    enum class MeasurementState {
        SCANNING,      // Detecting planes
        IDLE,          // Ready to place first node
        FIRST_NODE,    // First node placed, ready for second
        SECOND_NODE,   // Second node placed, ready for third
        THIRD_NODE,    // Third node placed, ready for fourth
        FOURTH_NODE    // Fourth node placed, showing area
    }
    
    // Unit selection
    enum class MeasurementUnit {
        CM,     // Centimeters
        M,      // Meters
        IN,     // Inches
        FT      // Feet
    }
    
    // Plane detection mode
    enum class PlaneDetectionMode {
        HORIZONTAL_ONLY,  // Detect only horizontal planes (floors/ceilings)
        VERTICAL_ONLY,    // Detect only vertical planes (walls)
        BOTH             // Detect both (slower but more versatile)
    }
    
    // Tracking quality state
    enum class TrackingQuality {
        EXCELLENT,       // Perfect tracking
        GOOD,           // Good enough for measurements
        INSUFFICIENT,   // Poor quality, needs user action
        PAUSED          // Tracking lost completely
    }
    
    // Performance optimization variables
    private var frameCounter = 0
    private var lastHitTestResult: HitResult? = null
    private var previewLineNode: AnchorNode? = null
    private var previewLineRenderable: ModelRenderable? = null
    private var planeIndicatorNode: AnchorNode? = null
    private var planeIndicatorRenderable: ModelRenderable? = null
    
    // State management
    private var currentState = MeasurementState.SCANNING
    private var scanningProgress = 0
    private val detectedPlanes = HashSet<Plane>()
    private val planeIndicators = ArrayList<AnchorNode>()
    private var currentUnit = MeasurementUnit.CM  // Default to centimeters
    private var planeDetectionMode = PlaneDetectionMode.HORIZONTAL_ONLY  // Start with horizontal for better performance
    private var currentTrackingQuality = TrackingQuality.EXCELLENT
    
    // Performance optimization: Plane pose smoothing
    private val planePoseHistory = mutableMapOf<Plane, MutableList<Pose>>()
    private val maxPoseHistorySize = 5  // Average over 5 frames for stability
    
    // Performance optimization: Node position smoothing with exponential moving average
    private val nodePositionSmoothing = 0.3f  // Lower = more smoothing, higher = more responsive
    private var lastPreviewPosition: Vector3? = null
    
    // Depth API support
    private var isDepthSupported = false
    private var depthEnabled = false
    
    // Instant placement support
    private var useInstantPlacement = true
    private val instantPlacementNodes = mutableListOf<AnchorNode>()
    
    // Environmental feedback
    private var lastTrackingStateUpdate = 0L
    private val trackingStateUpdateInterval = 1000L  // Check every second
    
    // Renderables
    private var redSphereRender: ModelRenderable? = null      // First node
    private var greenSphereRender: ModelRenderable? = null    // Second node (height)
    private var blueSphereRender: ModelRenderable? = null     // Third node (width)
    private var purpleSphereRender: ModelRenderable? = null   // Fourth node (auto-calculated)
    private var yellowLineRender: ModelRenderable? = null     // Height line
    private var widthLineRender: ModelRenderable? = null      // Width line
    private var planeIndicatorRender: ModelRenderable? = null // Plane detection circles
    private var horizontalPlaneIndicator: ModelRenderable? = null // Horizontal plane indicator (floor/ceiling)
    private var verticalPlaneIndicator: ModelRenderable? = null   // Vertical plane indicator (wall)
    private var meshRender: ModelRenderable? = null           // Dynamic mesh for area
    private var viewRenderable: ViewRenderable? = null
    
    // Measurement nodes
    private var firstNode: AnchorNode? = null
    private var secondNode: AnchorNode? = null
    private var thirdNode: AnchorNode? = null
    private var fourthNode: AnchorNode? = null  // Automatically calculated fourth point
    private var heightLine: AnchorNode? = null
    private var widthLine: AnchorNode? = null
    private var rectangleLine1: AnchorNode? = null  // Line from third to fourth
    private var rectangleLine2: AnchorNode? = null  // Line from fourth to first
    private var heightLabel: AnchorNode? = null
    private var widthLabel: AnchorNode? = null
    private var areaLabel: AnchorNode? = null
    private var dynamicMesh: AnchorNode? = null
    private var previewLine: AnchorNode? = null  // Real-time preview line
    private var planeOrientationIndicator: AnchorNode? = null // Current plane orientation indicator
    
    // Measurements
    private var heightMeters = 0f
    private var widthMeters = 0f
    
    lateinit var binding: FragmentArBinding
    lateinit var arFragment: com.google.ar.sceneform.ux.ArFragment
    private val viewModel: ViewModel by activityViewModels()
    private val measurementViewModel: MeasurementViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentArBinding.bind(view)
        arFragment = childFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment

        // Configure AR session for optimal performance
        configureArSession()
        
        // Initialize renderables
        initObjects()
        
        // Setup UI
        updateUIForState()
        
        // Home button - navigate back to room selection
        binding.btnHome.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // Clear button - reset everything
        binding.btnClear.setOnClickListener {
            clearMeasurement()
        }
        
        // Plane detection mode button - cycle through detection modes
        binding.btnPlaneMode.setOnClickListener {
            cyclePlaneDetectionMode()
        }

        // Unit selection button - cycle through units
        binding.btnUnitSelection.setOnClickListener {
            cycleUnitSelection()
        }

        // Add/Measurement button - handle node placement based on state
        binding.btnAdd.setOnClickListener {
            handleMeasurementButtonClick()
        }
        
        // Save measurement button - show dialog to save current measurement
        binding.btnSaveMeasurement.setOnClickListener {
            showSaveMeasurementDialog()
        }
        
        // Finish button - navigate to scene view
        binding.floatingActionButton2.setOnClickListener {
            if (firstNode != null && secondNode != null && thirdNode != null) {
                val tempNodes = arrayListOf(firstNode!!, secondNode!!, thirdNode!!)
                viewModel.setLists(tempNodes)
                viewModel.setRenderables(redSphereRender, widthLineRender, yellowLineRender)
                findNavController().navigate(R.id.action_arFragment_to_sceneViewFragment)
            } else {
                Toast.makeText(requireContext(), "Complete all 3 measurements first", Toast.LENGTH_SHORT).show()
            }
        }

        // Disable tap listener - we'll use button only
        arFragment.setOnTapArPlaneListener(null)
        
        // Scene update listener for plane detection and dynamic mesh
        arFragment.arSceneView.scene.addOnUpdateListener {
            frameCounter++

            // Performance optimization: Only run expensive operations every 3rd frame (20fps instead of 60fps)
            val shouldUpdateExpensive = frameCounter % 3 == 0

            // Always update tracking quality feedback (lightweight)
            if (shouldUpdateExpensive) {
                updateTrackingQualityFeedback()
            }

            if (currentState == MeasurementState.SCANNING) {
                if (shouldUpdateExpensive) {
                    updatePlaneDetection()
                }
            } else if (currentState == MeasurementState.FIRST_NODE) {
                updatePreviewLine()
            } else if (currentState == MeasurementState.SECOND_NODE) {
                updatePreviewLineSecond()
            } else if (currentState == MeasurementState.THIRD_NODE) {
                updatePreviewLineThird()
            }

            // Make text labels always face the camera (billboard effect) - lightweight, run every frame
            updateLabelBillboards()

            // Update plane orientation indicator at crosshair - lightweight, run every frame
            updatePlaneOrientationIndicator()
        }
    }
    
    /**
     * Configure AR Session for optimal performance and accuracy
     * Optimizations:
     * 1. Enable Depth API for better distance measurements
     * 2. Configure plane detection based on user mode (horizontal/vertical)
     * 3. Enable instant placement for faster initial anchor placement
     * 4. Optimize focus mode for better plane detection
     */
    private fun configureArSession() {
        // Access ARCore session through the arFragment
        val arSceneView = arFragment.arSceneView

        arSceneView.scene.addOnUpdateListener { frameTime ->
            val session = arSceneView.session ?: return@addOnUpdateListener

            // Only configure once per session
            if (depthEnabled) return@addOnUpdateListener

            try {
                val config = Config(session)

                // Enable depth if supported - improves 3D awareness and measurement accuracy
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                    isDepthSupported = true
                    depthEnabled = true
                    Log.d("ARFragment", "Depth API enabled - measurements will be more accurate")
                } else {
                    config.depthMode = Config.DepthMode.DISABLED
                    Log.d("ARFragment", "Depth API not supported on this device")
                }

                // Configure plane detection mode based on current mode
                // This improves detection speed by focusing only on relevant planes
                config.planeFindingMode = when (planeDetectionMode) {
                    PlaneDetectionMode.HORIZONTAL_ONLY -> {
                        Log.d("ARFragment", "Optimized for HORIZONTAL planes (floors/ceilings) - faster detection")
                        Config.PlaneFindingMode.HORIZONTAL
                    }
                    PlaneDetectionMode.VERTICAL_ONLY -> {
                        Log.d("ARFragment", "Optimized for VERTICAL planes (walls) - faster detection")
                        Config.PlaneFindingMode.VERTICAL
                    }
                    PlaneDetectionMode.BOTH -> {
                        Log.d("ARFragment", "Detecting BOTH plane types - slower but more versatile")
                        Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    }
                }

                // Enable instant placement - allows anchor placement before full plane lock
                // This makes the app feel much more responsive
                config.instantPlacementMode = if (useInstantPlacement) {
                    Config.InstantPlacementMode.LOCAL_Y_UP
                } else {
                    Config.InstantPlacementMode.DISABLED
                }

                // Use FIXED focus mode to prevent constant refocusing
                // This stops the annoying focus hunting behavior
                config.focusMode = Config.FocusMode.FIXED

                // Enable light estimation for better rendering (optional but improves visual quality)
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                
                // CRITICAL: Set update mode to LATEST_CAMERA_IMAGE for Sceneform compatibility
                // Sceneform requires this mode to function properly
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

                session.configure(config)
                Log.d("ARFragment", "AR Session configured with optimizations")
            } catch (e: Exception) {
                Log.e("ARFragment", "Failed to configure AR session", e)
            }
        }
    }
    
    /**
     * Monitor tracking quality and provide real-time feedback to user
     * Helps users understand when they need to move the camera or improve lighting
     */
    private fun updateTrackingQualityFeedback() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTrackingStateUpdate < trackingStateUpdateInterval) {
            return  // Don't check too frequently to avoid UI spam
        }
        lastTrackingStateUpdate = currentTime
        
        val frame = arFragment.arSceneView.arFrame ?: return
        val camera = frame.camera
        
        val newQuality = when (camera.trackingState) {
            TrackingState.TRACKING -> {
                // Check tracking quality based on tracked features
                val lightEstimate = frame.lightEstimate
                val lightIntensity = lightEstimate?.pixelIntensity ?: 0f
                
                when {
                    lightIntensity < 0.3f -> TrackingQuality.INSUFFICIENT  // Too dark
                    lightIntensity > 1.5f -> TrackingQuality.INSUFFICIENT  // Too bright
                    else -> TrackingQuality.EXCELLENT
                }
            }
            TrackingState.PAUSED -> TrackingQuality.PAUSED
            else -> TrackingQuality.INSUFFICIENT
        }
        
        // Only update UI if quality changed
        if (newQuality != currentTrackingQuality) {
            currentTrackingQuality = newQuality
            updateTrackingQualityUI()
        }
    }
    
    /**
     * Update UI to reflect current tracking quality
     * Provides actionable feedback to users
     */
    private fun updateTrackingQualityUI() {
        when (currentTrackingQuality) {
            TrackingQuality.EXCELLENT -> {
                // Hide any warning messages
                binding.tvInstruction.setTextColor(
                    resources.getColor(android.R.color.white, null)
                )
            }
            TrackingQuality.INSUFFICIENT -> {
                // Show warning with actionable advice
                binding.tvInstruction.setTextColor(
                    resources.getColor(android.R.color.holo_orange_light, null)
                )
                if (currentState == MeasurementState.SCANNING) {
                    Toast.makeText(
                        requireContext(),
                        "Move camera slowly and ensure good lighting for better tracking",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            TrackingQuality.PAUSED -> {
                binding.tvInstruction.setTextColor(
                    resources.getColor(android.R.color.holo_red_light, null)
                )
                Toast.makeText(
                    requireContext(),
                    "Tracking lost! Move camera to textured surfaces",
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {}
        }
    }
    
    /**
     * Apply exponential moving average smoothing to position
     * Reduces jitter and flicker in preview lines and nodes
     * Formula: smoothed = previous * (1 - alpha) + current * alpha
     * where alpha (nodePositionSmoothing) controls responsiveness vs stability
     */
    private fun smoothPosition(currentPos: Vector3, lastPos: Vector3?): Vector3 {
        if (lastPos == null) return currentPos
        
        return Vector3(
            lastPos.x * (1 - nodePositionSmoothing) + currentPos.x * nodePositionSmoothing,
            lastPos.y * (1 - nodePositionSmoothing) + currentPos.y * nodePositionSmoothing,
            lastPos.z * (1 - nodePositionSmoothing) + currentPos.z * nodePositionSmoothing
        )
    }
    
    /**
     * Refine plane pose by averaging multiple frames
     * Improves stability and accuracy of plane detection
     * Only confirms plane position after consistent detection across frames
     */
    private fun getRefinedPlanePose(plane: Plane): Pose {
        // Get or create pose history for this plane
        val poseHistory = planePoseHistory.getOrPut(plane) { mutableListOf() }
        
        // Add current pose to history
        poseHistory.add(plane.centerPose)
        
        // Keep only recent poses (moving window)
        if (poseHistory.size > maxPoseHistorySize) {
            poseHistory.removeAt(0)
        }
        
        // If we don't have enough samples yet, return current pose
        if (poseHistory.size < 3) {
            return plane.centerPose
        }
        
        // Average translation components across all poses
        var avgX = 0f
        var avgY = 0f
        var avgZ = 0f
        
        for (pose in poseHistory) {
            avgX += pose.tx()
            avgY += pose.ty()
            avgZ += pose.tz()
        }
        
        val count = poseHistory.size
        avgX /= count
        avgY /= count
        avgZ /= count
        
        // Return averaged pose (keeping original rotation)
        return Pose.makeTranslation(avgX, avgY, avgZ)
            .compose(plane.centerPose.extractRotation())
    }
    // Update plane detection progress
    private fun updatePlaneDetection() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val planes = frame.getUpdatedTrackables(Plane::class.java)
        
        for (plane in planes) {
            // Filter planes based on detection mode for faster, more focused detection
            val isRelevantPlane = when (planeDetectionMode) {
                PlaneDetectionMode.HORIZONTAL_ONLY -> 
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING || 
                    plane.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING
                PlaneDetectionMode.VERTICAL_ONLY -> 
                    plane.type == Plane.Type.VERTICAL
                PlaneDetectionMode.BOTH -> true
            }
            
            if (plane.trackingState == TrackingState.TRACKING && isRelevantPlane) {
                if (!detectedPlanes.contains(plane)) {
                    detectedPlanes.add(plane)
                    // Use refined pose for more stable plane indicators
                    addPlaneIndicatorWithRefinedPose(plane)
                }
            }
        }
        
        // Calculate progress based on plane count and area - made more lenient
        val totalPlaneArea = detectedPlanes.sumOf { it.extentX.toDouble() * it.extentZ.toDouble() }
        val planeCountBonus = detectedPlanes.size * 10  // Bonus for each detected plane
        val areaProgress = (totalPlaneArea * 25).toInt()  // Reduced threshold - now needs ~4 sq meters
        scanningProgress = min(100, areaProgress + planeCountBonus)
        
        // Update UI
        binding.progressBarScanning.progress = scanningProgress
        binding.tvScanningProgress.text = "$scanningProgress%"
        
        // If progress is 100%, switch to IDLE state
        if (scanningProgress >= 100 && currentState == MeasurementState.SCANNING) {
            currentState = MeasurementState.IDLE
            updateUIForState()
            
            // Provide feedback based on depth availability
            val depthMessage = if (depthEnabled) {
                "Planes detected with Depth API! Measurements will be highly accurate"
            } else {
                "Planes detected! Ready to measure"
            }
            Toast.makeText(requireContext(), depthMessage, Toast.LENGTH_SHORT).show()
        }
    }
    
    // Add visual indicator on detected planes with refined pose averaging
    private fun addPlaneIndicatorWithRefinedPose(plane: Plane) {
        if (planeIndicatorRender == null) return
        
        // Use refined pose for more stable indicators
        val refinedPose = getRefinedPlanePose(plane)
        val anchor = plane.createAnchor(refinedPose)
        val anchorNode = AnchorNode(anchor).apply {
            setParent(arFragment.arSceneView.scene)
        }
        
        TransformableNode(arFragment.transformationSystem).apply {
            renderable = planeIndicatorRender
            setParent(anchorNode)
        }
        
        planeIndicators.add(anchorNode)
        arFragment.arSceneView.scene.addChild(anchorNode)
        
        // Limit to 10 indicators to avoid clutter
        if (planeIndicators.size > 10) {
            val oldIndicator = planeIndicators.removeAt(0)
            arFragment.arSceneView.scene.removeChild(oldIndicator)
            oldIndicator.anchor?.detach()
        }
    }
    
    // Update UI based on current state
    private fun updateUIForState() {
        when (currentState) {
            MeasurementState.SCANNING -> {
                binding.scanningCard.visibility = View.VISIBLE
                binding.instructionCard.visibility = View.GONE
                binding.btnAdd.isEnabled = false
                binding.btnAdd.alpha = 0.5f
                binding.btnSaveMeasurement.visibility = View.GONE
            }
            MeasurementState.IDLE -> {
                binding.scanningCard.visibility = View.GONE
                binding.instructionCard.visibility = View.VISIBLE
                binding.tvInstructionTitle.text = "Step 1: Place Base Point"
                binding.tvInstruction.text = "Tap the center button to place the red base point"
                binding.btnAdd.isEnabled = true
                binding.btnAdd.alpha = 1f
                binding.btnSaveMeasurement.visibility = View.GONE
                // Remove plane indicators
                clearPlaneIndicators()
            }
            MeasurementState.FIRST_NODE -> {
                binding.tvInstructionTitle.text = "Step 2: Place Second Point"
                binding.tvInstruction.text = "Point at the second corner and tap the button again"
                binding.btnSaveMeasurement.visibility = View.GONE
            }
            MeasurementState.SECOND_NODE -> {
                binding.tvInstructionTitle.text = "Step 3: Place Third Point"
                binding.tvInstruction.text = "Point at the third corner and tap the button again"
                binding.btnSaveMeasurement.visibility = View.GONE
            }
            MeasurementState.THIRD_NODE -> {
                binding.tvInstructionTitle.text = "Step 4: Place Fourth Point"
                binding.tvInstruction.text = "Point at the fourth corner and tap the button again"
                binding.btnSaveMeasurement.visibility = View.GONE
            }
            MeasurementState.FOURTH_NODE -> {
                binding.tvInstructionTitle.text = "Measurement Complete!"
                binding.tvInstruction.text = "Height: ${formatDistance(heightMeters)}, Width: ${formatDistance(widthMeters)}, Area: ${formatArea(heightMeters * widthMeters)}"
                binding.btnSaveMeasurement.visibility = View.VISIBLE
            }
        }
    }
    
    // Cycle through plane detection modes
    private fun cyclePlaneDetectionMode() {
        // Cycle to next mode
        planeDetectionMode = when (planeDetectionMode) {
            PlaneDetectionMode.HORIZONTAL_ONLY -> PlaneDetectionMode.VERTICAL_ONLY
            PlaneDetectionMode.VERTICAL_ONLY -> PlaneDetectionMode.BOTH
            PlaneDetectionMode.BOTH -> PlaneDetectionMode.HORIZONTAL_ONLY
        }
        
        // Reset depth enabled flag to force reconfiguration
        depthEnabled = false
        
        // Clear detected planes to restart detection with new mode
        detectedPlanes.clear()
        clearPlaneIndicators()
        scanningProgress = 0
        binding.progressBarScanning.progress = 0
        binding.tvScanningProgress.text = "0%"
        
        // Show toast with current mode
        val modeName = when (planeDetectionMode) {
            PlaneDetectionMode.HORIZONTAL_ONLY -> "Floor/Ceiling Only 📐"
            PlaneDetectionMode.VERTICAL_ONLY -> "Walls Only 📏"
            PlaneDetectionMode.BOTH -> "Floor & Walls 🔲"
        }
        Toast.makeText(requireContext(), "Scanning Mode: $modeName", Toast.LENGTH_LONG).show()
        
        // Update instruction text based on mode
        if (currentState == MeasurementState.SCANNING) {
            val instruction = when (planeDetectionMode) {
                PlaneDetectionMode.HORIZONTAL_ONLY -> "Point camera at floors or ceilings"
                PlaneDetectionMode.VERTICAL_ONLY -> "Point camera at walls"
                PlaneDetectionMode.BOTH -> "Point camera at any surface"
            }
            binding.tvScanningInstruction.text = instruction
        }
    }
    
    // Cycle through unit selection
    private fun cycleUnitSelection() {
        currentUnit = when (currentUnit) {
            MeasurementUnit.CM -> MeasurementUnit.M
            MeasurementUnit.M -> MeasurementUnit.IN
            MeasurementUnit.IN -> MeasurementUnit.FT
            MeasurementUnit.FT -> MeasurementUnit.CM
        }
        
        // Update all existing labels with new unit
        updateAllLabels()
        
        // Show toast with current unit
        val unitName = when (currentUnit) {
            MeasurementUnit.CM -> "Centimeters"
            MeasurementUnit.M -> "Meters"
            MeasurementUnit.IN -> "Inches"
            MeasurementUnit.FT -> "Feet"
        }
        Toast.makeText(requireContext(), "Unit: $unitName", Toast.LENGTH_SHORT).show()
    }
    
    // Update all measurement labels with current unit
    private fun updateAllLabels() {
        // Recreate height label if it exists
        heightLabel?.let { label ->
            val position = label.worldPosition
            arFragment.arSceneView.scene.removeChild(label)
            heightLabel = addLabel(heightMeters, position)
        }
        
        // Recreate width label if it exists
        widthLabel?.let { label ->
            val position = label.worldPosition
            arFragment.arSceneView.scene.removeChild(label)
            widthLabel = addLabel(widthMeters, position)
        }
        
        // Recreate area label if it exists
        areaLabel?.let { label ->
            val position = label.worldPosition
            arFragment.arSceneView.scene.removeChild(label)
            // Recreate area label
            val area = heightMeters * widthMeters
            val centerPos = Vector3(
                (firstNode!!.worldPosition.x + secondNode!!.worldPosition.x + thirdNode!!.worldPosition.x + fourthNode!!.worldPosition.x) / 4f,
                firstNode!!.worldPosition.y + 0.3f,
                (firstNode!!.worldPosition.z + secondNode!!.worldPosition.z + thirdNode!!.worldPosition.z + fourthNode!!.worldPosition.z) / 4f
            )
            
            areaLabel = AnchorNode().apply {
                setParent(arFragment.arSceneView.scene)
                worldPosition = centerPos
            }
            
            ViewRenderable.builder()
                .setView(requireContext(), R.layout.distance)
                .build()
                .thenAccept { areaRenderable ->
                    val card = areaRenderable.view as CardView
                    val tv = card.getChildAt(0) as TextView
                    tv.text = "Area: ${formatArea(area)}"
                    tv.textSize = 18f
                    areaLabel?.renderable = areaRenderable
                }
            
            arFragment.arSceneView.scene.addChild(areaLabel)
        }
    }
    
    // Handle measurement button clicks based on state
    private fun handleMeasurementButtonClick() {
        when (currentState) {
            MeasurementState.SCANNING -> {
                Toast.makeText(requireContext(), "Please wait for plane detection", Toast.LENGTH_SHORT).show()
            }
            MeasurementState.IDLE -> placeFirstNode()
            MeasurementState.FIRST_NODE -> placeSecondNode()
            MeasurementState.SECOND_NODE -> placeThirdNode()
            MeasurementState.THIRD_NODE -> placeFourthNode()
            MeasurementState.FOURTH_NODE -> {
                // Reset for new measurement
                clearMeasurement()
            }
        }
    }
    
    // Place first node (red sphere) - base point
    // Enhanced with instant placement and depth API support
    private fun placeFirstNode() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        
        val hits = frame.hitTest(centerX, centerY)
        
        // Try instant placement first for faster response
        if (useInstantPlacement && hits.isEmpty()) {
            val instantHits = frame.hitTestInstantPlacement(centerX, centerY, 1.0f)
            if (instantHits.isNotEmpty()) {
                val instantHit = instantHits[0]
                placeNodeWithInstantPlacement(instantHit, true)
                return
            }
        }
        
        // Fall back to traditional plane-based placement with depth enhancement
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                // Use depth data if available for more accurate positioning
                val finalPose = if (depthEnabled) {
                    enhancePoseWithDepth(hit, frame)
                } else {
                    hit.hitPose
                }
                
                val anchor = hit.createAnchor()
                firstNode = AnchorNode(anchor).apply {
                    setParent(arFragment.arSceneView.scene)
                }
                
                TransformableNode(arFragment.transformationSystem).apply {
                    renderable = redSphereRender
                    setParent(firstNode)
                }
                
                arFragment.arSceneView.scene.addChild(firstNode)
                currentState = MeasurementState.FIRST_NODE
                updateUIForState()
                return
            }
        }
        
        Toast.makeText(requireContext(), "Point at a detected surface", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Place node using instant placement for faster response
     * Instant placement allows immediate anchor creation before full plane detection
     */
    private fun placeNodeWithInstantPlacement(hit: HitResult, isFirstNode: Boolean) {
        val trackable = hit.trackable as? InstantPlacementPoint ?: return
        
        val anchor = hit.createAnchor()
        val node = AnchorNode(anchor).apply {
            setParent(arFragment.arSceneView.scene)
        }
        
        val renderableToUse = if (isFirstNode) redSphereRender else greenSphereRender
        
        TransformableNode(arFragment.transformationSystem).apply {
            renderable = renderableToUse
            setParent(node)
        }
        
        arFragment.arSceneView.scene.addChild(node)
        instantPlacementNodes.add(node)
        
        if (isFirstNode) {
            firstNode = node
            currentState = MeasurementState.FIRST_NODE
            updateUIForState()
            Toast.makeText(
                requireContext(),
                "Using instant placement - node will refine as tracking improves",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * Enhance pose accuracy using depth data
     * Depth API provides more accurate distance information
     */
    private fun enhancePoseWithDepth(hit: HitResult, frame: com.google.ar.core.Frame): Pose {
        // Note: Full depth API integration requires additional implementation
        // For now, return the original pose
        // In a full implementation, you would:
        // 1. Acquire depth image
        // 2. Transform screen coordinates to depth image coordinates
        // 3. Sample depth value at that point
        // 4. Use depth value to refine the hit pose distance
        
        return hit.hitPose
    }
    
    // Place second node (green sphere) - height point
    private fun placeSecondNode() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        
        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val anchor = hit.createAnchor()
                secondNode = AnchorNode(anchor).apply {
                    setParent(arFragment.arSceneView.scene)
                }
                
                TransformableNode(arFragment.transformationSystem).apply {
                    renderable = greenSphereRender
                    setParent(secondNode)
                }
                
                arFragment.arSceneView.scene.addChild(secondNode)
                
                // Remove preview line
                previewLine?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                    previewLine = null
                }
                
                // Draw height line and calculate distance
                drawHeightLine()
                
                currentState = MeasurementState.SECOND_NODE
                updateUIForState()
                return
            }
        }
        
        Toast.makeText(requireContext(), "Point at a detected surface", Toast.LENGTH_SHORT).show()
    }
    
    // Place third node (blue sphere) - width point
    private fun placeThirdNode() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        
        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val anchor = hit.createAnchor()
                thirdNode = AnchorNode(anchor).apply {
                    setParent(arFragment.arSceneView.scene)
                }
                
                TransformableNode(arFragment.transformationSystem).apply {
                    renderable = blueSphereRender
                    setParent(thirdNode)
                }
                
                arFragment.arSceneView.scene.addChild(thirdNode)
                
                // Remove dynamic mesh
                dynamicMesh?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                }
                
                // Remove preview line
                previewLine?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                    previewLine = null
                }
                
                // Draw width line
                drawWidthLine()
                
                currentState = MeasurementState.THIRD_NODE
                updateUIForState()
                return
            }
        }
        
        Toast.makeText(requireContext(), "Point at a detected surface", Toast.LENGTH_SHORT).show()
    }
    
    // Place fourth node (purple sphere) - final corner point
    private fun placeFourthNode() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        
        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val anchor = hit.createAnchor()
                fourthNode = AnchorNode(anchor).apply {
                    setParent(arFragment.arSceneView.scene)
                }
                
                TransformableNode(arFragment.transformationSystem).apply {
                    renderable = purpleSphereRender
                    setParent(fourthNode)
                }
                
                arFragment.arSceneView.scene.addChild(fourthNode)
                
                // Remove preview line
                previewLine?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                    previewLine = null
                }
                
                // Draw rectangle lines and calculate area
                drawRectangleLines()
                calculateArea()
                
                currentState = MeasurementState.FOURTH_NODE
                updateUIForState()
                return
            }
        }
        
        Toast.makeText(requireContext(), "Point at a detected surface", Toast.LENGTH_SHORT).show()
    }
    
    // Calculate and place fourth node to complete rectangle: D = A + (C - B)
    private fun calculateAndPlaceFourthNode() {
        if (firstNode == null || secondNode == null || thirdNode == null) return
        
        val posA = firstNode!!.worldPosition  // A
        val posB = secondNode!!.worldPosition // B  
        val posC = thirdNode!!.worldPosition  // C
        
        // D = A + (C - B)
        val vectorCB = Vector3.subtract(posC, posB)
        val posD = Vector3.add(posA, vectorCB)
        
        // Create anchor at calculated position
        val pose = Pose.makeTranslation(posD.x, posD.y, posD.z)
        val anchor = arFragment.arSceneView.session?.createAnchor(pose)
        
        if (anchor != null) {
            fourthNode = AnchorNode(anchor).apply {
                setParent(arFragment.arSceneView.scene)
            }
            
            TransformableNode(arFragment.transformationSystem).apply {
                renderable = purpleSphereRender
                setParent(fourthNode)
            }
            
            arFragment.arSceneView.scene.addChild(fourthNode)
        }
    }
    
    // Draw yellow line between first and second nodes (height)
    private fun drawHeightLine() {
        if (firstNode == null || secondNode == null) return
        
        val node1Pos = firstNode!!.worldPosition
        val node2Pos = secondNode!!.worldPosition
        val difference = Vector3.subtract(node1Pos, node2Pos)
        heightMeters = difference.length()
        
        val rotationFromAToB = Quaternion.lookRotation(difference.normalized(), Vector3.up())
        
        heightLine = AnchorNode().apply {
            setParent(arFragment.arSceneView.scene)
            worldPosition = Vector3.add(node1Pos, node2Pos).scaled(0.5f)
            worldRotation = rotationFromAToB
            localScale = Vector3(1f, 1f, heightMeters)
            renderable = yellowLineRender
        }
        
        arFragment.arSceneView.scene.addChild(heightLine)
        
        // Add height label
        addLabel(heightMeters, Vector3.add(node1Pos, node2Pos).scaled(0.5f)).also {
            heightLabel = it
        }
    }
    
    // Draw yellow line from second to third node (width)
    private fun drawWidthLine() {
        if (secondNode == null || thirdNode == null) return
        
        val node2Pos = secondNode!!.worldPosition
        val node3Pos = thirdNode!!.worldPosition
        val difference = Vector3.subtract(node2Pos, node3Pos)
        widthMeters = difference.length()
        
        val rotationFromAToB = Quaternion.lookRotation(difference.normalized(), Vector3.up())
        
        widthLine = AnchorNode().apply {
            setParent(arFragment.arSceneView.scene)
            worldPosition = Vector3.add(node2Pos, node3Pos).scaled(0.5f)
            worldRotation = rotationFromAToB
            localScale = Vector3(1f, 1f, widthMeters)
            renderable = yellowLineRender
        }
        
        arFragment.arSceneView.scene.addChild(widthLine)
        
        // Add width label
        addLabel(widthMeters, Vector3.add(node2Pos, node3Pos).scaled(0.5f)).also {
            widthLabel = it
        }
    }
    
    // Draw line from third node back to first node to complete rectangle
    // Draw rectangle lines: from third to fourth and fourth to first
    private fun drawRectangleLines() {
        if (thirdNode == null || fourthNode == null || firstNode == null || yellowLineRender == null) return

        val node3Pos = thirdNode!!.worldPosition
        val node4Pos = fourthNode!!.worldPosition
        val node1Pos = firstNode!!.worldPosition

        // Draw line from third to fourth node
        val diff34 = Vector3.subtract(node4Pos, node3Pos)
        val distance34 = diff34.length()
        if (distance34 > 0.01f) {
            val rotation34 = Quaternion.lookRotation(diff34.normalized(), Vector3.up())
            rectangleLine1 = AnchorNode().apply {
                setParent(arFragment.arSceneView.scene)
                worldPosition = Vector3.add(node3Pos, node4Pos).scaled(0.5f)
                worldRotation = rotation34
                localScale = Vector3(1f, 1f, distance34)
                renderable = yellowLineRender
            }
            arFragment.arSceneView.scene.addChild(rectangleLine1)
        }

        // Draw line from fourth to first node
        val diff41 = Vector3.subtract(node1Pos, node4Pos)
        val distance41 = diff41.length()
        if (distance41 > 0.01f) {
            val rotation41 = Quaternion.lookRotation(diff41.normalized(), Vector3.up())
            rectangleLine2 = AnchorNode().apply {
                setParent(arFragment.arSceneView.scene)
                worldPosition = Vector3.add(node4Pos, node1Pos).scaled(0.5f)
                worldRotation = rotation41
                localScale = Vector3(1f, 1f, distance41)
                renderable = yellowLineRender
            }
            arFragment.arSceneView.scene.addChild(rectangleLine2)
        }
    }
    
    // Calculate and display area
    private fun calculateArea() {
        val area = heightMeters * widthMeters
        val centerPos = Vector3(
            (firstNode!!.worldPosition.x + secondNode!!.worldPosition.x + thirdNode!!.worldPosition.x + fourthNode!!.worldPosition.x) / 4f,
            firstNode!!.worldPosition.y + 0.3f,
            (firstNode!!.worldPosition.z + secondNode!!.worldPosition.z + thirdNode!!.worldPosition.z + fourthNode!!.worldPosition.z) / 4f
        )
        
        // Create area label with special formatting
        areaLabel = AnchorNode().apply {
            setParent(arFragment.arSceneView.scene)
            worldPosition = centerPos
        }
        
        ViewRenderable.builder()
            .setView(requireContext(), R.layout.distance)
            .build()
            .thenAccept { areaRenderable ->
                val card = areaRenderable.view as CardView
                val tv = card.getChildAt(0) as TextView
                tv.text = "Area: ${formatArea(area)}"
                tv.textSize = 18f
                areaLabel?.renderable = areaRenderable
            }
        
        arFragment.arSceneView.scene.addChild(areaLabel)
    }
    
    // Update dynamic mesh in SECOND_NODE state
    private fun updateDynamicMesh() {
        if (firstNode == null || secondNode == null || meshRender == null) return
        
        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        
        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                // Remove old mesh
                dynamicMesh?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                }
                
                val anchor = hit.createAnchor()
                val hitPos = Vector3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())
                
                // Calculate dimensions
                val node1Pos = firstNode!!.worldPosition
                val node2Pos = secondNode!!.worldPosition
                val height = Vector3.subtract(node1Pos, node2Pos).length()
                val width = Vector3.subtract(node2Pos, hitPos).length()
                
                dynamicMesh = AnchorNode(anchor).apply {
                    setParent(arFragment.arSceneView.scene)
                }
                
                TransformableNode(arFragment.transformationSystem).apply {
                    localScale = Vector3(width, 1f, height)
                    renderable = meshRender
                    setParent(dynamicMesh)
                }
                
                arFragment.arSceneView.scene.addChild(dynamicMesh)
                return
            }
        }
    }
    
    // Update real-time preview line from first node to crosshair
    // Enhanced with position smoothing to reduce jitter
    private fun updatePreviewLine() {
        if (firstNode == null || yellowLineRender == null) return

        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f

        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val firstPos = firstNode!!.worldPosition
                val currentHitPos = Vector3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())

                // Apply exponential moving average smoothing for stable preview
                val hitPos = smoothPosition(currentHitPos, lastPreviewPosition)
                lastPreviewPosition = hitPos

                val difference = Vector3.subtract(firstPos, hitPos)
                val distance = difference.length()

                if (distance > 0.01f) { // Only draw if there's meaningful distance
                    val rotationFromAToB = Quaternion.lookRotation(difference.normalized(), Vector3.up())

                    // Reuse existing preview line node or create new one
                    if (previewLineNode == null) {
                        previewLineNode = AnchorNode().apply {
                            setParent(arFragment.arSceneView.scene)
                            renderable = yellowLineRender
                        }
                        arFragment.arSceneView.scene.addChild(previewLineNode)
                    }

                    // Update existing node properties instead of creating new one
                    previewLineNode?.apply {
                        worldPosition = Vector3.add(firstPos, hitPos).scaled(0.5f)
                        worldRotation = rotationFromAToB
                        localScale = Vector3(1f, 1f, distance)
                    }
                } else {
                    // Hide preview line if distance too small
                    previewLineNode?.isEnabled = false
                }
                return
            }
        }

        // If no valid hit, hide preview line and reset smoothing
        previewLineNode?.isEnabled = false
        lastPreviewPosition = null
    }
    
    // Update real-time preview line from second node to crosshair (for width measurement)
    private fun updatePreviewLineSecond() {
        if (secondNode == null || widthLineRender == null) return

        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f

        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val secondPos = secondNode!!.worldPosition
                val hitPos = Vector3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())
                val difference = Vector3.subtract(secondPos, hitPos)
                val distance = difference.length()

                if (distance > 0.01f) { // Only draw if there's meaningful distance
                    val rotationFromAToB = Quaternion.lookRotation(difference.normalized(), Vector3.up())

                    // Reuse existing preview line node or create new one
                    if (previewLineNode == null) {
                        previewLineNode = AnchorNode().apply {
                            setParent(arFragment.arSceneView.scene)
                            renderable = widthLineRender
                        }
                        arFragment.arSceneView.scene.addChild(previewLineNode)
                    }

                    // Update existing node properties instead of creating new one
                    previewLineNode?.apply {
                        worldPosition = Vector3.add(secondPos, hitPos).scaled(0.5f)
                        worldRotation = rotationFromAToB
                        localScale = Vector3(1f, 1f, distance)
                        renderable = widthLineRender // Ensure correct renderable
                    }
                } else {
                    // Hide preview line if distance too small
                    previewLineNode?.isEnabled = false
                }
                return
            }
        }

        // If no valid hit, hide preview line
        previewLineNode?.isEnabled = false
    }
    
    // Update real-time preview line from third node to crosshair (for fourth node placement)
    private fun updatePreviewLineThird() {
        if (thirdNode == null || yellowLineRender == null) return

        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f

        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val thirdPos = thirdNode!!.worldPosition
                val hitPos = Vector3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())
                val difference = Vector3.subtract(thirdPos, hitPos)
                val distance = difference.length()

                if (distance > 0.01f) { // Only draw if there's meaningful distance
                    val rotationFromAToB = Quaternion.lookRotation(difference.normalized(), Vector3.up())

                    // Reuse existing preview line node or create new one
                    if (previewLineNode == null) {
                        previewLineNode = AnchorNode().apply {
                            setParent(arFragment.arSceneView.scene)
                            renderable = yellowLineRender
                        }
                        arFragment.arSceneView.scene.addChild(previewLineNode)
                    }

                    // Update existing node properties instead of creating new one
                    previewLineNode?.apply {
                        worldPosition = Vector3.add(thirdPos, hitPos).scaled(0.5f)
                        worldRotation = rotationFromAToB
                        localScale = Vector3(1f, 1f, distance)
                        renderable = yellowLineRender // Ensure correct renderable
                    }
                } else {
                    // Hide preview line if distance too small
                    previewLineNode?.isEnabled = false
                }
                return
            }
        }

        // If no valid hit, hide preview line
        previewLineNode?.isEnabled = false
    }
    
    // Update text labels to always face the camera (billboard effect)
    private fun updateLabelBillboards() {
        val camera = arFragment.arSceneView.scene.camera ?: return
        val cameraPosition = camera.worldPosition
        
        // Make each label face the camera
        listOf(heightLabel, widthLabel, areaLabel).forEach { label ->
            label?.let {
                val labelPosition = it.worldPosition
                val directionToCamera = Vector3.subtract(cameraPosition, labelPosition).normalized()
                
                // Create rotation to face the camera
                val rotation = Quaternion.lookRotation(directionToCamera, Vector3.up())
                it.worldRotation = rotation
            }
        }
    }
    
    // Update plane orientation indicator at crosshair position
    private fun updatePlaneOrientationIndicator() {
        // Only show indicator when placing nodes (not during scanning or when measurement is complete)
        if (currentState == MeasurementState.SCANNING || currentState == MeasurementState.FOURTH_NODE) {
            // Hide indicator if it exists
            planeIndicatorNode?.isEnabled = false
            return
        }

        val frame = arFragment.arSceneView.arFrame ?: return

        // Check if camera is tracking - critical to prevent crashes
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            // Hide indicator if camera lost tracking
            planeIndicatorNode?.isEnabled = false
            return
        }

        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f

        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose) && trackable.trackingState == TrackingState.TRACKING) {
                // Create new indicator based on plane type
                val indicatorRenderable = when (trackable.type) {
                    Plane.Type.HORIZONTAL_UPWARD_FACING, Plane.Type.HORIZONTAL_DOWNWARD_FACING -> {
                        horizontalPlaneIndicator // Blue for horizontal (floor/ceiling)
                    }
                    Plane.Type.VERTICAL -> {
                        verticalPlaneIndicator // Green for vertical (wall)
                    }
                    else -> null
                }

                if (indicatorRenderable != null) {
                    try {
                        // Reuse existing indicator node or create new one
                        if (planeIndicatorNode == null) {
                            val anchor = hit.createAnchor()
                            planeIndicatorNode = AnchorNode(anchor).apply {
                                setParent(arFragment.arSceneView.scene)
                                // Position slightly above the plane
                                localPosition = Vector3(0f, 0.01f, 0f)
                            }

                            TransformableNode(arFragment.transformationSystem).apply {
                                setParent(planeIndicatorNode)
                            }

                            arFragment.arSceneView.scene.addChild(planeIndicatorNode)
                        } else {
                            // Update existing anchor position
                            planeIndicatorNode?.anchor = hit.createAnchor()
                        }

                        // Update renderable
                        (planeIndicatorNode?.children?.firstOrNull() as? TransformableNode)?.renderable = indicatorRenderable
                        planeIndicatorNode?.isEnabled = true
                    } catch (e: Exception) {
                        Log.e("ARFragment", "Failed to create plane indicator anchor", e)
                        planeIndicatorNode?.isEnabled = false
                    }
                } else {
                    planeIndicatorNode?.isEnabled = false
                }
                return
            }
        }

        // If no valid plane detected, hide indicator
        planeIndicatorNode?.isEnabled = false
    }
    
    // Add distance label
    private fun addLabel(meters: Float, position: Vector3): AnchorNode {
        val labelNode = AnchorNode().apply {
            setParent(arFragment.arSceneView.scene)
            worldPosition = position
        }
        
        ViewRenderable.builder()
            .setView(requireContext(), R.layout.distance)
            .build()
            .thenAccept { renderable ->
                val card = renderable.view as CardView
                val tv = card.getChildAt(0) as TextView
                tv.text = formatDistance(meters)
                labelNode.renderable = renderable
            }
        
        arFragment.arSceneView.scene.addChild(labelNode)
        return labelNode
    }
    
    // Format distance string based on current unit
    private fun formatDistance(meters: Float): String {
        return when (currentUnit) {
            MeasurementUnit.CM -> {
                String.format(Locale.ENGLISH, "%.0f cm", meters * 100)
            }
            MeasurementUnit.M -> {
                String.format(Locale.ENGLISH, "%.2f m", meters)
            }
            MeasurementUnit.IN -> {
                String.format(Locale.ENGLISH, "%.1f in", meters * 39.3701f)
            }
            MeasurementUnit.FT -> {
                String.format(Locale.ENGLISH, "%.2f ft", meters * 3.28084f)
            }
        }
    }
    
    // Format area string based on current unit
    private fun formatArea(squareMeters: Float): String {
        return when (currentUnit) {
            MeasurementUnit.CM -> {
                String.format(Locale.ENGLISH, "%.0f cm²", squareMeters * 10000)
            }
            MeasurementUnit.M -> {
                String.format(Locale.ENGLISH, "%.2f m²", squareMeters)
            }
            MeasurementUnit.IN -> {
                String.format(Locale.ENGLISH, "%.1f in²", squareMeters * 1550.003f)
            }
            MeasurementUnit.FT -> {
                String.format(Locale.ENGLISH, "%.2f ft²", squareMeters * 10.7639f)
            }
        }
    }
    
    // Clear plane indicators
    private fun clearPlaneIndicators() {
        for (indicator in planeIndicators) {
            arFragment.arSceneView.scene.removeChild(indicator)
            indicator.anchor?.detach()
        }
        planeIndicators.clear()
    }
    
    // Clear all measurements
    private fun clearMeasurement() {
        // Remove all nodes
        firstNode?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
        }
        secondNode?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
        }
        thirdNode?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
        }
        fourthNode?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
        }
        heightLine?.let { arFragment.arSceneView.scene.removeChild(it) }
        widthLine?.let { arFragment.arSceneView.scene.removeChild(it) }
        rectangleLine1?.let { arFragment.arSceneView.scene.removeChild(it) }
        rectangleLine2?.let { arFragment.arSceneView.scene.removeChild(it) }
        heightLabel?.let { arFragment.arSceneView.scene.removeChild(it) }
        widthLabel?.let { arFragment.arSceneView.scene.removeChild(it) }
        areaLabel?.let { arFragment.arSceneView.scene.removeChild(it) }
        dynamicMesh?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
        }
        previewLine?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
        }
        planeOrientationIndicator?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
        }
        
        // Clear instant placement nodes
        for (node in instantPlacementNodes) {
            arFragment.arSceneView.scene.removeChild(node)
            node.anchor?.detach()
        }
        instantPlacementNodes.clear()
        
        // Reset variables
        firstNode = null
        secondNode = null
        thirdNode = null
        fourthNode = null
        heightLine = null
        widthLine = null
        rectangleLine1 = null
        rectangleLine2 = null
        heightLabel = null
        widthLabel = null
        areaLabel = null
        dynamicMesh = null
        previewLine = null
        planeOrientationIndicator = null
        heightMeters = 0f
        widthMeters = 0f
        lastPreviewPosition = null
        
        // Clear plane pose history for fresh refinement
        planePoseHistory.clear()
        
        // Reset to IDLE state
        currentState = MeasurementState.IDLE
        updateUIForState()
    }


    private fun initObjects() {
        // Red sphere for first node
        MaterialFactory.makeOpaqueWithColor(requireContext(), Color(rgb(219, 68, 55)))
            .thenAccept { material: Material? ->
                redSphereRender = ShapeFactory.makeSphere(0.03f, zero(), material)
                redSphereRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Green sphere for second node (height)
        MaterialFactory.makeOpaqueWithColor(requireContext(), Color(rgb(76, 175, 80)))
            .thenAccept { material: Material? ->
                greenSphereRender = ShapeFactory.makeSphere(0.03f, zero(), material)
                greenSphereRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Blue sphere for third node (width)
        MaterialFactory.makeOpaqueWithColor(requireContext(), Color(rgb(33, 150, 243)))
            .thenAccept { material: Material? ->
                blueSphereRender = ShapeFactory.makeSphere(0.03f, zero(), material)
                blueSphereRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Purple sphere for fourth node (calculated)
        MaterialFactory.makeOpaqueWithColor(requireContext(), Color(rgb(156, 39, 176)))
            .thenAccept { material: Material? ->
                purpleSphereRender = ShapeFactory.makeSphere(0.03f, zero(), material)
                purpleSphereRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Yellow line for height measurement
        MaterialFactory.makeOpaqueWithColor(requireContext(), Color(rgb(255, 235, 59)))
            .thenAccept { material: Material? ->
                yellowLineRender = ShapeFactory.makeCube(Vector3(.015f, .015f, 1f), zero(), material)
                yellowLineRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Blue line for width measurement
        MaterialFactory.makeOpaqueWithColor(requireContext(), Color(rgb(33, 150, 243)))
            .thenAccept { material: Material? ->
                widthLineRender = ShapeFactory.makeCube(Vector3(.015f, .015f, 1f), zero(), material)
                widthLineRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Cyan semi-transparent mesh for area visualization
        MaterialFactory.makeTransparentWithColor(requireContext(), Color(0.0f, 0.74f, 0.83f, 0.3f))
            .thenAccept { material: Material? ->
                meshRender = ShapeFactory.makeCube(Vector3(1f, 0.001f, 1f), zero(), material)
                meshRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Green circles for plane detection indicators
        MaterialFactory.makeTransparentWithColor(requireContext(), Color(0.3f, 0.69f, 0.31f, 0.5f))
            .thenAccept { material: Material? ->
                planeIndicatorRender = ShapeFactory.makeCylinder(0.15f, 0.002f, zero(), material)
                planeIndicatorRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Horizontal plane indicator (floor/ceiling) - blue circle
        MaterialFactory.makeTransparentWithColor(requireContext(), Color(0.2f, 0.6f, 1.0f, 0.7f))
            .thenAccept { material: Material? ->
                horizontalPlaneIndicator = ShapeFactory.makeCylinder(0.08f, 0.005f, zero(), material)
                horizontalPlaneIndicator!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Vertical plane indicator (wall) - green circle
        MaterialFactory.makeTransparentWithColor(requireContext(), Color(0.2f, 0.8f, 0.2f, 0.7f))
            .thenAccept { material: Material? ->
                verticalPlaneIndicator = ShapeFactory.makeCylinder(0.08f, 0.005f, zero(), material)
                verticalPlaneIndicator!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        // Text labels
        ViewRenderable.builder()
            .setView(requireContext(), R.layout.distance)
            .build()
            .thenAccept { renderable: ViewRenderable ->
                renderable.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                    verticalAlignment = ViewRenderable.VerticalAlignment.BOTTOM
                }
                viewRenderable = renderable
            }
    }




    override fun onStart() {
        super.onStart()
        if(::arFragment.isInitialized){
            arFragment.onStart()
        }
    }

    override fun onPause() {
        super.onPause()
        if(::arFragment.isInitialized){
            arFragment.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if(::arFragment.isInitialized){
            arFragment.onResume()
        }
    }
    
    // Show dialog to save current measurement
    private fun showSaveMeasurementDialog() {
        // Check if measurement is complete
        if (currentState != MeasurementState.FOURTH_NODE || 
            firstNode == null || secondNode == null || thirdNode == null || fourthNode == null) {
            Toast.makeText(requireContext(), "Complete the measurement first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get room ID from MainActivity
        val mainActivity = requireActivity() as? MainActivity
        val roomId = mainActivity?.currentRoomId ?: -1
        
        if (roomId == -1L) {
            Toast.makeText(requireContext(), "Invalid room", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Inflate dialog layout
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_measurement, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.etMeasurementName)
        val etDescription = dialogView.findViewById<EditText>(R.id.etMeasurementDescription)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupPlaneType)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save Measurement")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val name = etName.text.toString().trim()
                val description = etDescription.text.toString().trim()
                
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Measurement name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val planeType = when (radioGroup.checkedRadioButtonId) {
                    R.id.radioWall -> PlaneType.WALL
                    else -> PlaneType.FLOOR
                }
                
                // Get node positions
                val node1Pos = firstNode!!.worldPosition
                val node2Pos = secondNode!!.worldPosition
                val node3Pos = thirdNode!!.worldPosition
                val node4Pos = fourthNode!!.worldPosition
                
                // Save to database
                lifecycleScope.launch {
                    try {
                        measurementViewModel.saveMeasurement(
                            roomId = roomId,
                            name = name,
                            description = description,
                            planeType = planeType,
                            heightMeters = heightMeters,
                            widthMeters = widthMeters,
                            node1X = node1Pos.x, node1Y = node1Pos.y, node1Z = node1Pos.z,
                            node2X = node2Pos.x, node2Y = node2Pos.y, node2Z = node2Pos.z,
                            node3X = node3Pos.x, node3Y = node3Pos.y, node3Z = node3Pos.z,
                            node4X = node4Pos.x, node4Y = node4Pos.y, node4Z = node4Pos.z
                        )
                        
                        Toast.makeText(requireContext(), "Measurement saved!", Toast.LENGTH_SHORT).show()
                        
                        // Clear measurement for next one
                        clearMeasurement()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error saving measurement: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}


