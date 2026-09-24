# Synthetic demo data

Generated output belongs in `data/generated/` and is ignored by Git. Generate the default reproducible dataset from the repository root with:

```powershell
uv sync --project analytics --group dev
uv run --project analytics python -m app.generate_cli --rig-count 3 --cycle-count 1000 --seed 20260101
```

The generator writes `rigs.csv`, `rigs.json`, `cycles.csv`, and `cycles.json`, replacing only those named files. It accepts `--start` and `--end` ISO 8601 timestamps (including a UTC offset), and `--output-dir` for a different destination. IDs are stable for repeatable local regeneration; the default time range is January 2026 UTC.

Normal values use arbitrary means, standard deviations, a small background trend, and seeded random noise. Eight percent of cycles are labelled as synthetic ground truth, divided among isolated single-feature spikes, gradual temperature drift, and correlated feature shifts. This is a software fixture, not a model of aircraft equipment. All units and values are arbitrary demonstration data, not real aircraft measurements or validated engineering limits. Ground-truth labels are only for offline evaluation and do not appear inside the inference `features` object.
