# Fitness App Design Philosophy & Guidelines

This document outlines the design philosophy and implementation guidelines for the fitness app, derived from the established patterns in `activity_main.xml` and `activity_progress.xml`.

## Design Philosophy

### Core Principles
1. **Card-Based Interface**: Content is organized into distinct, elevated cards that create visual hierarchy and separation
2. **Layered Depth**: Use elevation and shadows to create a sense of depth and hierarchy
3. **Consistent Spacing**: Maintain rhythmic spacing throughout the app for visual harmony
4. **Color-Coded Information**: Use a consistent color system to convey meaning and importance
5. **Animated Background**: Subtle background animation (`avd_background_flow`) provides visual interest without distraction
6. **Accessibility First**: All interactive elements are properly sized and have content descriptions

## Layout Structure

### Root Container Pattern
```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/fitness_background"
    android:fitsSystemWindows="true">
    
    <!-- Background Animation -->
    <ImageView
        android:id="@+id/image_bg_animation"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:alpha="1.0"
        android:scaleType="centerCrop"
        app:srcCompat="@drawable/avd_background_flow" />
    
    <!-- Scrollable Content -->
    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@android:color/transparent"
        android:clipToPadding="false"
        android:fillViewport="true">
        
        <!-- Content Container -->
        <LinearLayout or ConstraintLayout
            android:padding="24dp"
            android:clipChildren="false"
            android:clipToPadding="false">
            <!-- Content -->
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>
</androidx.constraintlayout.widget.ConstraintLayout>
```

**Key Points:**
- Always include the background animation ImageView as the first child
- Use NestedScrollView for scrollable content
- Main content container uses 24dp padding
- Set `clipChildren="false"` and `clipToPadding="false"` to allow visual overflow

## Color System

### Color Usage Guidelines

| Color Resource | Usage | Examples |
|---------------|-------|----------|
| `@color/fitness_background` | Root container background | Main activity background |
| `@color/fitness_card_background` | Card backgrounds | All CardView backgrounds |
| `@color/fitness_text_primary` | Primary text, icons | Titles, main content, primary icons |
| `@color/fitness_text_secondary` | Secondary text, subtle icons | Subtitles, hints, secondary icons |
| `@color/fitness_primary` | Primary actions, highlights | Hero buttons, primary stat values, primary icons |
| `@color/fitness_accent` | Accent elements | Secondary icons, accent stat values |
| `@color/fitness_primary_dark` | Darker primary variant | Alternative stat values |
| `@color/white` | Text on primary background | Text on hero cards |

### Icon Tinting Rules
- **Primary actions**: `@color/fitness_primary`
- **Accent/secondary actions**: `@color/fitness_accent`
- **Alternative/tertiary**: `@color/fitness_primary_dark`
- **Subtle/secondary**: `@color/fitness_text_secondary`
- **On primary background**: `@color/white`

## Typography Scale

### Text Size Hierarchy

| Element | Size | Style | Color | Usage |
|---------|------|-------|-------|-------|
| Page Title | 26-28sp | Bold | Primary | Main screen titles |
| Section Title | 20sp | Bold | Primary | Section headers |
| Card Title | 18sp | Bold | Primary | Card headers |
| Subtitle | 14-16sp | Normal | Secondary | Subtitles, hints |
| Body Text | 13-15sp | Normal/Bold | Primary/Secondary | Card content, labels |
| Stat Value | 22-24sp | Bold | Primary/Accent | Statistics, metrics |
| Small Text | 10-12sp | Normal | Secondary | Fine print, units, labels |

### Text Spacing
- Title to subtitle: 4dp margin
- Section to content: 12-16dp margin
- Card title to content: 12dp margin
- Label to value: 8dp margin

## Card Design System

### Card Types

#### Standard Card
```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="20dp"
    app:cardElevation="4dp">
    <!-- Content with 16dp padding -->
</androidx.cardview.widget.CardView>
```

#### Featured Card (Charts, Important Content)
```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="24dp"
    app:cardElevation="6dp">
    <!-- Content with 20dp padding -->
</androidx.cardview.widget.CardView>
```

#### Hero Card (Primary Actions)
```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="100dp"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    app:cardBackgroundColor="@color/fitness_primary"
    app:cardCornerRadius="24dp"
    app:cardElevation="8dp">
    <!-- Content -->
</androidx.cardview.widget.CardView>
```

#### Subtle Card (Secondary Information)
```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="20dp"
    app:cardElevation="2dp">
    <!-- Content with 16dp padding -->
</androidx.cardview.widget.CardView>
```

### Elevation Hierarchy
- **0dp**: Flat buttons (back button, icon buttons)
- **2dp**: Subtle cards (secondary information)
- **4dp**: Standard cards (most content cards)
- **6dp**: Featured cards (charts, important content)
- **8dp**: Hero cards (primary action buttons)

### Corner Radius
- **20dp**: Standard cards
- **24dp**: Hero cards, featured cards, circular buttons (48dp size)

### Card Padding
- **16dp**: Standard cards
- **20dp**: Featured cards with more content
- **12dp**: Small action cards (90dp height)

## Spacing System

### Container Padding
- **Main container**: 24dp (all sides)
- **Card content**: 16dp (standard) or 20dp (featured)

### Vertical Spacing (Margins)
- **4dp**: Tight spacing (title to subtitle)
- **8dp**: Small spacing (label to value, small gaps)
- **12dp**: Standard spacing (section to content, card to card in grids)
- **16dp**: Medium spacing (section headers to content)
- **20dp**: Large spacing (major sections)
- **24dp**: Extra large spacing (between major sections)
- **28dp**: Section title spacing
- **32dp**: Major section separation

### Horizontal Spacing (Margins)
- **4dp**: Tight horizontal spacing
- **6dp**: Grid item spacing
- **8dp**: Standard horizontal spacing between cards
- **16dp**: Medium horizontal spacing

## Icon System

### Icon Sizes
- **16dp**: Small inline icons (date picker arrows, inline indicators)
- **24dp**: Medium icons (action buttons, inline actions)
- **28dp**: Standard icons (most UI icons)
- **40dp**: Large icons (hero card icons)
- **48dp**: Button icons (circular action buttons)

### Icon Padding
- **12dp**: Standard icon padding in buttons and cards

### Icon Usage
- Always use `app:tint` to apply color
- Icons should be vector drawables when possible
- Maintain consistent sizing within the same context
- Use `ic_plus` for add/create actions
- Use `ic_delete` for delete/remove actions (with error color)

## Interactive Elements

### Clickable Cards
All interactive cards must include:
```xml
android:clickable="true"
android:focusable="true"
android:foreground="?attr/selectableItemBackground"
```

### Button Cards
- Small circular buttons: 48dp × 48dp, 24dp corner radius
- Action cards: 90dp height for grid items
- Hero buttons: 100dp height for primary actions
- Bottom action buttons: 60dp height for save/cancel patterns
- Inline small clickable elements: 16dp icons with minimal padding

### Inline Date Picker Pattern
For compact date selection integrated into headers:

```xml
<LinearLayout
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical">
    
    <TextView
        android:id="@+id/text_date"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/fitness_text_secondary"
        android:textSize="14sp" />
    
    <ImageView
        android:id="@+id/button_change_date"
        android:layout_width="16dp"
        android:layout_height="16dp"
        android:layout_marginStart="6dp"
        android:clickable="true"
        android:focusable="true"
        android:src="@drawable/ic_arrow_back_24"
        android:rotation="270"
        app:tint="@color/fitness_text_secondary" />
</LinearLayout>
```

**Key Points:**
- 16dp icon size for subtle inline actions
- 6dp margin between text and icon
- Uses secondary text color for subtlety
- Icon rotation for dropdown indication
- Clickable icon triggers date picker dialog

### Stacked Bottom Action Buttons Pattern
For screens with multiple fixed bottom actions (e.g., Active Workout):

```xml
<androidx.constraintlayout.widget.ConstraintLayout>
    
    <!-- Content ScrollView -->
    <androidx.core.widget.NestedScrollView
        android:id="@+id/scroll_view"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintBottom_toTopOf="@id/button_secondary_action"
        app:layout_constraintTop_toTopOf="parent">
        <!-- Content -->
    </androidx.core.widget.NestedScrollView>
    
    <!-- Secondary Action Button -->
    <androidx.cardview.widget.CardView
        android:id="@+id/button_secondary_action"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="24dp"
        android:layout_marginEnd="24dp"
        android:layout_marginBottom="12dp"
        app:cardBackgroundColor="@color/fitness_card_background"
        app:cardCornerRadius="20dp"
        app:cardElevation="4dp"
        app:layout_constraintBottom_toTopOf="@id/button_primary_action">
        <!-- 60dp min height, centered content with icon + text -->
    </androidx.cardview.widget.CardView>
    
    <!-- Primary Action Button -->
    <androidx.cardview.widget.CardView
        android:id="@+id/button_primary_action"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="24dp"
        android:layout_marginEnd="24dp"
        android:layout_marginBottom="24dp"
        app:cardBackgroundColor="@color/fitness_primary"
        app:cardCornerRadius="24dp"
        app:cardElevation="8dp"
        app:layout_constraintBottom_toBottomOf="parent">
        <!-- 60dp min height, centered content -->
    </androidx.cardview.widget.CardView>
</androidx.constraintlayout.widget.ConstraintLayout>
```

**Key Points:**
- ScrollView ends above the first bottom button
- Buttons are stacked with 12dp spacing between them
- Secondary action: card background, 4dp elevation, 20dp radius
- Primary action: primary color, 8dp elevation, 24dp radius
- Both buttons: 24dp horizontal margins, 60dp min height
- Bottom button has 24dp bottom margin

### Delete Button Pattern
For destructive actions within forms:

```xml
<androidx.cardview.widget.CardView
    android:id="@+id/card_delete"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="20dp"
    app:cardElevation="2dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:orientation="horizontal"
        android:padding="16dp">
        
        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_delete"
            app:tint="@color/design_default_color_error" />
        
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:text="Delete [Item]"
            android:textColor="@color/design_default_color_error"
            android:textSize="16sp"
            android:textStyle="bold" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

**Key Points:**
- Initially hidden (`visibility="gone"`), shown when editing existing items
- Uses error color for both icon and text
- 24dp icon with 12dp margin to text
- Low elevation (2dp) to de-emphasize
- Centered content with horizontal layout
- Shows confirmation dialog before executing action

### List Item Card Pattern
For RecyclerView items with actions:

```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="16dp"
    app:cardElevation="2dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">
        
        <!-- Title -->
        <TextView
            android:textColor="@color/fitness_text_primary"
            android:textSize="18sp"
            android:textStyle="bold" />
        
        <!-- Description/Summary (Vertical Format) -->
        <TextView
            android:layout_marginTop="2dp"
            android:lineSpacingMultiplier="0.9"
            android:textColor="@color/fitness_text_secondary"
            android:textSize="12sp" />
        
        <!-- Action Buttons Row -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:orientation="horizontal">
            
            <!-- Primary Action (40dp height, 12dp radius, primary color) -->
            <!-- Secondary Actions (40dp height, 12dp radius, outlined) -->
            <!-- Delete Action (40dp × 40dp square, delete icon with error color) -->
        </LinearLayout>
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

**Key Points:**
- 16dp corner radius for list items (smaller than main cards)
- 2dp elevation for subtle lift
- 12dp bottom margin between items
- 16dp internal padding
- Title: 18sp bold, primary color
- Description: 12sp normal, secondary color, 2dp top margin, 0.9 line spacing for compact text
- Format multi-line descriptions vertically (one item per line) for clarity
- Action buttons: 40dp height, 12dp corner radius, 10dp top margin
- Button spacing: 6dp margins between buttons
- Primary button: primary color background, white text, plus icon (always visible)
- Secondary buttons: outlined (1dp stroke), card background (conditional visibility)
- Delete button: square (40dp), error color stroke and icon (conditional visibility)
- Button order: Primary action, Duplicate, Edit, Delete (left to right)
- Hide Duplicate, Edit, and Delete buttons when no items exist

### History/Summary List Item Pattern
For displaying workout sessions or historical data:

```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="16dp"
    app:cardElevation="2dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">
        
        <!-- Title Row with Badge -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">
            
            <TextView
                android:layout_width="0dp"
                android:layout_weight="1"
                android:textColor="@color/fitness_text_primary"
                android:textSize="18sp"
                android:textStyle="bold" />
            
            <!-- Type Badge -->
            <TextView
                android:background="@drawable/bg_chip"
                android:backgroundTint="@color/fitness_primary"
                android:paddingStart="10dp"
                android:paddingTop="4dp"
                android:paddingEnd="10dp"
                android:paddingBottom="4dp"
                android:textColor="@color/white"
                android:textSize="11sp"
                android:textStyle="bold" />
        </LinearLayout>
        
        <!-- Metadata Row with Icons -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">
            
            <!-- Icon (14dp) + Text (13sp) pairs -->
            <!-- Separated by dot separators (4dp circle) -->
        </LinearLayout>
        
        <!-- Summary Text -->
        <TextView
            android:layout_marginTop="6dp"
            android:textColor="@color/fitness_text_secondary"
            android:textSize="12sp" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

**Key Points:**
- Clickable card with ripple effect
- Title row: 18sp bold title + badge on right
- Badge: `@drawable/bg_chip` background, 12dp corner radius, uppercase text, 11sp bold
- Metadata row: 14dp icons with 13sp text, separated by 4dp dot circles
- Icon + text spacing: 6dp margin
- Dot separator margins: 10dp on both sides
- Summary text: 12sp, secondary color, 6dp top margin
- Format numbers with comma separators (e.g., "1,250kg")
- Format counts properly: "4 exercises • 12 sets"

### Form Card Pattern
For input forms with multiple fields:

```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="20dp"
    app:cardElevation="4dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">
        
        <!-- Section Title -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Section Title"
            android:textColor="@color/fitness_text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />
        
        <!-- Form Field (TextInputLayout) -->
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:hint="Field Label"
            app:boxBackgroundMode="outline"
            app:boxStrokeColor="@color/fitness_primary"
            app:hintTextColor="@color/fitness_primary">
            
            <com.google.android.material.textfield.TextInputEditText
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textColor="@color/fitness_text_primary" />
        </com.google.android.material.textfield.TextInputLayout>
        
        <!-- Action Button Inside Card (Optional) -->
        <androidx.cardview.widget.CardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:clickable="true"
            android:focusable="true"
            android:foreground="?attr/selectableItemBackground"
            app:cardBackgroundColor="@color/fitness_primary"
            app:cardCornerRadius="12dp"
            app:cardElevation="0dp">
            <!-- 44dp min height, icon + text -->
        </androidx.cardview.widget.CardView>
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

**Key Points:**
- Group related form fields in cards
- 20dp corner radius, 4dp elevation
- 16dp internal padding
- Section title: 16sp bold, primary color
- Use TextInputLayout with outline box style
- Primary color for strokes and hints
- 12dp margin between cards
- Inline action buttons: 12dp corner radius, 0dp elevation, 44dp min height
- Inline buttons use primary color background for primary actions
- Multiple form cards should flow vertically with consistent spacing

### Fixed Bottom Action Buttons
For screens with primary actions (Save/Cancel, Confirm/Dismiss, etc.), place buttons at the bottom of the screen using this pattern:

```xml
<androidx.constraintlayout.widget.ConstraintLayout>
    
    <!-- Content ScrollView -->
    <androidx.core.widget.NestedScrollView
        android:id="@+id/scroll_view_content"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:background="@android:color/transparent"
        android:clipToPadding="false"
        android:fillViewport="true"
        app:layout_constraintBottom_toTopOf="@id/button_container"
        app:layout_constraintTop_toTopOf="parent">
        <!-- Content -->
    </androidx.core.widget.NestedScrollView>
    
    <!-- Fixed Bottom Buttons -->
    <LinearLayout
        android:id="@+id/button_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:baselineAligned="false"
        android:paddingStart="24dp"
        android:paddingEnd="24dp"
        android:paddingTop="12dp"
        android:paddingBottom="24dp"
        app:layout_constraintBottom_toBottomOf="parent">
        
        <!-- Cancel Button (Subtle) -->
        <androidx.cardview.widget.CardView
            android:layout_width="0dp"
            android:layout_height="60dp"
            android:layout_weight="1"
            android:layout_marginEnd="8dp"
            android:clickable="true"
            android:focusable="true"
            android:foreground="?attr/selectableItemBackground"
            app:cardBackgroundColor="@color/fitness_card_background"
            app:cardCornerRadius="20dp"
            app:cardElevation="2dp">
            <!-- Button content -->
        </androidx.cardview.widget.CardView>
        
        <!-- Primary Action Button -->
        <androidx.cardview.widget.CardView
            android:layout_width="0dp"
            android:layout_height="60dp"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:clickable="true"
            android:focusable="true"
            android:foreground="?attr/selectableItemBackground"
            app:cardBackgroundColor="@color/fitness_primary"
            app:cardCornerRadius="20dp"
            app:cardElevation="4dp">
            <!-- Button content -->
        </androidx.cardview.widget.CardView>
    </LinearLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
```

**Key Points:**
- ScrollView height is `0dp` with constraints to top and above buttons
- Button container is constrained to bottom
- No background color on button container (transparent over animation)
- Padding: 24dp horizontal, 12dp top, 24dp bottom
- Buttons are 60dp height with equal weight
- Cancel/secondary action uses subtle styling (2dp elevation, card background)
- Primary action uses primary color (4dp elevation, primary background)
- 8dp margin between buttons

### Grid Layouts
When creating grid layouts:
```xml
<LinearLayout
    android:orientation="horizontal"
    android:baselineAligned="false"
    android:clipChildren="false"
    android:clipToPadding="false">
    
    <!-- Use layout_weight="1" for equal-width columns -->
    <!-- Use 6dp or 8dp margins between items -->
</LinearLayout>
```

**Key Attributes:**
- `baselineAligned="false"`: Prevents baseline alignment issues
- `clipChildren="false"`: Allows visual overflow
- `clipToPadding="false"`: Allows padding overflow

## Stats Cards Pattern

### Standard Stats Card
```xml
<androidx.cardview.widget.CardView
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:layout_marginEnd="8dp"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="20dp"
    app:cardElevation="4dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="120dp"
        android:orientation="vertical"
        android:padding="16dp">
        
        <ImageView
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:src="@drawable/ic_icon_name"
            app:tint="@color/fitness_primary" />
        
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="Label"
            android:textColor="@color/fitness_text_secondary"
            android:textSize="13sp" />
        
        <TextView
            android:id="@+id/text_value"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="--"
            android:textColor="@color/fitness_text_primary"
            android:textSize="22sp"
            android:textStyle="bold" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

**Key Points:**
- Use `minHeight="120dp"` for consistent stat card heights
- Icon at top (28dp), tinted with appropriate color
- Label below icon (8dp margin), 13sp, secondary color
- Value below label, 22sp, bold, primary color
- Default value: "--" (two dashes)

## Chart Containers

### Chart Card Pattern
```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="280dp"
    app:cardBackgroundColor="@color/fitness_card_background"
    app:cardCornerRadius="24dp"
    app:cardElevation="4dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="16dp">
        
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Chart Title"
            android:textColor="@color/fitness_text_primary"
            android:textStyle="bold"
            android:textSize="16sp" />
        
        <com.github.mikephil.charting.charts.LineChart
            android:id="@+id/chart"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_marginTop="8dp" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

**Chart Heights:**
- Standard charts: 280dp
- Large charts: 320dp

## Header Patterns

### Page Header with Back Button
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal">
    
    <androidx.cardview.widget.CardView
        android:layout_width="48dp"
        android:layout_height="48dp"
        app:cardBackgroundColor="@color/fitness_card_background"
        app:cardCornerRadius="24dp"
        app:cardElevation="0dp">
        
        <ImageButton
            android:id="@+id/button_back"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@android:color/transparent"
            android:contentDescription="@string/back_content_description"
            android:padding="12dp"
            android:src="@drawable/ic_arrow_back_24"
            app:tint="@color/fitness_text_primary" />
    </androidx.cardview.widget.CardView>
    
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_weight="1"
        android:orientation="vertical">
        
        <TextView
            android:id="@+id/text_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Title"
            android:textColor="@color/fitness_text_primary"
            android:textSize="26sp"
            android:textStyle="bold" />
        
        <TextView
            android:id="@+id/text_subtitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="Subtitle"
            android:textColor="@color/fitness_text_secondary"
            android:textSize="14sp" />
    </LinearLayout>
</LinearLayout>
```

## Empty States

### Empty State Pattern
```xml
<TextView
    android:id="@+id/text_empty_state"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:padding="24dp"
    android:text="@string/empty_state_message"
    android:textColor="@color/fitness_text_secondary"
    android:textSize="15sp"
    android:visibility="gone" />
```

**Key Points:**
- Center gravity for text
- 24dp padding
- Secondary text color
- 15sp text size
- Initially `gone`, shown when needed

## Tab Layouts

### TabLayout Pattern
```xml
<com.google.android.material.tabs.TabLayout
    android:id="@+id/tab_layout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    android:background="@android:color/transparent"
    app:tabIndicatorColor="@color/fitness_primary"
    app:tabIndicatorHeight="4dp"
    app:tabMode="scrollable"
    app:tabRippleColor="@android:color/transparent"
    app:tabSelectedTextColor="@color/fitness_primary"
    app:tabTextColor="@color/fitness_text_secondary" />
```

## Best Practices

### Do's
✅ Always use the color system resources, never hardcode colors  
✅ Maintain consistent spacing using the spacing scale  
✅ Use CardView for all content containers  
✅ Include background animation on all screens  
✅ Set `clipChildren="false"` and `clipToPadding="false"` on containers  
✅ Use `baselineAligned="false"` on grid LinearLayouts  
✅ Provide content descriptions for all interactive elements  
✅ Use string resources for all text content  
✅ Follow the elevation hierarchy for visual depth  
✅ Place primary action buttons (Save/Cancel, Confirm/Dismiss) at the bottom of the screen  

### Don'ts
❌ Don't use arbitrary spacing values - stick to the spacing scale  
❌ Don't mix different corner radius values arbitrarily  
❌ Don't use hardcoded colors  
❌ Don't skip the background animation  
❌ Don't forget to make cards clickable when they should be interactive  
❌ Don't use inconsistent icon sizes within the same context  
❌ Don't forget to set `minHeight` on stat cards for consistency  
❌ Don't place action buttons inline with content - use fixed bottom positioning  

## Implementation Checklist

When creating a new screen or component:

- [ ] Root ConstraintLayout with `@color/fitness_background`
- [ ] Background animation ImageView as first child
- [ ] NestedScrollView for scrollable content
- [ ] 24dp padding on main container
- [ ] Cards use appropriate elevation (2dp, 4dp, 6dp, 8dp)
- [ ] Cards use appropriate corner radius (20dp or 24dp)
- [ ] Text sizes follow typography scale
- [ ] Colors use the color system resources
- [ ] Spacing follows the spacing scale
- [ ] Icons are properly sized and tinted
- [ ] Interactive elements have proper click handlers
- [ ] Content descriptions provided for accessibility
- [ ] String resources used for all text
- [ ] Primary action buttons placed at bottom of screen (if applicable)

---

*This document should be referenced when creating new layouts or modifying existing ones to maintain design consistency across the app.*

