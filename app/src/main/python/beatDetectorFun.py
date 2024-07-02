import librosa
import numpy as np

def detect_beats_and_frequencies(filepath):
    y_stereo, sr = librosa.load(filepath, mono=False)  # Load the audio file as stereo
    y_mono = librosa.to_mono(y_stereo)  # Convert the stereo audio to mono

    # Compute STFT for mono and stereo signals
    D_mono = np.abs(librosa.stft(np.asfortranarray(y_mono)))
    D_left = np.abs(librosa.stft(np.asfortranarray(y_stereo[0])))
    D_right = np.abs(librosa.stft(np.asfortranarray(y_stereo[1])))

    # Define the low frequency band for mono signal
    low_band_mono = D_mono[:D_mono.shape[0]//3, :]

    # Define the high frequency band for mono signal
    high_band_mono = D_mono[2*D_mono.shape[0]//3:, :]

    # Define the high frequency bands for left and right channels
    high_band_left = D_left[2*D_left.shape[0]//3:, :]
    high_band_right = D_right[2*D_right.shape[0]//3:, :]

    all_beats = []
    for band in [high_band_left, high_band_right, low_band_mono, high_band_mono]:
        # Compute the onset strength for each band
        onset_env = librosa.onset.onset_strength(sr=sr, S=band)

        # Detect beats in each band
        beat_frames = librosa.beat.beat_track(onset_envelope=onset_env, tightness=400, sr=sr)[1]

        # Convert beat frames to time (in milliseconds) and round to nearest integer
        beat_times = np.round(librosa.frames_to_time(beat_frames, sr=sr) * 1000).astype(int)

        # Get the corresponding energy for each beat
        beat_energies = onset_env[beat_frames]

        # Create a list of tuples (timestamp, energy) for each band
        beats_band = [(time, energy) for time, energy in zip(beat_times, beat_energies)]

        all_beats.append(beats_band)

    all_beats.append(all_beats[2])

    return all_beats
