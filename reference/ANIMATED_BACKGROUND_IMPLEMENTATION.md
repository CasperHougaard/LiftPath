# Animated Background Implementation Guide

This guide provides complete instructions for implementing the animated background pattern used throughout the fitness app. The background consists of subtle flowing waves that create visual interest without distracting from content.

## Overview

The animated background uses an **Animated Vector Drawable (AVD)** that creates three overlapping wave patterns that continuously flow across the screen. The animation:
- Uses three waves with different colors (primary, accent, primary_dark)
- Has different animation speeds (12s, 15s, 18s) for visual variety
- Uses very low opacity (0.03-0.05) to remain subtle
- Automatically loops infinitely
- Adapts to light/dark mode via color resources

## File Structure

### 1. Create the Animated Vector Drawable

Create `app/src/main/res/drawable/avd_background_flow.xml`:

```xml
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
        <vector
            android:width="400dp"
            android:height="800dp"
            android:viewportWidth="100"
            android:viewportHeight="200">

            <!-- Background Wave 1 (Primary) -->
            <group android:name="wave1">
                <path
                    android:pathData="M-100,100 Q-75,80 -50,100 Q-25,120 0,100 Q25,80 50,100 Q75,120 100,100 Q125,80 150,100 Q175,120 200,100 V200 H-100 Z"
                    android:fillColor="@color/fitness_primary"
                    android:fillAlpha="0.05"/>
            </group>

            <!-- Background Wave 2 (Accent) -->
            <group android:name="wave2">
                <path
                    android:pathData="M-100,130 Q-75,150 -50,130 Q-25,110 0,130 Q25,150 50,130 Q75,110 100,130 Q125,150 150,130 Q175,110 200,130 V200 H-100 Z"
                    android:fillColor="@color/fitness_accent"
                    android:fillAlpha="0.05"/>
            </group>

            <!-- Background Wave 3 (Dark/Secondary) -->
            <group android:name="wave3">
                <path
                    android:pathData="M-100,160 Q-75,140 -50,160 Q-25,180 0,160 Q25,140 50,160 Q75,180 100,160 Q125,140 150,160 Q175,180 200,160 V200 H-100 Z"
                    android:fillColor="@color/fitness_primary_dark"
                    android:fillAlpha="0.03"/>
            </group>

        </vector>
    </aapt:attr>

    <target android:name="wave1">
        <aapt:attr name="android:animation">
            <objectAnimator
                android:propertyName="translateX"
                android:valueFrom="0"
                android:valueTo="100"
                android:duration="12000"
                android:repeatCount="infinite"
                android:repeatMode="restart"
                android:interpolator="@android:anim/linear_interpolator"/>
        </aapt:attr>
    </target>

    <target android:name="wave2">
        <aapt:attr name="android:animation">
            <objectAnimator
                android:propertyName="translateX"
                android:valueFrom="0"
                android:valueTo="100"
                android:duration="15000"
                android:repeatCount="infinite"
                android:repeatMode="restart"
                android:interpolator="@android:anim/linear_interpolator"/>
        </aapt:attr>
    </target>

    <target android:name="wave3">
        <aapt:attr name="android:animation">
            <objectAnimator
                android:propertyName="translateX"
                android:valueFrom="0"
                android:valueTo="100"
                android:duration="18000"
                android:repeatCount="infinite"
                android:repeatMode="restart"
                android:interpolator="@android:anim/linear_interpolator"/>
        </aapt:attr>
    </target>

</animated-vector>
```

**Important Notes:**
- Requires `aapt` namespace for proper vector animation support
- Wave paths use Bezier curves (Q commands) for smooth flow
- Each wave has a unique `android:name` attribute matching its target
- Animation durations differ (12s, 15s, 18s) to prevent synchronization
- Uses `linear_interpolator` for constant speed
- `repeatMode="restart"` creates seamless looping

### 2. Ensure Color Resources Exist

Make sure these color resources exist in `app/src/main/res/values/colors.xml`:

```xml
<!-- Light Mode -->
<color name="fitness_primary">#2563EB</color>
<color name="fitness_primary_dark">#1E40AF</color>
<color name="fitness_accent">#F59E0B</color>
```

And in `app/src/main/res/values-night/colors.xml` for dark mode:

```xml
<!-- Dark Mode -->
<color name="fitness_primary">#60A5FA</color>
<color name="fitness_primary_dark">#3B82F6</color>
<color name="fitness_accent">#FBBF24</color>
```

## Layout Implementation

### Step 1: Root Container Structure

The animated background **MUST** be the first child in your root ConstraintLayout:

```xml
<androidx.constraintlayout.widget.ConstraintLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/fitness_background"
    android:fitsSystemWindows="true">

    <!-- Background Animation - MUST BE FIRST CHILD -->
    <ImageView
        android:id="@+id/image_bg_animation"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:alpha="1.0"
        android:scaleType="centerCrop"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:srcCompat="@drawable/avd_background_flow" />

    <!-- Scrollable Content -->
    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@android:color/transparent"
        android:clipToPadding="false"
        android:fillViewport="true">
        
        <!-- Your content here -->
        
    </androidx.core.widget.NestedScrollView>
    
</androidx.constraintlayout.widget.ConstraintLayout>
```

### Critical Layout Requirements

1. **First Child Position**: The ImageView **MUST** be the first child in the ConstraintLayout to ensure it renders behind all other content
2. **Full Screen Coverage**: Use `match_parent` for both width and height
3. **Transparent Content Background**: All content containers must have `transparent` background
4. **Constraints**: Constrain to all edges (`parent` on all sides) for full coverage
5. **Scale Type**: Use `centerCrop` to ensure animation fills the screen properly
6. **Alpha**: Set to `1.0` (subtle opacity is handled in the drawable itself)

## Kotlin Implementation

### Step 1: Start the Animation in onCreate

Add this method to your Activity:

```kotlin
private fun setupBackgroundAnimation() {
    val drawable = binding.imageBgAnimation.drawable
    if (drawable is Animatable) {
        drawable.start()
    }
}
```

### Step 2: Call in onCreate

Call `setupBackgroundAnimation()` in your `onCreate()` method **after** `setContentView()`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    binding = YourActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)
    
    // Start background animation
    setupBackgroundAnimation()
    
    // Rest of your setup code...
}
```

### Complete Example Activity

```kotlin
package com.yourpackage.activities

import android.graphics.drawable.Animatable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yourpackage.databinding.YourActivityBinding

class YourActivity : AppCompatActivity() {
    
    private lateinit var binding: YourActivityBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = YourActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // IMPORTANT: Start animation after setContentView
        setupBackgroundAnimation()
        
        // Rest of your initialization...
    }
    
    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }
}
```

## Troubleshooting Common Conflicts

### Issue 1: Animation Not Starting

**Symptoms**: Background is static, no animation visible

**Solutions**:
- ✅ Verify `setupBackgroundAnimation()` is called **after** `setContentView()`
- ✅ Check that drawable resource exists: `@drawable/avd_background_flow`
- ✅ Ensure ImageView ID matches: `@+id/image_bg_animation`
- ✅ Verify `app:srcCompat` (not `android:src`) is used
- ✅ Check that drawable extends `Animatable` interface

### Issue 2: Animation Not Visible

**Symptoms**: Animation runs but can't see it

**Solutions**:
- ✅ Check ImageView is first child in ConstraintLayout
- ✅ Verify `alpha="1.0"` on ImageView
- ✅ Ensure content has `transparent` background
- ✅ Check color resources exist and have proper values
- ✅ Verify `scaleType="centerCrop"` is set

### Issue 3: Animation Covers Content

**Symptoms**: Content is hidden behind animation

**Solutions**:
- ✅ Ensure ImageView is **first** child (renders first = behind everything)
- ✅ Verify all content containers have `transparent` backgrounds
- ✅ Check that ImageView doesn't have `android:elevation`
- ✅ Ensure ScrollView and content containers are **after** ImageView in XML

### Issue 4: Animation Performance Issues

**Symptoms**: Animation causes lag or stutter

**Solutions**:
- ✅ Use `app:srcCompat` instead of `android:src` (better performance)
- ✅ Ensure `scaleType="centerCrop"` to optimize rendering
- ✅ Check that `clipToPadding="false"` on ScrollView
- ✅ Verify animation uses hardware acceleration (default on Android 5.0+)
- ✅ Consider reducing wave count if performance is critical

### Issue 5: Dark Mode Colors Not Working

**Symptoms**: Animation colors don't change in dark mode

**Solutions**:
- ✅ Verify `values-night/colors.xml` exists with color overrides
- ✅ Check color names match exactly (case-sensitive)
- ✅ Ensure app theme supports DayNight mode: `Theme.MaterialComponents.DayNight.NoActionBar`
- ✅ Test by switching system dark mode setting

### Issue 6: Animation Appears in Wrong Place

**Symptoms**: Animation doesn't cover full screen

**Solutions**:
- ✅ Verify `layout_width="match_parent"` and `layout_height="match_parent"`
- ✅ Check all constraints are to `parent` (not `@id/...`)
- ✅ Ensure root container is full screen
- ✅ Verify `scaleType="centerCrop"` is set

### Issue 7: Multiple Animations Conflict

**Symptoms**: Multiple screens with animation cause conflicts

**Solutions**:
- ✅ Each Activity should have its own ImageView with unique binding
- ✅ Animation automatically pauses when Activity is paused (default behavior)
- ✅ No need to manually stop animation - Android handles lifecycle
- ✅ If issues persist, stop animation in `onPause()`:
  ```kotlin
  override fun onPause() {
      super.onPause()
      (binding.imageBgAnimation.drawable as? Animatable)?.stop()
  }
  ```

### Issue 8: Animation Stops After Screen Rotation

**Symptoms**: Animation doesn't restart after rotation

**Solutions**:
- ✅ Call `setupBackgroundAnimation()` in `onCreate()` (handles rotation automatically)
- ✅ If using view binding, ensure binding is recreated after rotation
- ✅ No need to handle rotation separately - `onCreate()` is called on rotation

## Layout Order is Critical

The order of children in ConstraintLayout determines rendering order. **First = Bottom Layer**:

```xml
<ConstraintLayout>
    <!-- 1. BACKGROUND (Renders first = bottom layer) -->
    <ImageView android:id="@+id/image_bg_animation" ... />
    
    <!-- 2. CONTENT (Renders second = top layer) -->
    <NestedScrollView android:background="@android:color/transparent" ...>
        <!-- Your content -->
    </NestedScrollView>
</ConstraintLayout>
```

**Wrong Order Example** (DO NOT DO THIS):
```xml
<ConstraintLayout>
    <NestedScrollView ... />  <!-- ❌ Content first = hidden behind animation -->
    <ImageView android:id="@+id/image_bg_animation" ... />  <!-- ❌ Animation on top -->
</ConstraintLayout>
```

## Best Practices

### ✅ DO:
- Always place ImageView as first child in root ConstraintLayout
- Use `app:srcCompat` instead of `android:src`
- Set `android:background="@android:color/transparent"` on content containers
- Call `setupBackgroundAnimation()` after `setContentView()`
- Use `scaleType="centerCrop"` for proper scaling
- Keep animation opacity low (0.03-0.05) for subtlety

### ❌ DON'T:
- Don't place ImageView after content (will cover content)
- Don't use `android:src` (use `app:srcCompat`)
- Don't set background colors on ScrollView or content containers
- Don't call `setupBackgroundAnimation()` before `setContentView()`
- Don't set `android:elevation` on ImageView (will affect layering)
- Don't use high opacity (will distract from content)

## Integration Checklist

When adding animated background to a new Activity:

- [ ] Create `avd_background_flow.xml` in `res/drawable/` (if not exists)
- [ ] Verify color resources exist (`fitness_primary`, `fitness_primary_dark`, `fitness_accent`)
- [ ] Add ImageView as **first child** in root ConstraintLayout
- [ ] Set ImageView attributes: `match_parent`, `centerCrop`, `alpha="1.0"`
- [ ] Use `app:srcCompat="@drawable/avd_background_flow"`
- [ ] Set `transparent` background on content containers
- [ ] Add `setupBackgroundAnimation()` method to Activity
- [ ] Call `setupBackgroundAnimation()` in `onCreate()` after `setContentView()`
- [ ] Test in both light and dark modes
- [ ] Verify animation doesn't cover content

## Customization Options

### Adjust Animation Speed

Modify `android:duration` in the animator tags:

```xml
<!-- Slower animation (20 seconds) -->
<objectAnimator
    android:duration="20000"
    ... />

<!-- Faster animation (8 seconds) -->
<objectAnimator
    android:duration="8000"
    ... />
```

### Change Wave Opacity

Modify `android:fillAlpha` in path elements:

```xml
<!-- More visible (0.10 = 10% opacity) -->
<path
    android:fillAlpha="0.10"
    ... />

<!-- Less visible (0.02 = 2% opacity) -->
<path
    android:fillAlpha="0.02"
    ... />
```

### Customize Colors

Use different color resources in path elements:

```xml
<!-- Use custom color -->
<path
    android:fillColor="@color/your_custom_color"
    ... />
```

### Add More Waves

Duplicate a wave group and animator target:

```xml
<!-- New wave -->
<group android:name="wave4">
    <path
        android:pathData="M-100,190 Q-75,170 -50,190 Q-25,210 0,190 ..."
        android:fillColor="@color/fitness_primary"
        android:fillAlpha="0.04"/>
</group>

<target android:name="wave4">
    <aapt:attr name="android:animation">
        <objectAnimator
            android:duration="20000"
            ... />
    </aapt:attr>
</target>
```

## Performance Considerations

### Minimal Impact
- Animated Vector Drawables are hardware-accelerated by default
- Low opacity reduces rendering overhead
- Only 3 animated elements (very lightweight)
- Animations pause automatically when Activity is paused

### When to Optimize
- If experiencing lag, consider reducing to 2 waves
- Lower opacity further if needed (minimum 0.01)
- Simplify wave paths (fewer curve points) if needed
- Only animate on visible screens (already handled automatically)

## Compatibility

- **Minimum SDK**: API 21 (Android 5.0) - Required for Animated Vector Drawables
- **Vector Support**: Requires `app:srcCompat` (provided by AndroidX)
- **Dark Mode**: Requires API 29+ for automatic switching (or manual implementation)
- **Theme**: Works with MaterialComponents themes

## Summary

The animated background is a simple but effective design element that:
1. Uses an Animated Vector Drawable for smooth, lightweight animation
2. Must be placed as the first child in the layout hierarchy
3. Requires a single method call to start: `setupBackgroundAnimation()`
4. Automatically adapts to light/dark mode via color resources
5. Provides subtle visual interest without performance impact

Follow this guide carefully to avoid conflicts with content layering and ensure proper animation behavior.
