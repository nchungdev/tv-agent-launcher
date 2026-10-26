# Camera Streaming & Smart Home Integration Guide

This guide details how to configure RTSP video streaming for IP cameras (e.g., TP-Link Tapo) and integrate them into **Home Assistant** and the **TV Launcher**.

---

## 1. Enabling RTSP / ONVIF on Tapo Cameras

By default, TP-Link Tapo cameras close port 554 for security until a dedicated camera account is created.

1. Open the **Tapo app** on your phone.
2. Select your camera device.
3. Tap **Device Settings** (gear icon in the top right).
4. Navigate to **Advanced Settings -> Camera Account**.
5. Create local credentials:
   - **Username**: e.g., `tapo_stream`
   - **Password**: Secure local password (do not use your TP-Link Cloud password).
6. Save. The camera will automatically open RTSP port `554` on the local LAN.

---

## 2. RTSP Stream URLs

Once configured, the camera exposes two RTSP endpoints:

- **High-Definition Stream (Main Stream - HD/2K):**
  ```text
  rtsp://<username>:<password>@<camera-ip>:554/stream1
  ```
- **Standard-Definition Stream (Sub Stream - SD, low latency):**
  ```text
  rtsp://<username>:<password>@<camera-ip>:554/stream2
  ```

### Quick Verification via CLI:
```bash
# Preview directly using ffplay
ffplay "rtsp://<username>:<password>@<camera-ip>:554/stream1"
```
Or open in **VLC**: `Media -> Open Network Stream -> Enter RTSP URL`.

---

## 3. Integration with Home Assistant

### Option A: Tapo Camera Control (Recommended via HACS)
Provides full control over PTZ, Privacy Mode, alarms, and WebRTC streaming:
1. Open **HACS** in Home Assistant -> Search for `Tapo: Cameras Control`.
2. Install and restart Home Assistant.
3. Go to **Settings -> Devices & Services -> Add Integration -> Tapo**.
4. Enter Camera IP, username, and password created in Step 1.
5. The integration exposes `camera.<name>_hd_stream` and controls for motion detection.

### Option B: Generic Camera / ONVIF
1. Go to **Settings -> Devices & Services -> Add Integration -> Generic Camera**.
2. Set Stream Source URL to `rtsp://<username>:<password>@<camera-ip>:554/stream1`.
3. Set Transport Protocol to `TCP` to prevent packet drops.

---

## 4. Displaying on Android TV

1. **Via Home Assistant Widget**:
   The launcher's Smart Home panel and widgets can trigger camera views directly through Home Assistant entities.
2. **Via Native RTSP Player / WebRTC**:
   Use lightweight RTSP/WebRTC players (or companion `services/camera`) to stream video with minimal hardware decoding overhead on 1 GB RAM TVs.
