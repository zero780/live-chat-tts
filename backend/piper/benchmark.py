"""Measure local Piper synthesis speed without playing audio."""

from __future__ import annotations

import argparse
import time

from piper import PiperVoice


SAMPLES = (
    "Carolina, la voz Piper está lista.",
    "Bienvenidos al chat en vivo. Gracias por acompañarnos esta noche.",
    "Usuario noventa y nueve dice: hola, envío este mensaje para comprobar la pronunciación de números, nombres y palabras en español mexicano.",
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--runs", type=int, default=3)
    args = parser.parse_args()

    voice = PiperVoice.load(args.model)
    for index, text in enumerate(SAMPLES, start=1):
        durations: list[float] = []
        audio_seconds = 0.0
        for _ in range(args.runs):
            started = time.perf_counter()
            chunks = list(voice.synthesize(text))
            durations.append(time.perf_counter() - started)
            audio_seconds = sum(
                len(chunk.audio_int16_bytes)
                / (chunk.sample_rate * chunk.sample_width * chunk.sample_channels)
                for chunk in chunks
            )
        average = sum(durations) / len(durations)
        rtf = average / audio_seconds if audio_seconds else 0.0
        print(
            f"Sample {index}: synth={average * 1000:.0f} ms, "
            f"audio={audio_seconds * 1000:.0f} ms, RTF={rtf:.3f}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
