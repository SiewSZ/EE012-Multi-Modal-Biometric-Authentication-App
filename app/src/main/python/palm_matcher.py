import cv2
import numpy as np

def find_and_crop_palm(image_path):
    """
    Loads an image of a hand, isolates the palm region, and returns a cropped,
    standardized image of just the palm.
    """
    try:
        img = cv2.imread(image_path)
        if img is None:
            print(f"Error: Could not read image at {image_path}")
            return None

        # Convert to grayscale
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

        # Apply a threshold to create a binary image. This helps separate the hand from the background.
        # We use OTSU's method to automatically determine the best threshold value.
        _, thresh = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)

        # Find the largest contour, which should be the hand
        contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            print(f"Warning: No contours found in {image_path}. Cannot isolate palm.")
            return None
        hand_contour = max(contours, key=cv2.contourArea)

        # --- Find the center of the palm using Distance Transform ---
        # This creates a map where each pixel's value is its distance to the nearest edge.
        # The brightest spot will be the center of the largest area, i.e., the palm.
        dist_transform = cv2.distanceTransform(thresh, cv2.DIST_L2, 5)
        _, _, _, max_loc = cv2.minMaxLoc(dist_transform) # We only need the location of the max value
        palm_center_x, palm_center_y = max_loc

        # Define a standard size for the cropped palm region (e.g., 300x300 pixels)
        crop_size = 300
        half_crop = crop_size // 2

        # Calculate the coordinates for the crop
        x_start = max(0, palm_center_x - half_crop)
        y_start = max(0, palm_center_y - half_crop)
        x_end = x_start + crop_size
        y_end = y_start + crop_size

        # Crop the original grayscale image to get just the palm
        palm_crop = gray[y_start:y_end, x_start:x_end]

        # Verify the crop has the correct dimensions
        if palm_crop.shape[0] != crop_size or palm_crop.shape[1] != crop_size:
            print(f"Warning: Could not create a full-sized palm crop. The hand might be too close to the edge.")
            # If the crop is bad, fall back to a simple resize of the whole hand to maintain functionality
            return cv2.resize(gray, (crop_size, crop_size), interpolation=cv2.INTER_AREA)

        print(f"Successfully isolated and cropped palm region for {image_path}")
        return palm_crop

    except Exception as e:
        print(f"An error occurred during palm isolation: {e}")
        return None


def compare_palms_py(image_path_1, image_path_2):
    """
    Compares two hand images by first isolating the palm region in each,
    and then using ORB feature matching on just the palms.
    """
    print(f"--- Starting Advanced Palm Comparison ---")

    # Step 1: Isolate the palm region from each full hand image
    palm1 = find_and_crop_palm(image_path_1)
    palm2 = find_and_crop_palm(image_path_2)

    if palm1 is None or palm2 is None:
        print("Comparison failed because a palm region could not be isolated.")
        return 0.0

    # Step 2: Initialize ORB detector
    # We can use fewer features now since we are focused on a smaller, more consistent region.
    orb = cv2.ORB_create(nfeatures=1500)

    # Step 3: Find keypoints and descriptors for both PALMS
    kp1, des1 = orb.detectAndCompute(palm1, None)
    kp2, des2 = orb.detectAndCompute(palm2, None)

    if des1 is None or des2 is None:
        print("Comparison failed: no features detected in one or both isolated palm regions.")
        return 0.0

    print(f"Found {len(kp1)} features in palm 1 and {len(kp2)} features in palm 2.")

    # Step 4: Match features using Brute-Force Matcher
    bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
    matches = bf.match(des1, des2)

    # Step 5: Filter matches to keep only the best ones
    matches = sorted(matches, key=lambda x: x.distance)
    good_matches = matches[:50] # Consider the top 50 matches
    print(f"Found {len(matches)} initial matches, keeping the best {len(good_matches)}.")

    if not good_matches:
        return 0.0

    # Step 6: Calculate score based on the quality (distance) of good matches
    total_inverse_distance = sum(1.0 / (m.distance + 1e-6) for m in good_matches)

    # A normalization factor. This is an empirical value that you can tune.
    # A lower value makes the check stricter.
    normalization_factor = 2.5
    final_score = total_inverse_distance / normalization_factor

    # Cap the score at 1.0
    final_score = min(final_score, 1.0)

    print(f"Final Palm Match Score: {final_score:.4f}")

    return final_score

