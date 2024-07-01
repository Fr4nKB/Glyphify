import librosa
import numpy as np

def detect_beats_and_frequencies(filepath):
    y, sr = librosa.load(filepath)
    
    # Separate the signal into different frequency bands
    D = np.abs(librosa.stft(y))
    bands = [D[i*D.shape[0]//5:(i+1)*D.shape[0]//5, :] for i in range(5)]

    all_beats = []
    for band in bands:
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

    return all_beats
