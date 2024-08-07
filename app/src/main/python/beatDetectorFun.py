import librosa
import numpy as np

def detect_beats_and_frequencies(filepath, filename):
    y, sr = librosa.load(filepath + "/" + filename)

    # compute STFT for mono and stereo signals
    stft = np.abs(librosa.stft(np.asfortranarray(y)))

    # low frequency band for mono signal
    low_band = stft[:stft.shape[0]//3, :]

    # high frequency band for mono signal
    high_band = stft[2 * stft.shape[0]//3:, :]

    all_beats = []
    tempos = []
    for band in [low_band, high_band]:
        # compute the onset strength for each band
        onset_env = librosa.onset.onset_strength(sr=sr, S=band)

        # detect beats in each band
        tempo, beat_frames = librosa.beat.beat_track(onset_envelope=onset_env, tightness=400, sr=sr)
        tempos.append(tempo)

        # convert beat frames to time in ms and round to nearest integer
        beat_times = np.round(librosa.frames_to_time(beat_frames, sr=sr) * 1000000).astype(int)

        # get the corresponding energy for each beat
        beat_energies = onset_env[beat_frames]

        # create a list of tuples (timestamp, energy) for each band
        beats_band = [(time, energy) for time, energy in zip(beat_times, beat_energies)]

        all_beats.append(beats_band)

    return tempos[0], tempos[1], all_beats
