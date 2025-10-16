# Quick Installation Guide

## 📱 Install on Android Device (Without Android Studio)

### Prerequisites

- Android device with ARCore support
- USB cable
- ADB (Android Debug Bridge) installed on your computer

### Steps

1. **Enable Developer Options on Your Phone:**

   - Go to `Settings` > `About Phone`
   - Tap `Build Number` 7 times
   - Developer Options will be enabled

2. **Enable USB Debugging:**

   - Go to `Settings` > `Developer Options`
   - Enable `USB Debugging`

3. **Build the APK:**

   ```bash
   cd /home/incygnia/Desktop/ArCoreMeasurement
   ./gradlew assembleDebug
   ```

4. **Connect Your Phone:**

   - Connect via USB cable
   - Allow USB debugging when prompted on phone

5. **Verify Connection:**

   ```bash
   adb devices
   ```

   You should see your device listed.

6. **Install the App:**

   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

7. **Launch the App:**
   ```bash
   adb shell am start -n com.infinity.roometric/.MainActivity
   ```

## 🎯 Using the App

### Phase 1: Scanning (0-100%)

1. Move your phone around slowly
2. Point at floors, tables, flat surfaces
3. Green circles will appear on detected planes
4. Progress bar shows scanning completion
5. Wait until 100% is reached

### Phase 2: Measurement

1. **First Point (Red):** Tap "New Measurement" at base point
2. **Place Second Point (Height)**
   - Point crosshair at the height you want to measure
   - **Yellow preview line appears** from red sphere to crosshair in real-time
   - Move camera to adjust measurement
   - Tap "New Measurement"
   - Green sphere appears with permanent yellow line
3. **Place Third Point (Width)**
   - Point crosshair at the width you want to measure
   - **Blue preview line appears** from green sphere to crosshair in real-time
   - Move camera to adjust measurement
   - Tap "New Measurement"
   - Blue sphere appears and **closed shape forms** connecting all three points
   - Height, width, and area are displayed

### Controls

- 🔴 **New Measurement Button:** Place nodes/reset
- 🗑️ **Clear Button:** Delete current measurement
- ✅ **Finish Button:** View in scene mode

## 🔧 Troubleshooting

### "Please wait for plane detection"

- Keep moving your device
- Point at flat, well-lit surfaces
- Wait for progress to reach 100%

### "Point at a detected surface"

- Make sure scanning is complete (100%)
- Aim crosshair at a surface with a green circle
- Try different angles

### App won't install

```bash
# Uninstall old version first
adb uninstall com.infinity.roometric

# Then install again
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📊 Measurement Tips

1. **Best Lighting:** Use in well-lit environments
2. **Flat Surfaces:** Works best on floors, tables, walls
3. **Steady Movement:** Move device slowly during scanning
4. **Accuracy:** Hold phone steady when placing nodes
5. **Distance:** Stand at comfortable distance from measurement area

## 🎨 Visual Guide

- 🎯 White Crosshair = Targeting center (always visible)
- 🔴 Red Sphere = Starting point
- 🟢 Green Sphere = Height measurement point
- 🔵 Blue Sphere = Width measurement point
- 🟡 Yellow Line = Height (Red → Green)
- 🔵 Blue Line = Width (Green → Blue)
- 🔵 Blue Line = Shape completion (Blue → Red)
- 🟢 Green Circles = Detected surfaces

**Closed Shape:** When all three points are placed, you'll see a closed triangular shape connecting Red → Green → Blue → Red!
