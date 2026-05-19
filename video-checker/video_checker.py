import cv2
import numpy as np
import tkinter as tk
from tkinter import filedialog, messagebox
import os
from datetime import datetime, timedelta

# ===== CONFIG =====
SAMPLE_INTERVAL_SECONDS = 2          # How often to sample frames
SHAKE_THRESHOLD = 15.0               # Motion magnitude threshold for gimbal shake
BANNER_BRIGHTNESS_THRESHOLD = 200    # Brightness threshold for banner detection
BANNER_HEIGHT_RATIO = 0.05           # Banner must occupy ~5% of frame height
LOG_FOLDER = "video_check_logs"

# ===== SETUP LOG FOLDER =====
os.makedirs(LOG_FOLDER, exist_ok=True)

def log_message(log_file, message):
    """Append a timestamped message to the log file."""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    full_message = f"[{timestamp}] {message}"
    print(full_message)
    with open(log_file, "a", encoding="utf-8") as f:
        f.write(full_message + "\n")

def format_timecode(seconds):
    """Convert seconds to HH:MM:SS format."""
    return str(timedelta(seconds=int(seconds)))

def detect_warp_banner(frame):
    """
    Detects the Premiere Pro warp stabilizer banner.
    The banner appears as a horizontal bar across the frame
    with distinctive colour/brightness.
    """
    height, width = frame.shape[:2]
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    # Scan horizontal strips for a uniformly bright/coloured bar
    for y in range(0, height - int(height * BANNER_HEIGHT_RATIO), 10):
        strip = gray[y:y + int(height * BANNER_HEIGHT_RATIO), :]
        mean_brightness = np.mean(strip)
        std_brightness = np.std(strip)

        # Banner is typically a uniform bright band (low std, high brightness)
        if mean_brightness > BANNER_BRIGHTNESS_THRESHOLD and std_brightness < 20:
            return True
    return False

def detect_shake(prev_frame, curr_frame):
    """
    Detect gimbal shake by measuring optical flow between frames.
    Returns the average motion magnitude.
    """
    prev_gray = cv2.cvtColor(prev_frame, cv2.COLOR_BGR2GRAY)
    curr_gray = cv2.cvtColor(curr_frame, cv2.COLOR_BGR2GRAY)

    flow = cv2.calcOpticalFlowFarneback(
        prev_gray, curr_gray, None,
        0.5, 3, 15, 3, 5, 1.2, 0
    )
    magnitude = np.sqrt(flow[..., 0]**2 + flow[..., 1]**2)
    avg_motion = np.mean(magnitude)
    return avg_motion

def analyse_video(video_path):
    """Main analysis routine."""
    video_name = os.path.basename(video_path)
    log_file = os.path.join(
        LOG_FOLDER,
        f"{os.path.splitext(video_name)[0]}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
    )

    log_message(log_file, f"Starting analysis: {video_name}")

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        log_message(log_file, "ERROR: Could not open video file.")
        messagebox.showerror("Error", f"Could not open: {video_name}")
        return

    fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration = total_frames / fps
    sample_frame_interval = int(fps * SAMPLE_INTERVAL_SECONDS)

    log_message(log_file, f"Duration: {format_timecode(duration)} | FPS: {fps:.1f}")

    issues_found = []
    prev_frame = None
    frame_idx = 0

    while True:
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_idx)
        ret, frame = cap.read()
        if not ret:
            break

        current_time = frame_idx / fps
        timecode = format_timecode(current_time)

        # --- Warp stabilizer banner check ---
        if detect_warp_banner(frame):
            msg = f"⚠ WARP STABILIZER BANNER detected at {timecode}"
            log_message(log_file, msg)
            issues_found.append(msg)

        # --- Gimbal shake check (needs previous frame) ---
        if prev_frame is not None:
            motion = detect_shake(prev_frame, frame)
            if motion > SHAKE_THRESHOLD:
                msg = f"⚠ EXCESSIVE SHAKE (motion={motion:.2f}) at {timecode}"
                log_message(log_file, msg)
                issues_found.append(msg)

        prev_frame = frame
        frame_idx += sample_frame_interval

    cap.release()

    log_message(log_file, f"Analysis complete. {len(issues_found)} issue(s) found.")

    # Pop-up notification
    if issues_found:
        summary = "\n".join(issues_found[:15])
        if len(issues_found) > 15:
            summary += f"\n... and {len(issues_found) - 15} more (see log)"
        messagebox.showwarning(
            "Issues Found",
            f"{len(issues_found)} issue(s) found in {video_name}:\n\n{summary}\n\nLog: {log_file}"
        )
    else:
        messagebox.showinfo(
            "All Clear",
            f"No issues detected in {video_name}.\n\nLog: {log_file}"
        )

def main():
    """Open file picker and start analysis."""
    root = tk.Tk()
    root.withdraw()

    video_path = filedialog.askopenfilename(
        title="Select rendered video to check",
        filetypes=[("Video files", "*.mp4 *.mov *.avi *.mkv"), ("All files", "*.*")]
    )

    if not video_path:
        return

    analyse_video(video_path)

if __name__ == "__main__":
    main()
