package com.infinity.roometric.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infinity.roometric.MainActivity
import com.infinity.roometric.R
import com.infinity.roometric.data.Measurement
import com.infinity.roometric.data.PlaneType
import com.infinity.roometric.databinding.FragmentEstimateBinding
import com.infinity.roometric.utils.MaterialCalculator
import com.infinity.roometric.viewmodel.MeasurementViewModel
import kotlinx.coroutines.launch

class EstimateFragment : Fragment() {
    private var _binding: FragmentEstimateBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MeasurementViewModel by viewModels()
    private lateinit var measurementAdapter: MeasurementAdapter
    
    private var currentRoomId: Long = -1
    private var allMeasurements: List<Measurement> = emptyList()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEstimateBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get room ID from MainActivity
        val mainActivity = requireActivity() as? MainActivity
        currentRoomId = mainActivity?.currentRoomId ?: -1
        
        if (currentRoomId == -1L) {
            Toast.makeText(requireContext(), "Invalid room", Toast.LENGTH_SHORT).show()
            return
        }
        
        setupRecyclerView()
        setupClickListeners()
        observeMeasurements()
    }
    
    private fun setupRecyclerView() {
        measurementAdapter = MeasurementAdapter(
            onEditClick = { measurement -> showEditMeasurementDialog(measurement) },
            onDeleteClick = { measurement -> showDeleteMeasurementDialog(measurement) }
        )
        binding.recyclerViewMeasurements.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = measurementAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.btnCalculateMaterials.setOnClickListener {
            showMaterialCalculatorDialog()
        }
    }
    
    private fun observeMeasurements() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getMeasurementsForRoom(currentRoomId).collect { measurements ->
                    allMeasurements = measurements
                    updateUI(measurements)
                }
            }
        }
    }
    
    private fun updateUI(measurements: List<Measurement>) {
        if (measurements.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.contentLayout.visibility = View.GONE
            return
        }
        
        binding.emptyStateLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
        
        // Calculate stats
        val floorCount = measurements.count { it.planeType == PlaneType.FLOOR }
        val wallCount = measurements.count { it.planeType == PlaneType.WALL }
        val totalMeasurements = measurements.size
        
        binding.tvFloorCount.text = floorCount.toString()
        binding.tvWallCount.text = wallCount.toString()
        binding.tvTotalCount.text = totalMeasurements.toString()
        
        // Calculate total areas
        val totalFloorArea = measurements.filter { it.planeType == PlaneType.FLOOR }
            .sumOf { it.areaMeters.toDouble() }.toFloat()
        val totalWallArea = measurements.filter { it.planeType == PlaneType.WALL }
            .sumOf { it.areaMeters.toDouble() }.toFloat()
        
        binding.tvFloorArea.text = String.format("%.2f m²", totalFloorArea)
        binding.tvWallArea.text = String.format("%.2f m²", totalWallArea)
        
        // Group measurements by type
        val groupedMeasurements = mutableListOf<MeasurementItem>()
        
        if (floorCount > 0) {
            groupedMeasurements.add(MeasurementItem.Header("Floors ($floorCount)"))
            measurements.filter { it.planeType == PlaneType.FLOOR }
                .forEach { groupedMeasurements.add(MeasurementItem.Data(it)) }
        }
        
        if (wallCount > 0) {
            groupedMeasurements.add(MeasurementItem.Header("Walls ($wallCount)"))
            measurements.filter { it.planeType == PlaneType.WALL }
                .forEach { groupedMeasurements.add(MeasurementItem.Data(it)) }
        }
        
        measurementAdapter.submitList(groupedMeasurements)
    }
    
    private fun showMaterialCalculatorDialog() {
        if (allMeasurements.isEmpty()) {
            Toast.makeText(requireContext(), "No measurements to calculate", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Calculate materials
        val floorMeasurements = allMeasurements.filter { it.planeType == PlaneType.FLOOR }
        val wallMeasurements = allMeasurements.filter { it.planeType == PlaneType.WALL }
        
        val totalFloorArea = floorMeasurements.sumOf { it.areaMeters.toDouble() }.toFloat()
        val totalWallArea = wallMeasurements.sumOf { it.areaMeters.toDouble() }.toFloat()
        
        val paintResult = MaterialCalculator.calculatePaint(totalWallArea)
        val tilesResult = MaterialCalculator.calculateTiles(totalFloorArea)
        val cementResult = MaterialCalculator.calculateCement(totalFloorArea)
        
        // Build message
        val message = buildString {
            appendLine("Floor Materials:")
            appendLine("• Tiles: ${tilesResult.tileCount} tiles (${String.format("%.2f", tilesResult.areaWithWaste)} m²)")
            appendLine("• Cement/Adhesive: ${String.format("%.2f", cementResult.cementKg)} kg")
            appendLine()
            appendLine("Wall Materials:")
            appendLine("• Paint: ${String.format("%.2f", paintResult.litersNeeded)} liters")
            appendLine()
            appendLine("Note: Calculations include waste allowance")
            appendLine("• Tiles: +10% waste")
            appendLine("• Paint: +15% waste")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Material Estimates")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showEditMeasurementDialog(measurement: Measurement) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_measurement, null)
        val etName = dialogView.findViewById<EditText>(R.id.etMeasurementName)
        val etDescription = dialogView.findViewById<EditText>(R.id.etMeasurementDescription)
        val etHeight = dialogView.findViewById<EditText>(R.id.etHeight)
        val etWidth = dialogView.findViewById<EditText>(R.id.etWidth)
        val spinnerPlaneType = dialogView.findViewById<Spinner>(R.id.spinnerPlaneType)
        
        // Pre-fill current values
        etName.setText(measurement.name)
        etDescription.setText(measurement.description)
        etHeight.setText(String.format("%.2f", measurement.heightMeters))
        etWidth.setText(String.format("%.2f", measurement.widthMeters))
        
        // Setup plane type spinner
        val planeTypes = arrayOf("FLOOR", "WALL")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, planeTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPlaneType.adapter = adapter
        spinnerPlaneType.setSelection(if (measurement.planeType == PlaneType.FLOOR) 0 else 1)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Measurement")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val description = etDescription.text.toString().trim()
                val heightStr = etHeight.text.toString().trim()
                val widthStr = etWidth.text.toString().trim()
                
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                try {
                    val height = heightStr.toFloat()
                    val width = widthStr.toFloat()
                    val area = height * width
                    val planeType = if (spinnerPlaneType.selectedItemPosition == 0) PlaneType.FLOOR else PlaneType.WALL
                    
                    val updatedMeasurement = measurement.copy(
                        name = name,
                        description = description,
                        heightMeters = height,
                        widthMeters = width,
                        areaMeters = area,
                        planeType = planeType
                    )
                    
                    lifecycleScope.launch {
                        viewModel.updateMeasurement(updatedMeasurement)
                        Toast.makeText(requireContext(), "Measurement updated", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(requireContext(), "Invalid dimensions", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeleteMeasurementDialog(measurement: Measurement) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Measurement")
            .setMessage("Are you sure you want to delete '${measurement.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteMeasurement(measurement)
                    Toast.makeText(requireContext(), "Measurement deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
