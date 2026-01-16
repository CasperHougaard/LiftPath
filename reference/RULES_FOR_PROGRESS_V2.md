LiftPath 2.0 Logic: The 3 Dimensions of Progress

We are upgrading the "View Progress" screen to track three metrics instead of just one. This shift moves LiftPath from a simple logger to an intelligent coaching tool that recognizes multiple forms of adaptation. By visualizing progress across Intensity, Capacity, and Efficiency, we ensure that the user receives positive reinforcement even when the weight on the bar remains static, provided other performance indicators are improving. This aligns perfectly with a "Project Armor" philosophy where consistency and volume accumulation are as vital as peak strength.

1. Estimated 1RM (Intensity) - EXISTING

Keep the current hybrid logic (Brzycki/Epley).

Goal: Measures top-end strength and maximal force production potential. This is the classic metric for "getting stronger" and serves as the primary benchmark for heavy, low-rep training sessions (Session A).

Formula & Implementation Detail:

Low Reps (≤ 8): Use the Epley Formula: Weight * (1 + 0.0333 * Reps). This formula is generally more accurate for lower rep ranges typical of strength blocks, avoiding the overestimation that can occur with other formulas.

High Reps (> 8): Use the Brzycki Formula: Weight * (36 / (37 - Reps)). This formula better accounts for the metabolic fatigue inherent in higher rep sets, providing a more realistic max estimate from hypertrophy work.

RPE Correction: To refine accuracy, the "Reps" variable in these formulas should be the theoretical max reps. If a user logs 5 reps @ RPE 8, they theoretically had 2 reps in reserve. The formula should use Reps + (10 - RPE) as the input. Example: 100kg x 5 @ RPE 8 is treated as a 7-rep max effort for calculation purposes.

Context: This metric is crucial for heavy compound lifts (Bench Press, Leg Press, Rows) where progressive overload in weight is the primary driver. It allows users to see their strength trend upwards even if they never actually test a true 1RM, reducing injury risk while maintaining motivation.

2. Volume Load (Capacity) - NEW

Formula: Sum(Weight * Reps) for all sets of a specific exercise in a single session.

Goal: Measures "Armor Building" (Hypertrophy) and Work Capacity. This metric quantifies the total mechanical stress placed on the muscle, which is the primary driver for muscle growth and connective tissue strengthening.

Usage: Used for the new Bar Chart mode. This is the hero metric for "Light" or "Hypertrophy" days (Session B), where the goal isn't necessarily to lift heavier, but to do more work.

Interpretation & Value:

Stagnation Buster: A user might be stuck benching 80kg for weeks (flat E1RM). However, if they increase from 3x8 to 3x10 reps, their Volume Load jumps from 1920kg to 2400kg. This is a massive 25% increase in "Armor Building" signal that the old system would miss.

Recovery Monitoring: A sudden, sharp drop in Volume Load can indicate fatigue accumulation or a need for a deload week, acting as an early warning system for the "Coach" algorithm.

3. RPE Efficiency (Neurological) - NEW

Formula: (Weight * Reps) / RPE (Calculated for the top/heaviest set of the session).

Goal: Detects progress when weight/reps are static but effort decreases. This is the "IronBrain" metric—it measures how easily the nervous system handles a given load.

Win Condition: If Current_Efficiency > Previous_Efficiency with same load, trigger "Efficiency Badge".

Deep Dive Explanation:

Progress isn't always linear. Often, a lifter will repeat the exact same workout: 85kg x 5 reps.

Session 1: RPE 9.5 (Grinding, shaking, near failure). Score: (85*5)/9.5 = 44.7.

Session 2: RPE 8.0 (Clean, fast, controlled). Score: (85*5)/8.0 = 53.1.

The Verdict: Even though the logbook looks identical, the efficiency score reveals a ~19% improvement in neurological efficiency. The user has gotten stronger; the "internal cost" of the lift has gone down.

This metric is vital for preventing burnout. It rewards "mastery" and "good form" rather than just encouraging ego-lifting.

UI Requirements

Graph Toggle: A ChipGroup above the chart to switch between metrics, allowing the user to view their progress through different lenses:

"Strength" (Line Chart - E1RM): The default view for tracking peak power. Best for compound lifts on Heavy days.

"Volume" (Bar Chart - Total Tonnage): A new view visualizing total work. Best for isolation movements and Light/Hypertrophy days. Visualizing volume as bars emphasizes the "accumulation" aspect of training.

Insight Card: A dynamic "Smart Card" below the chart providing immediate, actionable feedback:

"Last Session" vs "Previous Session" comparison: A direct side-by-side of the two most recent data points to show immediate trends (e.g., "Volume: +500kg vs Last Session").

Badges: Visual rewards that trigger based on specific "Win Conditions":

"🛡️ Volume PR": Triggered when the total tonnage exceeds the previous session's volume. Reinforces the "Project Armor" goal.

"🧠 Efficiency Gain": Triggered when the Efficiency Score improves despite static or similar loads. Reinforces technical mastery and patience.

"🥇 Strength PR": Triggered when the E1RM hits a new all-time high.

This comprehensive approach transforms the "View Progress" screen from a passive history log into an active tool for motivation and analysis, ensuring that every drop of sweat—whether it adds weight, reps, or just makes the bar move faster—is counted as a victory.
