# 🔒 Privacy Policy Setup Guide

## ✅ Why You Need This

Google Play **requires a privacy policy** for ALL apps that handle user data, regardless of age rating. Even though your app is 18+, you still need a privacy policy because:

1. **The app stores user data** (workouts, exercises, sets)
2. **The app uses permissions** (notifications, foreground service, vibration)
3. **Google Play policy requirement** for any app that handles data

The message about "under 13" is just additional context - it's required for ALL apps.

## 📄 Privacy Policy Files Created

I've created `privacy_policy.html` - a complete privacy policy that:

✅ States the app is 18+ only  
✅ Explains all data is stored locally (no internet, no sharing)  
✅ Documents all permissions and their usage  
✅ Complies with GDPR and Google Play requirements  
✅ Clear and user-friendly format  

## 🌐 Hosting Options

You need to host the privacy policy at a **publicly accessible URL**. Here are options:

### Option 1: GitHub Pages (Free & Easy)
1. Create a GitHub repository (or use existing one)
2. Create a `docs` folder
3. Copy `privacy_policy.html` to `docs/privacy_policy.html`
4. Enable GitHub Pages in repository settings
5. Your URL will be: `https://[username].github.io/[repo-name]/privacy_policy.html`

### Option 2: Your Own Website
1. Upload `privacy_policy.html` to your web server
2. Ensure it's accessible via HTTPS
3. Use the direct URL

### Option 3: Google Sites (Free)
1. Go to sites.google.com
2. Create a new site
3. Copy the HTML content (body only) into the page
4. Publish and use the published URL

### Option 4: Privacy Policy Generator Sites
- privacy-policy-template.com
- termsfeed.com
- privacypolicygenerator.info

## 📤 Adding to Google Play Console

1. **Go to Google Play Console**
   - Navigate to your app
   - Go to **Policy** → **App content** (or **Store listing** → **Privacy policy**)

2. **Enter Privacy Policy URL**
   - Paste your hosted privacy policy URL
   - Click **Save**

3. **Additional Info (if prompted)**
   - **Target Audience:** 18+
   - **Data Collection:** Select "No, we do not collect any user data" (since it's all local)
   - **Data Sharing:** Select "No" (no data is shared)

## ✅ Data Collection Declaration

When Google Play Console asks about data collection:

### **What Data Do You Collect?**
Select: **"No, we do not collect any user data"** or **"No data collection"**

Even though the app stores workout data, this is **local-only storage** on the user's device. Google considers this as "no collection" since:
- Data never leaves the device
- No transmission to servers
- No third-party services involved

### **Permissions Declaration**
You'll also need to declare why you use each permission:

- **POST_NOTIFICATIONS:** "Rest timer notifications during workouts"
- **FOREGROUND_SERVICE_SPECIAL_USE:** "Continuous rest timer countdown visible when app is minimized"
- **VIBRATE:** "Haptic feedback when rest timer completes"

## 🎯 Key Points in Your Privacy Policy

The privacy policy I created covers:

1. ✅ **18+ only** - Clearly states age restriction
2. ✅ **Local storage only** - All data stays on device
3. ✅ **No data collection** - Nothing sent to servers
4. ✅ **No sharing** - No third-party services
5. ✅ **Permission explanations** - Why each permission is used
6. ✅ **User rights** - How to access/delete data
7. ✅ **GDPR compliant** - Meets EU requirements

## 📋 Checklist Before Submission

- [ ] Host `privacy_policy.html` at a publicly accessible URL
- [ ] Verify URL is accessible via HTTPS
- [ ] Test URL opens correctly in browser
- [ ] Add URL to Google Play Console → Store listing → Privacy policy
- [ ] Declare data collection as "No data collection" (local-only)
- [ ] Complete any additional data safety forms if prompted
- [ ] Save and submit for review

## 🔍 Testing

After hosting:
1. Open the privacy policy URL in a browser
2. Verify it displays correctly on mobile devices
3. Ensure all sections are readable
4. Check that the "Last Updated" date appears correctly

## 📝 Optional: In-App Privacy Policy Link

You can also add a privacy policy link within your app:

1. Add a menu item or button in Settings
2. Open the privacy policy URL in a browser:
   ```kotlin
   val intent = Intent(Intent.ACTION_VIEW, Uri.parse("YOUR_PRIVACY_POLICY_URL"))
   startActivity(intent)
   ```

---

**Need help?** The privacy policy is ready to use - just host it and add the URL to Google Play Console!


