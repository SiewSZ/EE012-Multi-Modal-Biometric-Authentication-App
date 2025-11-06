import cv2
from skimage.metrics import structural_similarity as ssim
import face_recognition
import numpy as np

# --- Helper function for face comparison ---
def face_confidence(face_distance, face_match_threshold=0.6):
    """
    Calculates a confidence score (percentage) from a face distance.
    A lower distance means a higher confidence.
    """
    if face_distance > face_match_threshold:
        # If the distance is too high, the confidence is low.
        range_val = (1.0 - face_match_threshold)
        linear_val = (1.0 - face_distance) / (range_val * 2.0)
        return max(0.0, linear_val * 100)
    else:
        # If the distance is low, the confidence is high.
        # This formula provides a non-linear boost to confidence at lower distances.
        range_val = face_match_threshold
        linear_val = (1.0 - (face_distance / range_val))
        return max(0.0, (linear_val + ((1.0 - linear_val) * pow(linear_val, 2))) * 100)


# --- NEW FUNCTION FOR FACE RECOGNITION ---
def compare_faces(reference_image_path, new_image_path):
    """
    Compares two faces using the face_recognition library and returns a confidence score.
    """
    print(f"Chaquopy: Received request to compare faces: '{reference_image_path}' and '{new_image_path}'")

    try:
        # 1. Load the reference image and find its encoding
        reference_image = face_recognition.load_image_file(reference_image_path)
        # We assume the reference image has exactly one face.
        reference_face_encodings = face_recognition.face_encodings(reference_image)

        if not reference_face_encodings:
            print("Error: No face found in the reference image.")
            return 0.0
        reference_encoding = reference_face_encodings[0]

        # 2. Load the new image and find its encoding
        new_image = face_recognition.load_image_file(new_image_path)
        # Find all faces in the new image to handle cases where multiple are present.
        new_face_encodings = face_recognition.face_encodings(new_image)

        if not new_face_encodings:
            print("Error: No face found in the new image for verification.")
            return 0.0

        # Handle multiple faces in the new image - we'll just use the first one found.
        new_encoding = new_face_encodings[0]
        if len(new_face_encodings) > 1:
            print("Warning: Multiple faces detected in the new image. Using the first one found.")

        # 3. Compare the faces and calculate distance
        # face_distance returns a list of distances; since we compare one-to-one, we take the first element.
        face_distances = face_recognition.face_distance([reference_encoding], new_encoding)

        if not face_distances:
            return 0.0

        distance = face_distances[0]
        print(f"Calculated face distance: {distance}")

        # 4. Convert the distance to a confidence score (0-100)
        confidence = face_confidence(distance)
        print(f"Calculated face confidence: {confidence}%")

        # Return the score as a value between 0.0 and 1.0
        return confidence / 100.0

    except Exception as e:
        print(f"An error occurred in compare_faces: {e}")
        return 0.0


# --- EXISTING FUNCTION FOR PALM COMPARISON ---
def compare_palms(image_a_path, image_b_path):
    """
    Compares two palm images using the Structural Similarity Index (SSIM).
    """
    print(f"Chaquopy: Received request to compare palms: '{image_a_path}' and '{image_b_path}'")
    try:
        image_a = cv2.imread(image_a_path, cv2.IMREAD_GRAYSCALE)
        image_b = cv2.imread(image_b_path, cv2.IMREAD_GRAYSCALE)
        if image_a is None or image_b is None:
            return 0.0
        standard_size = (512, 512)
        image_a_resized = cv2.resize(image_a, standard_size)
        image_b_resized = cv2.resize(image_b, standard_size)
        score, _ = ssim(image_a_resized, image_b_resized, full=True, data_range=255)
        print(f"SSIM comparison score: {score}")
        return max(0.0, score)
    except Exception as e:
        print(f"An error occurred in compare_palms: {e}")
        return 0.0

