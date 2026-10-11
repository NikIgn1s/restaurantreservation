import argparse
from pathlib import Path

from app.data import schema
from app.data.synthetic import generate_dataset

DEFAULT_OUTPUT = Path("data/generated/bookings.csv")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Generate a synthetic bookings dataset with no-show labels")
    parser.add_argument("--rows", type=int, default=20000, help="number of bookings to generate")
    parser.add_argument("--seed", type=int, default=42, help="random seed, the same seed gives the same file")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT, help="destination csv file")
    parser.add_argument("--with-probability", action="store_true", help="also write the true no-show probability")
    return parser


def main(argv: list[str] | None = None) -> Path:
    args = build_parser().parse_args(argv)
    frame = generate_dataset(args.rows, seed=args.seed, include_probability=args.with_probability)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    frame.to_csv(args.out, index=False)
    rate = frame[schema.TARGET_COLUMN].mean()
    print(f"Wrote {len(frame)} rows to {args.out} (no-show rate {rate:.1%})")
    return args.out


if __name__ == "__main__":
    main()
