# Android WebView SVG Element Access Issue

## Problem Summary
I'm trying to modify SVG elements loaded in an Android WebView, but I cannot access or modify them via JavaScript. The SVG displays correctly, but I cannot change element colors/styles programmatically.

## Current Setup

### Files Structure
- **HTML File**: `app/src/main/assets/muscle_map.html` - Loaded in WebView via `file:///android_asset/muscle_map.html`
- **SVG File**: `app/src/main/assets/liftpath_musclegroups.svg` - External SVG file
- **Kotlin Activity**: `EditExerciseActivity.kt` - Calls JavaScript via `evaluateJavascript()`

### SVG Content
The SVG file contains elements like:
```xml
<path id="TRICEPS_LONG" ... style="fill:none;stroke:#272726;stroke-width:2.08px;"/>
<g id="BICEPS" ...>
  <path ... style="fill:none;stroke:#272726;stroke-width:2.08px;"/>
</g>
<rect id="LATS" ... style="fill-opacity:0.53;stroke:#272726;stroke-width:2.08px;"/>
```

### Current HTML Implementation
The HTML tries to load the SVG using an `<object>` tag:
```html
<object id="svg-object" data="liftpath_musclegroups.svg" type="image/svg+xml"></object>
```

Then JavaScript tries to access it via:
```javascript
svgDoc = svgObject.contentDocument;
const latsElement = svgDoc.getElementById('LATS');
```

There's also a fallback that loads the SVG via XMLHttpRequest and injects it directly into the DOM.

### Kotlin Code
```kotlin
binding.webViewMuscleMap.evaluateJavascript("setHighlights(['BICEPS']);", null)
```

## What Works
✅ WebView displays the SVG correctly (perfect fit, centered)
✅ JavaScript execution works (tested with `document.body.style.backgroundColor = 'red'` - this works)
✅ Kotlin → JavaScript communication works (tested with inline JavaScript that changes body background)
✅ SVG file loads and displays properly

## What Doesn't Work
❌ Cannot access SVG elements via `svgObject.contentDocument` (returns null or throws error)
❌ Cannot find elements by ID when SVG is loaded via `<object>` tag
❌ Cannot modify element attributes (fill, stroke, etc.) even when elements are found
❌ Fallback method (XMLHttpRequest + innerHTML) also doesn't allow element modification

## What We've Tried
1. Using `<object>` tag with `contentDocument` access
2. Using XMLHttpRequest to load SVG and inject via `innerHTML`
3. Using `setAttribute()` to set fill/stroke
4. Removing inline `style` attributes before setting attributes
5. Using both `svgDoc` and main `document` to search for elements
6. Adding delays to wait for SVG to load
7. Logging extensively - elements are not found even though they exist in the SVG file

## Test Case
When I execute this JavaScript from Kotlin:
```javascript
var latsElement = document.getElementById('LATS');
// Returns null even though LATS exists in the SVG file
```

## Key Questions
1. How do I reliably access SVG elements loaded in an Android WebView when the SVG is in an external file?
2. Is `contentDocument` accessible in Android WebView for `<object>` tags?
3. Should I use a different method to load the SVG (embed directly, use `<embed>`, `<iframe>`, etc.)?
4. Are there WebView settings I need to enable for SVG manipulation?
5. How can I modify SVG element styles/attributes when they have inline styles?

## WebView Settings (Current)
```kotlin
webView.settings.javaScriptEnabled = true
webView.settings.useWideViewPort = true
webView.settings.loadWithOverviewMode = true
webView.setBackgroundColor(Color.TRANSPARENT)
```

## Goal
I need to be able to:
- Find SVG elements by their `id` attribute (e.g., `id="BICEPS"`, `id="LATS"`)
- Change their `fill` color to red (#FF3B30) when selected
- Reset them to gray (#444444) when deselected
- This needs to work when called from Kotlin via `evaluateJavascript()`

## Environment
- Android WebView
- Kotlin/Java
- SVG file in assets folder
- HTML file in assets folder
- Android API level: Modern (likely 24+)

Please help me find a working solution to access and modify SVG elements in Android WebView!

