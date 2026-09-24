from __future__ import annotations

import json

import pandas as pd
import pytest

from app.generator import FEATURES, generate_dataset, write_dataset


def test_generation_has_requested_counts_stable_ids_and_contract_features() -> None:
    dataset = generate_dataset(rig_count=4, cycle_count=150, seed=19)

    assert len(dataset.rigs) == 4
    assert len(dataset.cycles) == 150
    assert dataset.rigs["id"].is_unique
    assert dataset.cycles["id"].is_unique
    assert dataset.cycles["cycleCode"].tolist()[0] == "CYC-000001"
    assert set(FEATURES).issubset(dataset.cycles.columns)
    assert dataset.cycles[list(FEATURES)].notna().all().all()
    assert all("synthetic" in description.lower() for description in dataset.rigs["description"])


def test_generation_is_deterministic_for_same_configuration() -> None:
    first = generate_dataset(rig_count=2, cycle_count=240, seed=123)
    second = generate_dataset(rig_count=2, cycle_count=240, seed=123)

    pd.testing.assert_frame_equal(first.rigs, second.rigs)
    pd.testing.assert_frame_equal(first.cycles, second.cycles)


def test_anomaly_labels_are_a_small_known_fraction_and_not_features() -> None:
    dataset = generate_dataset(cycle_count=1000, seed=DEFAULT_TEST_SEED)
    labels = dataset.cycles["syntheticLabel"]
    assert 0.07 <= labels.mean() <= 0.09

    feature_vectors = dataset.cycle_records()
    assert "syntheticLabel" not in feature_vectors[0]["features"]
    assert set(feature_vectors[0]["features"]) == set(FEATURES)
    assert sum(row["syntheticLabel"] for row in feature_vectors) == int(labels.sum())


def test_timestamps_are_utc_and_ids_repeat_across_calls() -> None:
    first = generate_dataset(cycle_count=12, seed=1)
    second = generate_dataset(cycle_count=12, seed=1)

    assert first.cycles["recordedAt"].str.endswith("Z").all()
    assert first.cycles["id"].tolist() == second.cycles["id"].tolist()


@pytest.mark.parametrize(
    ("options", "message"),
    [
        ({"rig_count": 0}, "rig_count"),
        ({"cycle_count": 0}, "cycle_count"),
        ({"start": "2026-01-01T00:00:00"}, "UTC offset"),
        ({"start": "2026-02-01T00:00:00Z", "end": "2026-01-01T00:00:00Z"}, "earlier"),
    ],
)
def test_invalid_configuration_is_rejected(options: dict[str, object], message: str) -> None:
    with pytest.raises(ValueError, match=message):
        generate_dataset(**options)  # type: ignore[arg-type]


def test_writer_overwrites_only_named_csv_and_json_outputs(tmp_path) -> None:
    dataset = generate_dataset(rig_count=1, cycle_count=15, seed=4)
    keep = tmp_path / "unrelated.txt"
    keep.write_text("keep", encoding="utf-8")

    write_dataset(dataset, tmp_path)
    write_dataset(dataset, tmp_path)

    assert sorted(path.name for path in tmp_path.iterdir()) == [
        "cycles.csv",
        "cycles.json",
        "rigs.csv",
        "rigs.json",
        "unrelated.txt",
    ]
    data = json.loads((tmp_path / "cycles.json").read_text(encoding="utf-8"))
    assert len(data) == 15
    assert set(data[0]["features"]) == set(FEATURES)
    assert keep.read_text(encoding="utf-8") == "keep"


DEFAULT_TEST_SEED = 84
