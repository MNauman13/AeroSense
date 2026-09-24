"""Command line entry point for the deterministic synthetic data generator."""

from __future__ import annotations

import argparse
from pathlib import Path

from app.generator import (
    DEFAULT_END,
    DEFAULT_SEED,
    DEFAULT_START,
    default_output_dir,
    generate_dataset,
    write_dataset,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rig-count", type=int, default=3)
    parser.add_argument("--cycle-count", type=int, default=1000)
    parser.add_argument("--start", default=DEFAULT_START, help="ISO 8601 timestamp with offset")
    parser.add_argument("--end", default=DEFAULT_END, help="ISO 8601 timestamp with offset")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--output-dir", type=Path, default=default_output_dir())
    return parser


def main() -> None:
    args = build_parser().parse_args()
    dataset = generate_dataset(
        rig_count=args.rig_count,
        cycle_count=args.cycle_count,
        start=args.start,
        end=args.end,
        seed=args.seed,
    )
    write_dataset(dataset, args.output_dir)
    print(
        f"Wrote {len(dataset.rigs)} synthetic rigs and {len(dataset.cycles)} synthetic cycles "
        f"to {args.output_dir.resolve()} (seed={args.seed})."
    )


if __name__ == "__main__":
    main()
