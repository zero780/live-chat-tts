"""Persistent local Piper worker used by the Java backend."""

from __future__ import annotations

import argparse
import base64
import ctypes
import os
import sys

from piper import PiperVoice
from piper.config import SynthesisConfig


def emit(value: str) -> None:
    print(value, flush=True)


def set_below_normal_priority() -> None:
    if os.name != "nt":
        return
    try:
        below_normal_priority_class = 0x00004000
        ctypes.windll.kernel32.SetPriorityClass(
            ctypes.windll.kernel32.GetCurrentProcess(), below_normal_priority_class
        )
    except OSError:
        pass


def decode(value: str) -> str:
    return base64.b64decode(value.encode("ascii"), validate=True).decode("utf-8")


def length_scale(rate: int) -> float:
    return max(0.5, min(1.5, 1.0 - (rate / 20.0)))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    args = parser.parse_args()

    set_below_normal_priority()
    voice = PiperVoice.load(args.model)
    emit("READY")

    for line in sys.stdin:
        command = line.strip()
        if command == "EXIT":
            return 0
        try:
            encoded_text, raw_rate = command.split("|", 1)
            text = decode(encoded_text)
            rate = max(-10, min(10, int(raw_rate)))
            config = SynthesisConfig(length_scale=length_scale(rate))
            for chunk in voice.synthesize(text, syn_config=config):
                payload = base64.b64encode(chunk.audio_int16_bytes).decode("ascii")
                emit(f"AUDIO|{chunk.sample_rate}|{payload}")
            emit("OK")
        except Exception as error:  # The parent protocol transports worker errors.
            detail = base64.b64encode(str(error).encode("utf-8")).decode("ascii")
            emit(f"ERR|{detail}")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    raise SystemExit(main())
