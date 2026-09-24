"""Load stable source metadata and paragraph-sized chunks from local Markdown notes."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Passage:
    source_id: str
    title: str
    excerpt: str


def load_corpus(notes_dir: Path | None = None) -> list[Passage]:
    """Read local Markdown notes and assign stable IDs to non-empty paragraphs."""
    directory = notes_dir or Path(__file__).resolve().parents[2] / "reference_notes"
    passages: list[Passage] = []
    for path in sorted(directory.glob("*.md")):
        source_id, title, content = _parse_note(path.read_text(encoding="utf-8"))
        content = re.sub(r"(?m)^#{1,6}\s+.*\n?", "", content)
        paragraphs = [
            re.sub(r"\s+", " ", paragraph).strip() for paragraph in re.split(r"\n\s*\n", content)
        ]
        for index, paragraph in enumerate(filter(None, paragraphs), start=1):
            passages.append(
                Passage(
                    source_id=f"{source_id}#chunk-{index:02d}",
                    title=title,
                    excerpt=paragraph,
                )
            )
    if not passages:
        raise ValueError("The fictional reference corpus is empty.")
    return passages


def _parse_note(text: str) -> tuple[str, str, str]:
    if not text.startswith("---\n"):
        raise ValueError("Reference notes must begin with source metadata.")
    header, separator, content = text[4:].partition("\n---\n")
    if not separator:
        raise ValueError("Reference note metadata is incomplete.")
    metadata: dict[str, str] = {}
    for line in header.splitlines():
        key, delimiter, value = line.partition(":")
        if delimiter:
            metadata[key.strip()] = value.strip()
    source_id = metadata.get("source_id", "")
    title = metadata.get("title", "")
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", source_id) or not title:
        raise ValueError("Reference notes need a stable source_id and title.")
    return source_id, title, content.strip()
