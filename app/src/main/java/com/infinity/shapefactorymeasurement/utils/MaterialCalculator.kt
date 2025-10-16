package com.infinity.roometric.utils

object MaterialCalculator {
    
    // Paint calculator for walls
    data class PaintResult(
        val litersNeeded: Float,
        val baseArea: Float,
        val wastePercentage: Float = 15f
    )
    
    // Tiles calculator for floors
    data class TilesResult(
        val tileCount: Int,
        val areaWithWaste: Float,
        val baseArea: Float,
        val wastePercentage: Float = 10f
    )
    
    // Cement/Adhesive calculator
    data class CementResult(
        val cementKg: Float,
        val baseArea: Float,
        val thickness: Float = 0.005f // 5mm default thickness
    )
    
    /**
     * Calculate paint required for walls
     * @param areaMeters Total wall area in square meters
     * @param coveragePerLiter Coverage per liter in m² (default: 10 m²/liter)
     * @param wastePercentage Waste allowance (default: 15%)
     * @return PaintResult with liters needed
     */
    fun calculatePaint(
        areaMeters: Float,
        coveragePerLiter: Float = 10f,
        wastePercentage: Float = 15f
    ): PaintResult {
        val areaWithWaste = areaMeters * (1 + wastePercentage / 100)
        val litersNeeded = areaWithWaste / coveragePerLiter
        
        return PaintResult(
            litersNeeded = litersNeeded,
            baseArea = areaMeters,
            wastePercentage = wastePercentage
        )
    }
    
    /**
     * Calculate tiles required for floors
     * @param areaMeters Total floor area in square meters
     * @param tileSizeMeters Size of one tile in square meters (default: 0.09 m² for 30x30cm tiles)
     * @param wastePercentage Waste allowance (default: 10%)
     * @return TilesResult with tile count
     */
    fun calculateTiles(
        areaMeters: Float,
        tileSizeMeters: Float = 0.09f, // 30cm x 30cm tile
        wastePercentage: Float = 10f
    ): TilesResult {
        val areaWithWaste = areaMeters * (1 + wastePercentage / 100)
        val tileCount = Math.ceil((areaWithWaste / tileSizeMeters).toDouble()).toInt()
        
        return TilesResult(
            tileCount = tileCount,
            areaWithWaste = areaWithWaste,
            baseArea = areaMeters,
            wastePercentage = wastePercentage
        )
    }
    
    /**
     * Calculate cement/adhesive required for floors
     * @param areaMeters Total floor area in square meters
     * @param thickness Application thickness in meters (default: 5mm = 0.005m)
     * @param density Cement density in kg/m³ (default: 1400 kg/m³)
     * @return CementResult with kg needed
     */
    fun calculateCement(
        areaMeters: Float,
        thickness: Float = 0.005f, // 5mm
        density: Float = 1400f // kg/m³
    ): CementResult {
        val volume = areaMeters * thickness
        val cementKg = volume * density
        
        return CementResult(
            cementKg = cementKg,
            baseArea = areaMeters,
            thickness = thickness
        )
    }
    
    /**
     * Calculate grout for tiles
     * @param areaMeters Total floor area in square meters
     * @param groutWidth Width of grout lines in meters (default: 3mm = 0.003m)
     * @return Grout needed in kg
     */
    fun calculateGrout(
        areaMeters: Float,
        groutWidth: Float = 0.003f, // 3mm
        groutDepth: Float = 0.005f // 5mm
    ): Float {
        // Simplified grout calculation
        // Approximately 1.5 kg per m² for standard tiles with 3mm joints
        val groutPerSqMeter = 1.5f
        return areaMeters * groutPerSqMeter
    }
}
