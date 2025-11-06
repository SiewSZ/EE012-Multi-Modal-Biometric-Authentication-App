import numpy as np
from python_speech_features import mfcc, delta
from sklearn.mixture import GaussianMixture
import scipy.io.wavfile as wav
import os
import math

def _extract_features(wav_path: str):
    """
    Reads a WAV file and extracts MFCC features and their deltas.
    """
    try:
        sr, audio = wav.read(wav_path)
        static_features = mfcc(audio, sr, nfilt=40, numcep=20)
        delta_features = delta(static_features, 2)
        combined_features = np.hstack((static_features, delta_features))
        return combined_features
    except Exception as e:
        print(f"Feature extraction failed for {os.path.basename(wav_path)}: {e}")
        return None

def _calibrate_score(raw_score: float) -> float:
    """
    Applies an aggressive, non-linear curve based on your analogy.
    Scores above the midpoint are rapidly scaled towards 100%.
    Scores below the midpoint are rapidly pushed towards 0%.
    """
    # The 50% line, set between your best fake score (-125.8) and worst real score (-118.5)
    midpoint = -123.0

    # How aggressive the curve is. A higher number means a sharper "hockey-stick" shape.
    aggressiveness = 3.0

    # Calculate the difference from the midpoint
    difference = raw_score - midpoint

    # If the score is worse than the midpoint, push it aggressively to 0%.
    if difference < 0:
        # A small negative difference results in a score very close to 0.
        # This is the "capped at 1km/h" part of your analogy.
        final_score = 0.5 * math.exp(difference * 0.5) # Softly pushes to 0
    else:
        # If the score is better than the midpoint, apply the "speeding up" curve.
        # This is the "10km/h -> 20km/h" part of your analogy.
        # We'll scale the difference to a 0-1 range and apply a power function.
        # A typical "good" range is about 15 points (e.g., from -123 to -108).
        scale = 15.0
        normalized_improvement = difference / scale
        # The power function makes small improvements result in small gains,
        # and large improvements result in huge gains.
        final_score = 0.5 + 0.5 * (normalized_improvement ** (1/aggressiveness))

    # Clamp the final result between 0.0 and 1.0 to handle any edge cases.
    final_score = max(0.0, min(1.0, final_score))

    return final_score

# --- THIS IS THE ONLY FUNCTION KOTLIN WILL CALL ---
def verify_voice_on_the_fly(reference_wav_path: str, new_wav_path: str) -> float:
    """
    Trains the optimal GMM and applies your custom, aggressive calibration curve.
    """
    print(f"--- Starting on-the-fly verification (USER-DEFINED CURVE) ---")
    print(f"Reference: {os.path.basename(reference_wav_path)}")
    print(f"New Audio: {os.path.basename(new_wav_path)}")

    try:
        reference_features = _extract_features(reference_wav_path)
        new_features = _extract_features(new_wav_path)

        if reference_features is None or new_features is None:
            print("Verification failed due to feature extraction error.")
            return 0.0

        # <<< USING THE PROVEN ROBUST GMM >>>
        gmm = GaussianMixture(n_components=16, covariance_type='diag', n_init=3)
        gmm.fit(reference_features)
        print("GMM model (16 components with deltas) trained successfully in memory.")

        # Get the raw log-likelihood score from the model.
        score = gmm.score(new_features)

        # <<< APPLYING YOUR CUSTOM CALIBRATION CURVE >>>
        final_score = _calibrate_score(score)

        print(f"GMM Log-Likelihood Score: {score:.4f}")
        print(f"Final Calibrated Score: {final_score:.4f}")

        return float(final_score)

    except Exception as e:
        print(f"FATAL: On-the-fly verification failed. Error: {e}")
        return 0.0
