import face_recognition
import numpy as np
import math
import warnings
import cv2 # Make sure cv2 is imported
import os

# (The face_confidence function remains the same)
def face_confidence(face_distance, face_match_threshold=0.6):
    """
    Calculates a confidence score (percentage) from a face distance.
    A lower distance means a higher confidence.
    """
    if face_distance > face_match_threshold:
        range_val = (1.0 - face_match_threshold)
        linear_val = (1.0 - face_distance) / (range_val * 2.0)
        return max(0.0, linear_val * 100)
    else:
        range_val = face_match_threshold
        linear_val = 1.0 - (face_distance / range_val)
        value = (linear_val + ((1.0 - linear_val) * math.pow((linear_val - 0.5) * 2, 0.2))) * 100
        return max(0.0, round(value, 2))


# --- IMPROVED FUNCTION FOR FACE RECOGNITION ---
def get_face_encoding_robust(image_path):
    """
    Tries to find a face encoding in an image, rotating it if necessary.
    """
    # Load image using OpenCV to easily rotate it
    image = cv2.imread(image_path)
    if image is None:
        print(f"Warning: Could not load image at {image_path}")
        return None

    # Face_recognition library expects RGB, but OpenCV loads as BGR
    rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

    # 1. Try the original orientation first
    encodings = face_recognition.face_encodings(rgb_image)
    if encodings:
        return encodings[0]

    # 2. If no face is found, try rotating the image
    print(f"No face found in original orientation for {os.path.basename(image_path)}. Trying rotations...")
    for angle in [cv2.ROTATE_90_CLOCKWISE, cv2.ROTATE_180, cv2.ROTATE_90_COUNTERCLOCKWISE]:
        rotated_img = cv2.rotate(rgb_image, angle)
        encodings = face_recognition.face_encodings(rotated_img)
        if encodings:
            print(f"Found face after rotating.")
            return encodings[0]

    return None # Return None if no face is found in any orientation


def compare_faces(reference_image_path, new_image_path):
    """
    Compares two faces from image files and returns a confidence score.
    """
    print(f"Chaquopy: Received request to compare faces: '{os.path.basename(reference_image_path)}' and '{os.path.basename(new_image_path)}'")

    try:
        # 1. Get reference encoding (robustly)
        reference_encoding = get_face_encoding_robust(reference_image_path)
        if reference_encoding is None:
            print("FATAL: No face found in the reference image, even after rotations.")
            return 0.0

        # 2. Get new image encoding (robustly)
        new_encoding = get_face_encoding_robust(new_image_path)
        if new_encoding is None:
            print("Error: No face found in the new verification image, even after rotations.")
            return 0.0

        # 3. Compare the faces and calculate distance
        face_distances = face_recognition.face_distance([reference_encoding], new_encoding)
        if not face_distances.size > 0:
            return 0.0

        distance = face_distances[0]
        print(f"Calculated face distance: {distance:.4f}")

        # 4. Convert the distance to a confidence score
        confidence = face_confidence(distance)
        print(f"Calculated face confidence: {confidence:.2f}%")

        # 5. Return the score
        return confidence / 100.0

    except Exception as e:
        print(f"An error occurred in compare_faces: {e}")
        return 0.0

# (Your compare_palms function can remain unchanged below this)
# ...
