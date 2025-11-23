# Android WebView SVG Highlighting Not Working

## Problem Summary
I have successfully loaded an SVG file into an Android WebView and can execute JavaScript. However, when I try to highlight SVG elements by their ID (to show selected muscle groups), the highlighting does not work. The JavaScript function is being called, but the SVG elements are not changing color.

## What Works ✅
- SVG loads and displays correctly in WebView
- JavaScript execution works (tested with `document.body.style.backgroundColor = 'red'` - works)
- Kotlin → JavaScript communication works (`evaluateJavascript()` successfully calls functions)
- SVG is loaded into main document using `fetch()` and `innerHTML` (no `<object>` tag boundary issues)
- Can find SVG elements by ID using `document.getElementById('LATS')` (returns element)
- Can modify element styles using `element.style.fill = '#FF3B30'` (tested manually)

## What Doesn't Work ❌
- When calling `setHighlights(['BICEPS'])` from Kotlin, the SVG element with `id="BICEPS"` does not change color
- The function executes (console logs show it's called), but visual changes don't appear
- Elements are found (no "Element not found" errors), but styling doesn't take effect

## Current Implementation

### Kotlin Code (EditExerciseActivity.kt)
```kotlin
private fun updateMuscleMap() {
    if (!isWebViewReady) return

    val primaryTargets = getSelectedTargetMuscles(binding.chipGroupPrimaryTargets)
    val secondaryTargets = getSelectedTargetMuscles(binding.chipGroupSecondaryTargets)
    val allTargets = (primaryTargets + secondaryTargets).distinct()
    
    // Convert to JavaScript array: ['BICEPS', 'TRICEPS_LONG']
    val jsArray = allTargets.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ", "
    ) { "'${it.name}'" }
    
    val jsCode = "setHighlights($jsArray);"
    binding.webViewMuscleMap.evaluateJavascript(jsCode, null)
}
```

### JavaScript Function (muscle_map.html)
```javascript
function setHighlights(idsArray) {
    console.log('setHighlights called with:', idsArray);
    
    // Check if SVG is loaded
    const svgLoaded = document.querySelector('svg');
    if (!svgLoaded) {
        console.log('SVG not ready, retrying in 100ms...');
        setTimeout(() => setHighlights(idsArray), 100);
        return;
    }
    
    // 1. Reset ALL elements to default
    document.querySelectorAll('path, rect').forEach(el => {
        el.style.fill = '#444444';
        el.style.fillOpacity = '1.0';
        el.style.stroke = '#ffffff';
        el.style.strokeWidth = '2px';
    });
    
    // Also reset paths/rects inside groups
    document.querySelectorAll('g').forEach(g => {
        g.querySelectorAll('path, rect').forEach(el => {
            el.style.fill = '#444444';
            el.style.fillOpacity = '1.0';
            el.style.stroke = '#ffffff';
            el.style.strokeWidth = '2px';
        });
    });
    
    // 2. Highlight target muscles
    if (idsArray && idsArray.length > 0) {
        idsArray.forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                console.log('Found element:', el.tagName, el.id);
                
                if (el.tagName === 'g' || el.tagName === 'G') {
                    // If it's a group, highlight all paths/rects in the group
                    el.querySelectorAll('path, rect').forEach(path => {
                        path.style.fill = '#FF3B30';
                        path.style.fillOpacity = '1.0';
                        path.style.stroke = '#ffffff';
                        path.style.strokeWidth = '2px';
                    });
                } else if (el.tagName === 'path' || el.tagName === 'PATH' || 
                           el.tagName === 'rect' || el.tagName === 'RECT') {
                    // If it's a path or rect, highlight it directly
                    el.style.fill = '#FF3B30';
                    el.style.fillOpacity = '1.0';
                    el.style.stroke = '#ffffff';
                    el.style.strokeWidth = '2px';
                }
            } else {
                console.warn('Element not found with id:', id);
            }
        });
    }
}
```

### SVG Structure (liftpath_musclegroups.svg)
The SVG contains elements like:
```xml
<path id="TRICEPS_LONG" d="..." style="fill:none;stroke:#272726;stroke-width:2.08px;"/>
<g id="BICEPS" transform="...">
    <path d="..." style="fill:none;stroke:#272726;stroke-width:2.08px;"/>
</g>
<rect id="LATS" x="..." y="..." width="..." height="..." style="fill-opacity:0.53;stroke:#272726;stroke-width:2.08px;"/>
```

### WebView Settings
```kotlin
binding.webViewMuscleMap.settings.apply {
    javaScriptEnabled = true
    useWideViewPort = true
    loadWithOverviewMode = true
    allowFileAccess = true
    allowContentAccess = true
    allowFileAccessFromFileURLs = true
    allowUniversalAccessFromFileURLs = true
}
```

## Key Observations
1. **Manual testing works**: When I manually execute `document.getElementById('LATS').style.fill = '#FF3B30'` in the browser console, it works
2. **Function is called**: Console logs show `setHighlights called with: ['BICEPS']`
3. **Elements are found**: Console logs show `Found element: g BICEPS` or `Found element: path TRICEPS_LONG`
4. **But no visual change**: The SVG elements don't actually change color when called from Kotlin

## Possible Issues to Investigate
1. **Timing issue**: Maybe the SVG isn't fully loaded when `setHighlights` is called?
2. **Style specificity**: Maybe inline styles in the SVG are overriding the JavaScript styles?
3. **WebView rendering**: Maybe WebView needs a force refresh or repaint?
4. **CSS conflicts**: Maybe CSS rules in the HTML are interfering?
5. **SVG namespace**: Maybe we need to use `setAttributeNS` instead of `style.fill`?
6. **Async execution**: Maybe `evaluateJavascript` needs a callback to ensure execution completes?

## Test Case
When I select a "Biceps" chip in the Android app:
- Kotlin calls: `setHighlights(['BICEPS']);`
- JavaScript logs: `Found element: g BICEPS`
- But the BICEPS group in the SVG does not turn red

## What I Need
A working solution that:
1. Highlights SVG elements when `setHighlights(['BICEPS'])` is called from Kotlin
2. Works for all element types: `<path>`, `<rect>`, and `<g>` (groups)
3. Properly resets previous highlights
4. Works reliably in Android WebView

Please help me debug why the style changes aren't taking effect even though the JavaScript executes successfully!

