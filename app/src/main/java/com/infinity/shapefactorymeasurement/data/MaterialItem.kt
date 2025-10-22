package com.infinity.roometric.data

enum class MaterialCategory {
    FLOOR_MATERIAL, CEMENT, WALL_MATERIAL, FLOOR_TOOLS, WALL_TOOLS
}

data class MaterialItem(
    val id: Int,
    val category: MaterialCategory,
    val name: String,
    val unitSize: String,
    val unitWeight: String,
    val quantityUnit: String,
    val baseQuantity: Double,
    val minPrice: Double,
    val maxPrice: Double,
    val areaPerUnit: Double?,
    val coverageNote: String
)

object MaterialDatabase {
    val materials = listOf(
        // Floor Materials - Tiles
        MaterialItem(1, MaterialCategory.FLOOR_MATERIAL, "Tile", "20×20 cm", "0.88 kg/pc", "Piece", 25.0, 10.0, 54.0, 0.04, "N/A"),
        MaterialItem(2, MaterialCategory.FLOOR_MATERIAL, "Tile", "30×30 cm", "1.98 kg/pc", "Piece", 11.11, 20.0, 165.99, 0.09, "N/A"),
        MaterialItem(3, MaterialCategory.FLOOR_MATERIAL, "Tile", "40×40 cm", "3.52 kg/pc", "Piece", 6.25, 29.0, 117.49, 0.16, "N/A"),
        MaterialItem(4, MaterialCategory.FLOOR_MATERIAL, "Tile", "30×60 cm", "3.96 kg/pc", "Piece", 5.56, 88.0, 160.49, 0.18, "N/A"),
        MaterialItem(5, MaterialCategory.FLOOR_MATERIAL, "Tile", "60×60 cm", "7.92 kg/pc", "Piece", 2.78, 59.0, 467.49, 0.36, "N/A"),
        
        // Floor Materials - Adhesive
        MaterialItem(6, MaterialCategory.FLOOR_MATERIAL, "Tile Adhesive", "N/A", "5 kg", "Bag", 0.2, 150.0, 220.0, null, "1.5−2.0 m²"),
        MaterialItem(7, MaterialCategory.FLOOR_MATERIAL, "Tile Adhesive", "N/A", "9 kg", "Bag", 0.11, 250.0, 450.0, null, "3.0−3.5 m²"),
        MaterialItem(8, MaterialCategory.FLOOR_MATERIAL, "Tile Adhesive", "N/A", "25 kg", "Bag", 1.0, 190.0, 825.5, null, "7.0 m² (Average)"),
        MaterialItem(9, MaterialCategory.FLOOR_MATERIAL, "Tile Adhesive", "N/A", "40 kg", "Bag", 0.625, 320.0, 580.0, null, "11.0 m² (Average)"),
        
        // Floor Materials - Grout
        MaterialItem(10, MaterialCategory.FLOOR_MATERIAL, "Tile Grout", "N/A", "1 kg", "Pouch", 0.5, 73.0, 145.0, null, "4.0 m² (Average)"),
        MaterialItem(11, MaterialCategory.FLOOR_MATERIAL, "Tile Grout", "N/A", "2 kg", "Bag", 1.0, 48.0, 95.0, null, "7.0 m² (Average)"),
        
        // Cement
        MaterialItem(12, MaterialCategory.CEMENT, "Cement", "N/A", "1kg", "Bag/Pouch", 6.8, 9.64, 30.0, null, "For very small repairs only"),
        MaterialItem(13, MaterialCategory.CEMENT, "Cement", "N/A", "40kg", "Bag", 0.17, 225.0, 328.0, null, "6 m² (20 mm thick, 1:4 mix)"),
        MaterialItem(14, MaterialCategory.CEMENT, "Cement", "N/A", "50kg", "Bag", 0.14, 245.0, 328.0, null, "7.5 m² (20 mm thick, 1:4 mix)"),
        MaterialItem(15, MaterialCategory.CEMENT, "Sand", "N/A", "40kg", "Bag", 0.77, 38.25, 65.0, null, "1.3 m² (20 mm thick, 1:4 mix)"),
        MaterialItem(16, MaterialCategory.CEMENT, "Sand", "N/A", "50kg", "Bag", 0.62, 48.0, 80.0, null, "1.6 m² (20 mm thick, 1:4 mix)"),
        
        // Wall Materials - Paint
        MaterialItem(17, MaterialCategory.WALL_MATERIAL, "Paint", "1 Liter (Quart)", "N/A", "Can", 0.125, 148.0, 275.0, null, "6-8 m² per coat"),
        MaterialItem(18, MaterialCategory.WALL_MATERIAL, "Paint", "4 Liters (Gallon)", "N/A", "Can", 0.03, 366.0, 950.0, null, "25-35 m² per coat"),
        MaterialItem(19, MaterialCategory.WALL_MATERIAL, "Paint", "16 Liters (Pail)", "N/A", "Can", 0.007, 2680.0, 3200.0, null, "100-140 m² per coat"),
        
        // Floor Tools
        MaterialItem(20, MaterialCategory.FLOOR_TOOLS, "Tile Trowel", "Common Size (6mm-10mm)", "N/A", "Piece", 1.0, 130.0, 350.0, null, "Used to apply adhesive/mortar"),
        MaterialItem(21, MaterialCategory.FLOOR_TOOLS, "Tile Spacer (Pack)", "100 pcs pack", "N/A", "Pack", 1.0, 8.1, 190.0, null, "N/A"),
        
        // Wall Tools
        MaterialItem(22, MaterialCategory.WALL_TOOLS, "Paint Brush", "2\" to 4\" size", "N/A", "Piece", 1.0, 26.0, 120.0, null, "For corners and trim work"),
        MaterialItem(23, MaterialCategory.WALL_TOOLS, "Paint Roller", "7\" to 9\" size", "N/A", "Piece", 1.0, 69.0, 170.0, null, "For fast coverage on walls/ceiling")
    )
    
    fun getTiles(): List<MaterialItem> {
        return materials.filter { it.category == MaterialCategory.FLOOR_MATERIAL && it.name == "Tile" }
    }
    
    fun getTileAdhesives(): List<MaterialItem> {
        return materials.filter { it.category == MaterialCategory.FLOOR_MATERIAL && it.name == "Tile Adhesive" }
    }
    
    fun getTileGrouts(): List<MaterialItem> {
        return materials.filter { it.category == MaterialCategory.FLOOR_MATERIAL && it.name == "Tile Grout" }
    }
    
    fun getCements(): List<MaterialItem> {
        return materials.filter { it.category == MaterialCategory.CEMENT && it.name == "Cement" }
    }
    
    fun getSands(): List<MaterialItem> {
        return materials.filter { it.category == MaterialCategory.CEMENT && it.name == "Sand" }
    }
    
    fun getPaints(): List<MaterialItem> {
        return materials.filter { it.category == MaterialCategory.WALL_MATERIAL && it.name == "Paint" }
    }
    
    fun getFloorTools(): List<MaterialItem> {
        return materials.filter { it.category == MaterialCategory.FLOOR_TOOLS }
    }
    
    fun getWallTools(): List<MaterialItem> {
        return materials.filter { it.category == MaterialCategory.WALL_TOOLS }
    }
}