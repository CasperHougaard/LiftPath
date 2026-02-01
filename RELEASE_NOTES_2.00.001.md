# Release Notes - Version 2.00.001

**Release Date:** January 25, 2026

## 🎉 Major Update: Complete Progress Tracking Redesign

This release represents a major milestone with a complete overhaul of the progress tracking system, introducing powerful new analytics, visualization tools, and workout insights. We've also added a new training intent system that provides more granular tracking and better progression guidance.

---

## ✨ New Features

### 🎯 New Set Intent System

The app now tracks the **intent** of each set you perform, allowing for more precise progress tracking and better progression suggestions. The system includes four main intents:

- **STRENGTH** - Low-rep, high-weight sets focused on maximal strength (typically ≤7 reps)
  - Used for building maximal strength
  - Eligible for Weight PRs and 1RM PRs
  - Higher rest periods (default 180 seconds)
  - Higher target RPE (8.0-8.5)

- **BUILD** - Moderate-rep sets for muscle building (typically 8-15 reps)
  - Balanced approach for strength and hypertrophy
  - Eligible for Weight PRs and Volume PRs
  - Moderate rest periods (default 90 seconds)
  - Moderate target RPE (7.0-8.0)

- **FLUSH** - High-rep sets for volume and pump work (typically 16+ reps) ⭐ **NEW**
  - Focused on volume, metabolic stress, and muscle pump
  - Eligible for Weight PRs only (not 1RM or Volume PRs)
  - Shorter rest periods (default 45 seconds)
  - Lower target RPE (7.0-7.5)
  - No progression suggestions (focus on feel and volume)
  - Automatically detected for sets with 16+ reps in light workouts

- **WARMUP** - Warmup sets excluded from progress tracking

**Benefits:**
- Separate progress tracking for each intent type
- Intent-specific rest timer defaults
- More accurate PR detection (1RM PRs only from strength work)
- Better progression suggestions based on training goal
- Visual distinction in charts and progress views

### 📊 Redesigned Progress Section

The Progress section has been completely rebuilt with a modern tabbed interface featuring five comprehensive views:

#### Overview Tab
Get a high-level summary of your training progress with key metrics and insights. See your overall training volume, session count, and recent achievements at a glance.

#### Exercises Tab
Deep dive into individual exercise performance with:
- **Detailed charts** showing weight, volume, and 1RM progression over time
- **Intent-based filtering** - View strength, build, or flush sets separately
- **Trend analysis** - See how each exercise is progressing
- **Time range filtering** - Focus on recent progress (1, 3, 6, or 12 months)
- **Statistics per intent** - Separate stats for strength, build, and flush work
- **Combined charts** - View all intents together or filter by type

#### Muscles Tab
Visualize muscle group development with:
- **Interactive muscle map** - See which muscle groups you're training
- **Training distribution** - Visual representation of muscle activation
- **Progress tracking** - Identify which muscles are being worked most frequently
- **Training balance** - Spot potential imbalances in your training

#### Sessions Tab
Compare training sessions side-by-side to track your workout evolution:
- **Session comparison** - See how your workouts change over time
- **Volume trends** - Track total volume progression
- **Exercise trends** - Compare exercise performance across sessions
- **Intent breakdown** - See the mix of strength, build, and flush work

#### PRs Tab
Track and celebrate your Personal Records across multiple dimensions:
- **Weight PR** - Maximum weight lifted for each exercise
- **Volume PR** - Maximum total volume (weight × reps) in a session
- **1RM PR** - Maximum estimated one-rep max
- **PR Timeline** - Visual timeline showing when PRs were achieved
- **Intent-aware PRs** - PRs tracked separately for different training intents
- **Automatic detection** - System automatically identifies new PRs as you log workouts

### 📈 Advanced Exercise Analytics

- **Exercise Trends** - Visualize performance trends over time with interactive charts
- **1RM Estimation** - Enhanced one-rep max calculations with RPE adjustments using hybrid formulas (Epley's for ≤8 reps, Brzycki's for 9-15 reps)
- **Volume Tracking** - Monitor total volume progression for each exercise
- **Intent-Based Analysis** - Separate tracking for Strength, Build, and Flush training intents
- **Time Range Filtering** - Focus on recent progress with customizable time windows (1, 3, 6, 12 months)
- **Combined Charts** - View multiple metrics together (weight, volume, reps) with intent-based color coding

### 🏋️ Workout Report Activity

A comprehensive post-workout analysis screen that appears after completing a training session:

- **Post-Workout Summary** - Complete workout breakdown with total volume, sets, and reps
- **Session Comparison** - Compare current workout with previous sessions
- **Exercise Trends Display** - See how each exercise performed relative to your history
- **Volume and Set Statistics** - Detailed breakdown of total volume, sets, and reps
- **PR Highlights** - See which exercises hit new personal records
- **Intent Breakdown** - Visual representation of strength, build, and flush work distribution

### 🎯 Enhanced Progression System

- **Improved Progression Settings UI** - Redesigned settings interface with collapsible sections for better organization
- **Intent-Based Suggestions** - Different progression strategies based on set intent:
  - **STRENGTH**: Linear RPE-based progression with weight adjustments
  - **BUILD**: Double progression (reps then weight)
  - **FLUSH**: No suggestions (focus on feel and volume)
- **Better Suggested Weights** - Enhanced algorithm for weight and rep suggestions using tier-based progression
- **RPE-Based Adjustments** - More accurate weight suggestions based on RPE feedback
- **Intent-Specific Rest Timers** - Separate rest timer defaults for strength (180s), build (90s), and flush (45s) sets
- **Target RPE by Intent** - Different RPE targets based on training intent and user level

### 💪 Personal Records (PR) Tracking

The PR system has been completely rebuilt with automatic detection and intent-aware tracking:

- **Automatic PR Detection** - System automatically identifies new personal records as you log workouts
- **Multiple PR Types**:
  - **Weight PR** - Maximum weight lifted (all intents)
  - **Volume PR** - Maximum total volume in a session (BUILD intent only)
  - **1RM PR** - Maximum estimated one-rep max (STRENGTH intent only)
- **PR Timeline** - Visual timeline showing when PRs were achieved
- **Intent-Aware PRs** - PRs tracked separately for different training intents
- **Historical Accuracy** - PRs are detected chronologically, ensuring historical accuracy
- **30-Day Window** - PRs are tracked within a 30-day window for relevance

### 🗺️ Muscle Map Visualization

- **Interactive Muscle Map** - Visual representation of muscle group training distribution using SVG visualization
- **Progress Tracking** - See which muscle groups are being trained most frequently
- **Training Balance** - Identify potential imbalances in your training
- **Volume by Muscle** - Track volume progression for each muscle group
- **Color-Coded Activation** - Visual feedback showing which muscles are being worked

### 📱 UI/UX Improvements

- **Modern Tab Interface** - Smooth tab navigation in Progress section with Material Design components
- **Animated Backgrounds** - Enhanced visual experience with animated vector drawable backgrounds
- **Improved Layouts** - Better organization and spacing throughout the app
- **Dark Mode Support** - Enhanced dark mode theming with proper color schemes
- **New Icons** - Added icons for legacy mode and notes
- **Intent Color Coding** - Visual distinction for different set intents:
  - STRENGTH: Blue
  - BUILD: Orange/Amber
  - FLUSH: Green/Emerald
- **Better Empty States** - Improved messaging when no data is available
- **Smooth Animations** - Enhanced transitions and animations throughout

---

## 🔧 Improvements & Enhancements

### Active Training
- Enhanced workout flow with better state management
- Improved set editing interface with intent selection
- Better active exercise display with intent chips
- New "Finish Workout" action button in the action bar
- Intent-based rest timer suggestions
- Better handling of workout drafts

### Exercise Management
- Enhanced exercise editing with intent configuration
- Improved workout plan editing with per-exercise intent settings
- Better exercise selection interface with intent context
- Support for default intents in workout plans

### Data Management
- More robust data models with intent tracking
- Enhanced JSON handling with backward compatibility
- Better error handling and data validation
- Legacy session detection and migration
- Intent inference for historical data

### Helpers & Utilities
- **ProgressAnalysisHelper** - New helper for comprehensive progress analysis with intent-aware calculations
- **WorkoutComparisonHelper** - Compare workouts and calculate session summaries with intent breakdowns
- **Enhanced OneRMEstimationHelper** - Improved 1RM calculations with RPE normalization and intent filtering
- **Enhanced ProgressionHelper** - Expanded progression logic with 940+ lines of improvements, including intent-based suggestions
- **Intent Detection** - Automatic intent detection for legacy data based on rep ranges and workout type

---

## 📚 New Documentation

Added comprehensive reference documentation to help understand the new systems:

- **PR_LOGIC.md** - Complete documentation of Personal Record detection and tracking system, including intent-aware PR rules
- **PROGRESSION_AND_SUGGESTED_WEIGHT.md** - Detailed guide to progression settings and weight suggestions, including intent-based progression
- **HEAVY_LIGHT_USAGE.md** - Documentation on heavy/light workout type usage and how it relates to set intents

---

## 🐛 Bug Fixes & Technical Improvements

- Fixed duration helper calculations
- Improved rest timer service reliability
- Enhanced workout type formatting
- Better handling of edge cases in data processing
- Improved memory management in progress views
- Fixed intent detection for legacy sessions
- Better handling of null and missing data
- Improved chart rendering performance
- Fixed empty state displays

---

## 📊 Statistics

This release includes significant code changes:

- **79 files changed**
- **11,531 insertions**
- **4,148 deletions**
- **Net addition: 7,383 lines of code**

### New Components
- 5 new Fragment classes for progress views (Overview, Exercises, Muscles, Sessions, PRs)
- 1 new Activity (WorkoutReportActivity)
- 5 new Adapter classes (ExerciseTrendAdapter, PRTimelineAdapter, ProgressPagerAdapter, SessionComparisonAdapter, plus updates to existing adapters)
- 2 new Helper classes (ProgressAnalysisHelper, WorkoutComparisonHelper)
- 1 new Component (AddSpecialBottomSheet)
- Multiple new layout files for fragments and activities
- New data models for intent tracking and progress analysis

---

## 🚀 What's Next

This release sets the foundation for future enhancements. We're continuously working on improving your training experience with better analytics, more insights, and enhanced features. The new intent system opens up possibilities for even more sophisticated tracking and progression guidance.

---

## 📝 Important Notes

- **Major Version Update** - This is version 2.00.001, representing a significant milestone
- **Backward Compatible** - All existing training data will be automatically migrated
- **Intent Detection** - Historical sessions will have intents automatically inferred based on rep ranges and workout type
- **Data Structure** - Progress data structure remains compatible with previous versions
- **UI Changes** - Some UI elements have been redesigned for better usability and to accommodate new features
- **Settings Migration** - Progression settings will be automatically migrated to include new intent-specific rest timer defaults

---

## 🎓 Understanding Set Intents

If you're new to the intent system, here's a quick guide:

- **STRENGTH** sets are for building maximal strength - think heavy squats, deadlifts, bench press at low reps
- **BUILD** sets are for muscle building - moderate reps for both strength and size
- **FLUSH** sets are for volume and pump - high reps for metabolic stress and muscle endurance
- The app automatically detects intent based on your rep ranges, but you can manually set it when logging sets
- Each intent has different progression strategies and rest periods optimized for that training goal

---

**Thank you for using LiftPath!** 💪

For questions or feedback, please reach out through the app or visit our support channels.
