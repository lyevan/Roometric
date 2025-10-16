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
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
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
    // State management
    private var currentState = MeasurementState.SCANNING
    private var scanningProgress = 0
    private val detectedPlanes = HashSet<Plane>()
    private val planeIndicators = ArrayList<AnchorNode>()
    private var currentUnit = MeasurementUnit.CM  // Default to centimeters
    
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
    lateinit var arFragment: ArFragment
    private val viewModel: ViewModel by activityViewModels()
    private val measurementViewModel: MeasurementViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentArBinding.bind(view)
        arFragment = childFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment

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
            if (currentState == MeasurementState.SCANNING) {
                updatePlaneDetection()
            } else if (currentState == MeasurementState.FIRST_NODE) {
                updatePreviewLine()
            } else if (currentState == MeasurementState.SECOND_NODE) {
                updatePreviewLineSecond()
            } else if (currentState == MeasurementState.THIRD_NODE) {
                updatePreviewLineThird()
            }
            
            // Make text labels always face the camera (billboard effect)
            updateLabelBillboards()
            
            // Update plane orientation indicator at crosshair
            updatePlaneOrientationIndicator()
        }
    }
    // Update plane detection progress
    private fun updatePlaneDetection() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val planes = frame.getUpdatedTrackables(Plane::class.java)
        
        for (plane in planes) {
            if (plane.trackingState == TrackingState.TRACKING && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                if (!detectedPlanes.contains(plane)) {
                    detectedPlanes.add(plane)
                    addPlaneIndicator(plane)
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
            Toast.makeText(requireContext(), "Planes detected! Ready to measure", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Add visual indicator on detected planes
    private fun addPlaneIndicator(plane: Plane) {
        if (planeIndicatorRender == null) return
        
        val pose = plane.centerPose
        val anchor = plane.createAnchor(pose)
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
                binding.tvInstruction.text = "Tap 'New Measurement' to place the red base point"
                binding.btnAdd.isEnabled = true
                binding.btnAdd.alpha = 1f
                binding.btnSaveMeasurement.visibility = View.GONE
                // Remove plane indicators
                clearPlaneIndicators()
            }
            MeasurementState.FIRST_NODE -> {
                binding.tvInstructionTitle.text = "Step 2: Place Second Point"
                binding.tvInstruction.text = "Point at the second corner and tap 'Add Node'"
                binding.btnSaveMeasurement.visibility = View.GONE
            }
            MeasurementState.SECOND_NODE -> {
                binding.tvInstructionTitle.text = "Step 3: Place Third Point"
                binding.tvInstruction.text = "Point at the third corner and tap 'Add Node'"
                binding.btnSaveMeasurement.visibility = View.GONE
            }
            MeasurementState.THIRD_NODE -> {
                binding.tvInstructionTitle.text = "Step 4: Place Fourth Point"
                binding.tvInstruction.text = "Point at the fourth corner and tap 'Add Node'"
                binding.btnSaveMeasurement.visibility = View.GONE
            }
            MeasurementState.FOURTH_NODE -> {
                binding.tvInstructionTitle.text = "Measurement Complete!"
                binding.tvInstruction.text = "Height: ${formatDistance(heightMeters)}, Width: ${formatDistance(widthMeters)}, Area: ${formatArea(heightMeters * widthMeters)}"
                binding.btnSaveMeasurement.visibility = View.VISIBLE
            }
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
    private fun placeFirstNode() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        
        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
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
    private fun updatePreviewLine() {
        if (firstNode == null || yellowLineRender == null) return
        
        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        
        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                // Remove old preview line
                previewLine?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                }
                
                val firstPos = firstNode!!.worldPosition
                val hitPos = Vector3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())
                val difference = Vector3.subtract(firstPos, hitPos)
                val distance = difference.length()
                
                if (distance > 0.01f) { // Only draw if there's meaningful distance
                    val rotationFromAToB = Quaternion.lookRotation(difference.normalized(), Vector3.up())
                    
                    previewLine = AnchorNode().apply {
                        setParent(arFragment.arSceneView.scene)
                        worldPosition = Vector3.add(firstPos, hitPos).scaled(0.5f)
                        worldRotation = rotationFromAToB
                        localScale = Vector3(1f, 1f, distance)
                        renderable = yellowLineRender
                    }
                    
                    arFragment.arSceneView.scene.addChild(previewLine)
                }
                return
            }
        }
        
        // If no valid hit, remove preview line
        previewLine?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
            previewLine = null
        }
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
                // Remove old preview line
                previewLine?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                }
                
                val secondPos = secondNode!!.worldPosition
                val hitPos = Vector3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())
                val difference = Vector3.subtract(secondPos, hitPos)
                val distance = difference.length()
                
                if (distance > 0.01f) { // Only draw if there's meaningful distance
                    val rotationFromAToB = Quaternion.lookRotation(difference.normalized(), Vector3.up())
                    
                    previewLine = AnchorNode().apply {
                        setParent(arFragment.arSceneView.scene)
                        worldPosition = Vector3.add(secondPos, hitPos).scaled(0.5f)
                        worldRotation = rotationFromAToB
                        localScale = Vector3(1f, 1f, distance)
                        renderable = widthLineRender
                    }
                    
                    arFragment.arSceneView.scene.addChild(previewLine)
                }
                return
            }
        }
        
        // If no valid hit, remove preview line
        previewLine?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
            previewLine = null
        }
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
                // Remove old preview line
                previewLine?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                }
                
                val thirdPos = thirdNode!!.worldPosition
                val hitPos = Vector3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())
                val difference = Vector3.subtract(thirdPos, hitPos)
                val distance = difference.length()
                
                if (distance > 0.01f) { // Only draw if there's meaningful distance
                    val rotationFromAToB = Quaternion.lookRotation(difference.normalized(), Vector3.up())
                    
                    previewLine = AnchorNode().apply {
                        setParent(arFragment.arSceneView.scene)
                        worldPosition = Vector3.add(thirdPos, hitPos).scaled(0.5f)
                        worldRotation = rotationFromAToB
                        localScale = Vector3(1f, 1f, distance)
                        renderable = yellowLineRender
                    }
                    
                    arFragment.arSceneView.scene.addChild(previewLine)
                }
                return
            }
        }
        
        // If no valid hit, remove preview line
        previewLine?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
            previewLine = null
        }
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
            // Remove indicator if it exists
            planeOrientationIndicator?.let {
                arFragment.arSceneView.scene.removeChild(it)
                it.anchor?.detach()
                planeOrientationIndicator = null
            }
            return
        }
        
        val frame = arFragment.arSceneView.arFrame ?: return
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        
        val hits = frame.hitTest(centerX, centerY)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                // Remove old indicator
                planeOrientationIndicator?.let {
                    arFragment.arSceneView.scene.removeChild(it)
                    it.anchor?.detach()
                }
                
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
                    val anchor = hit.createAnchor()
                    planeOrientationIndicator = AnchorNode(anchor).apply {
                        setParent(arFragment.arSceneView.scene)
                        // Position slightly above the plane
                        localPosition = Vector3(0f, 0.01f, 0f)
                    }
                    
                    TransformableNode(arFragment.transformationSystem).apply {
                        renderable = indicatorRenderable
                        setParent(planeOrientationIndicator)
                    }
                    
                    arFragment.arSceneView.scene.addChild(planeOrientationIndicator)
                }
                return
            }
        }
        
        // If no valid plane detected, remove indicator
        planeOrientationIndicator?.let {
            arFragment.arSceneView.scene.removeChild(it)
            it.anchor?.detach()
            planeOrientationIndicator = null
        }
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


