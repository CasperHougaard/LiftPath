# 📹 Video Requirements for FOREGROUND_SERVICE_SPECIAL_USE Approval

## 🎯 Purpose
Google Play Console requires a video demonstration showing how your foreground service is **visible to users even when not directly interacting with the app**.

## 📋 What to Record in the Video (2-3 minutes)

### 1. **Start the Rest Timer** (30 seconds)
- Open the fitness app
- Log a workout set (e.g., Bench Press, 100kg, RPE 8.0)
- Show the toast notification: "⏱️ Rest timer started: 3m 0s"
- Show the timer dialog popping up with countdown

### 2. **Show Notification Persistence** (30 seconds)
- **Pull down the notification panel** to show the persistent notification
- Clearly show:
  - "Rest Timer - [Exercise Name]"
  - Countdown time (e.g., "⏱️ 2:45 remaining")
  - Action buttons: +15s, -15s, Stop
- Show that timer continues counting down while app is in background

### 3. **Background Operation** (30 seconds)
- **Minimize/close the app** (press home button)
- Open another app (e.g., browser, settings)
- **Pull down notification panel again** to show timer still running and updating
- Emphasize that the timer is **visible and functional even when app is closed**

### 4. **Interactive Features** (30 seconds)
- Show interacting with notification actions:
  - Tap "+15s" → Timer increases
  - Tap "-15s" → Timer decreases
  - Show timer updating in real-time
- Optionally show the timer dialog with +15s/-15s buttons

### 5. **Lock Screen Display** (20 seconds) - *If applicable*
- Lock the phone screen
- Show timer dialog appearing on lock screen (if supported)
- Show that timer continues even on lock screen

### 6. **Timer Completion** (20 seconds)
- Show timer reaching 0:00
- Show completion notification/feedback
- Show that service stops properly

## 🎬 Video Specifications

### **Requirements:**
- **Format**: MP4 or WebM
- **Resolution**: Minimum 720p (1280x720) or higher
- **Duration**: 2-3 minutes (maximum 5 minutes)
- **Size**: Maximum 100MB
- **Language**: English or with English subtitles

### **Recording Tips:**
1. **Use screen recording** (built-in on Android/Windows/Mac)
2. **Record in landscape mode** for better visibility
3. **Use a physical device** (not emulator) if possible
4. **Show clear UI elements** - zoom if needed
5. **Speak clearly** if adding narration, or add text annotations
6. **Show real-time updates** - let timer count down visibly

### **What to Emphasize:**
✅ **Persistent notification visible in status bar**
✅ **Timer updates continue when app is closed**
✅ **User can interact with notification actions without opening app**
✅ **Critical timing information visible at all times**

## 📤 Where to Upload

### **In Google Play Console:**

1. Go to **App content** → **Foreground service permissions**
2. Find **FOREGROUND_SERVICE_SPECIAL_USE** section
3. Click **Edit declaration**
4. Fill in the form:
   - **Subtype description**: Use the same text from AndroidManifest.xml
   - **Justification**: 
     ```
     The rest timer service provides continuous countdown visible to users 
     through persistent notification and lock screen dialog. Users need real-time 
     timing feedback between exercise sets to optimize workout performance. 
     The service must run in foreground to ensure accurate countdown updates 
     visible even when app is minimized, allowing users to track rest time 
     without actively interacting with the app.
     ```
   - **Impact of suspension**: 
     ```
     If the service is suspended, users would lose critical timing information 
     between exercise sets, disrupting workout flow and potentially affecting 
     training effectiveness. The timer must continue running reliably in 
     background to provide accurate rest period tracking.
     ```
5. **Upload your video** in the designated field
6. **Submit for review**

## ✅ Checklist Before Upload

- [ ] Video clearly shows persistent notification
- [ ] Video demonstrates timer running when app is closed
- [ ] Video shows interactive notification actions working
- [ ] Video is 2-3 minutes long
- [ ] Video is in MP4 or WebM format
- [ ] Video meets resolution requirements (720p+)
- [ ] Video file size is under 100MB
- [ ] Subtype description matches AndroidManifest.xml
- [ ] Justification clearly explains user-visible functionality
- [ ] Impact explanation describes consequences of suspension

## 🎯 Key Points to Highlight

Your video must demonstrate that:
1. **Users notice the service** - notification is visible and prominent
2. **Works without direct interaction** - timer continues when app is closed
3. **Provides critical information** - rest time is essential for workout
4. **Justifies foreground service** - requires continuous execution

## 📝 Example Video Script

**0:00-0:30**: "I'll log a workout set to start the rest timer. Notice the timer dialog appears immediately."

**0:30-1:00**: "Here's the persistent notification in the status bar. The timer is counting down and remains visible even when I close the app."

**1:00-1:30**: "Let me minimize the app and open another app. The timer continues running in the background - you can see it updating in the notification panel."

**1:30-2:00**: "I can interact with the timer through notification actions - adding or removing time - without opening the app."

**2:00-2:30**: "The timer is critical for workout timing. If this service stops, users lose track of rest periods between sets."

---

**Good luck with your submission!** 🚀


