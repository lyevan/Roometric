package com.infinity.shapefactorymeasurement.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
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
import com.infinity.roometric.data.MaterialDatabase
import com.infinity.roometric.data.MaterialItem
import com.infinity.roometric.data.Measurement
import com.infinity.shapefactorymeasurement.views.MeasurementItem
import com.infinity.roometric.data.PlaneType
import com.infinity.roometric.databinding.DialogMaterialEstimateBinding
import com.infinity.roometric.databinding.DialogTileSelectionBinding
import com.infinity.roometric.databinding.FragmentEstimateBinding
import com.infinity.roometric.viewmodel.MeasurementViewModel
import kotlinx.coroutines.launch

class EstimateFragment : Fragment() {
    private var _binding: FragmentEstimateBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MeasurementViewModel by viewModels()
    private lateinit var measurementAdapter: MeasurementAdapter
    
    private var currentRoomId: Long = -1
    private var allMeasurements: List<Measurement> = emptyList()
    
    // Material calculation variables
    private var selectedTileSize: String = "30×30 cm"
    private var totalFloorArea: Float = 0f
    private var totalWallArea: Float = 0f
    
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
        // CHANGED: Button text updated to "Calculate Materials & Tools"
        binding.btnCalculateMaterials.text = "Calculate Materials & Tools"
        binding.btnCalculateMaterials.setOnClickListener {
            showTileSelectionDialog()
        }
    }
    
    private fun observeMeasurements() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getMeasurementsForRoom(currentRoomId).collect { measurements ->
                    allMeasurements = measurements
                    updateUI(measurements)
                    
                    // Calculate total areas for material estimation
                    totalFloorArea = measurements.filter { it.planeType == PlaneType.FLOOR }
                        .sumOf { it.areaMeters.toDouble() }.toFloat()
                    totalWallArea = measurements.filter { it.planeType == PlaneType.WALL }
                        .sumOf { it.areaMeters.toDouble() }.toFloat()
                }
            }
        }
    }
    
    // NEW: Tile Selection Dialog - Mandatory tile size selection
    private fun showTileSelectionDialog() {
        if (allMeasurements.isEmpty()) {
            Toast.makeText(requireContext(), "No measurements to calculate", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogBinding = DialogTileSelectionBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Before proceeding please choose:")
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        // Setup tile size dropdown with all options
        val tileSizes = MaterialDatabase.getTiles().map { it.unitSize }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tileSizes)
        dialogBinding.autoCompleteTileSize.setAdapter(adapter)
        dialogBinding.autoCompleteTileSize.setText(selectedTileSize, false)
        
        dialogBinding.btnCancelTile.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnDoneTile.setOnClickListener {
            val selectedSize = dialogBinding.autoCompleteTileSize.text.toString()
            if (selectedSize.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a tile size", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            selectedTileSize = selectedSize
            dialog.dismiss()
            showMaterialEstimateDialog()
        }
        
        dialog.show()
    }
    
    // NEW: Material Estimate Dialog with editable tile size
    private fun showMaterialEstimateDialog() {
        val dialogBinding = DialogMaterialEstimateBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        // Setup tile size dropdown for editing
        val tileSizes = MaterialDatabase.getTiles().map { it.unitSize }
        val tileAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tileSizes)
        dialogBinding.autoCompleteTileSizeEdit.setAdapter(tileAdapter)
        dialogBinding.autoCompleteTileSizeEdit.setText(selectedTileSize, false)
        
        // Calculate and display estimates
        updateMaterialEstimates(dialogBinding)
        
        // Update when tile size changes
        dialogBinding.autoCompleteTileSizeEdit.setOnItemClickListener { _, _, position, _ ->
            selectedTileSize = tileSizes[position]
            updateMaterialEstimates(dialogBinding)
        }
        
        dialogBinding.btnCancelEstimate.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnDoneEstimate.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    // NEW: Update material estimates based on calculations
    private fun updateMaterialEstimates(binding: DialogMaterialEstimateBinding) {
        if (totalFloorArea == 0f && totalWallArea == 0f) {
            Toast.makeText(requireContext(), "No area measurements available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get selected tile
        val selectedTile = MaterialDatabase.getTiles().find { it.unitSize == selectedTileSize }
        if (selectedTile == null || selectedTile.areaPerUnit == null) {
            Toast.makeText(requireContext(), "Invalid tile selection", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Calculate quantities with waste allowance
        val tileWasteMultiplier = 1.10 // 10% waste for tiles
        val paintWasteMultiplier = 1.15 // 15% waste for paint
        
        // Floor Calculations
        val floorAreaWithWaste = totalFloorArea * tileWasteMultiplier
        val tileQuantity = kotlin.math.ceil((floorAreaWithWaste / selectedTile.areaPerUnit!!.toFloat()).toDouble()).toInt()
        val tileMinCost = tileQuantity * selectedTile.minPrice
        val tileMaxCost = tileQuantity * selectedTile.maxPrice
        
        // Cement calculations (using 40kg bag as default)
        val cementItem = MaterialDatabase.getCements().find { it.unitWeight == "40kg" } ?: MaterialDatabase.getCements().first()
        val cementQuantity = totalFloorArea * cementItem.baseQuantity.toFloat()
        val cementMinCost = cementQuantity * cementItem.minPrice
        val cementMaxCost = cementQuantity * cementItem.maxPrice
        
        // Trowel calculation (always 1 piece)
        val trowelItem = MaterialDatabase.getFloorTools().find { it.name == "Tile Trowel" } ?: MaterialDatabase.getFloorTools().first()
        val trowelQuantity = 1
        val trowelMinCost = trowelQuantity * trowelItem.minPrice
        val trowelMaxCost = trowelQuantity * trowelItem.maxPrice
        
        // Floor totals
        val floorTotalMin = tileMinCost + cementMinCost + trowelMinCost
        val floorTotalMax = tileMaxCost + cementMaxCost + trowelMaxCost
        
        // Wall Calculations
        val wallAreaWithWaste = totalWallArea * paintWasteMultiplier
        val paintItem = MaterialDatabase.getPaints().find { it.unitSize == "1 Liter (Quart)" } ?: MaterialDatabase.getPaints().first()
        val paintQuantity = wallAreaWithWaste * paintItem.baseQuantity.toFloat()
        val paintMinCost = paintQuantity * paintItem.minPrice
        val paintMaxCost = paintQuantity * paintItem.maxPrice
        
        // Paintbrush calculation (always 1 piece)
        val paintbrushItem = MaterialDatabase.getWallTools().find { it.name == "Paint Brush" } ?: MaterialDatabase.getWallTools().first()
        val paintbrushQuantity = 1
        val paintbrushMinCost = paintbrushQuantity * paintbrushItem.minPrice
        val paintbrushMaxCost = paintbrushQuantity * paintbrushItem.maxPrice
        
        // Wall totals
        val wallTotalMin = paintMinCost + paintbrushMinCost
        val wallTotalMax = paintMaxCost + paintbrushMaxCost
        
        // Overall totals
        val overallTotalMin = floorTotalMin + wallTotalMin
        val overallTotalMax = floorTotalMax + wallTotalMax
        
        // Update UI with exact format you specified
        binding.tvTileQuantity.text = "• Tile Quantity: $tileQuantity ${selectedTile.quantityUnit}"
        binding.tvTilePrice.text = "• Price Estimate: ₱${"%.2f".format(tileMinCost)} - ₱${"%.2f".format(tileMaxCost)}"
        
        binding.tvCementQuantity.text = "• Cement Quantity: ${"%.2f".format(cementQuantity)} ${cementItem.unitWeight}"
        binding.tvCementPrice.text = "• Price Estimate: ₱${"%.2f".format(cementMinCost)} - ₱${"%.2f".format(cementMaxCost)}"
        
        binding.tvTrowelQuantity.text = "• Trowel Quantity: $trowelQuantity ${trowelItem.quantityUnit}"
        binding.tvTrowelPrice.text = "• Price Estimate: ₱${"%.2f".format(trowelMinCost)} - ₱${"%.2f".format(trowelMaxCost)}"
        
        binding.tvFloorTotal.text = "Total Cost Estimate of Floor Materials & Tools: ₱${"%.2f".format(floorTotalMin)} - ₱${"%.2f".format(floorTotalMax)}"
        
        binding.tvPaintQuantity.text = "• Paint Quantity: ${"%.2f".format(paintQuantity)} ${paintItem.unitSize.split(" ")[0]}"
        binding.tvPaintPrice.text = "• Price Estimate: ₱${"%.2f".format(paintMinCost)} - ₱${"%.2f".format(paintMaxCost)}"
        
        binding.tvPaintbrushQuantity.text = "• Paintbrush Quantity: $paintbrushQuantity ${paintbrushItem.quantityUnit}"
        binding.tvPaintbrushPrice.text = "• Price Estimate: ₱${"%.2f".format(paintbrushMinCost)} - ₱${"%.2f".format(paintbrushMaxCost)}"
        
        binding.tvWallTotal.text = "Total Cost Estimate of Wall Materials & Tools: ₱${"%.2f".format(wallTotalMin)} - ₱${"%.2f".format(wallTotalMax)}"
        
        binding.tvOverallTotal.text = "Overall Cost Estimate of Materials & Tools: ₱${"%.2f".format(overallTotalMin)} - ₱${"%.2f".format(overallTotalMax)}"
    }
    
    private fun updateUI(measurements: List<Measurement>) {
        if (measurements.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.contentLayout.visibility = View.GONE
            return
        }
        
        binding.emptyStateLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
        
        val floorCount = measurements.count { it.planeType == PlaneType.FLOOR }
        val wallCount = measurements.count { it.planeType == PlaneType.WALL }
        val totalMeasurements = measurements.size
        
        binding.tvFloorCount.text = floorCount.toString()
        binding.tvWallCount.text = wallCount.toString()
        binding.tvTotalCount.text = totalMeasurements.toString()
        
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
    
    private fun showEditMeasurementDialog(measurement: Measurement) {
        Toast.makeText(requireContext(), "Edit: ${measurement.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun showDeleteMeasurementDialog(measurement: Measurement) {
        Toast.makeText(requireContext(), "Delete: ${measurement.name}", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}