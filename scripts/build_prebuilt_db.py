#!/usr/bin/env python3
"""Build the Hanzi Writing Room database and separate curriculum metadata DB.

Python standard library only. Run from the repository root:
  python scripts/build_prebuilt_db.py \
    --assets-dir app/src/main/assets \
    --output app/src/main/assets/hanzi_database_prebuilt.db \
    --metadata-output app/src/main/assets/hanzi_vocabulary_metadata.db
"""
from __future__ import annotations

import argparse
import collections
import csv
import json
import re
import sqlite3
import sys
from pathlib import Path
from typing import Any, Iterable

ROOM_DATABASE_VERSION = 3
METADATA_DATABASE_VERSION = 1
CEDICT_PATTERN = re.compile(r"^(\S+)\s+(\S+)\s+\[(.+?)\]\s+/(.+)/$")

MAIN_SCHEMA = """
CREATE TABLE hanzi_characters (
    character TEXT NOT NULL PRIMARY KEY,
    pinyin TEXT NOT NULL,
    definition TEXT NOT NULL,
    radical TEXT NOT NULL,
    decomposition TEXT NOT NULL,
    matchesJson TEXT NOT NULL,
    mmaDefinition TEXT NOT NULL
);
CREATE TABLE hanzi_graphics (
    character TEXT NOT NULL PRIMARY KEY,
    rawJson TEXT NOT NULL
);
CREATE TABLE practice_lists (
    listId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE TABLE list_items (
    listId INTEGER NOT NULL,
    character TEXT NOT NULL,
    PRIMARY KEY(listId, character)
);
CREATE TABLE hanzi_word_forms (
    simplified TEXT NOT NULL,
    traditional TEXT NOT NULL,
    PRIMARY KEY(simplified, traditional)
);
CREATE INDEX index_hanzi_word_forms_traditional ON hanzi_word_forms(traditional);
CREATE TABLE vocabulary_entries (
    source TEXT NOT NULL,
    entryId TEXT NOT NULL,
    level TEXT NOT NULL,
    simplified TEXT NOT NULL,
    traditional TEXT NOT NULL,
    preferredForm TEXT NOT NULL,
    pinyin TEXT NOT NULL,
    partOfSpeech TEXT NOT NULL,
    sortOrder INTEGER NOT NULL,
    PRIMARY KEY(source, entryId)
);
CREATE INDEX index_vocabulary_entries_source_level ON vocabulary_entries(source, level);
CREATE INDEX index_vocabulary_entries_simplified ON vocabulary_entries(simplified);
CREATE INDEX index_vocabulary_entries_traditional ON vocabulary_entries(traditional);
CREATE TABLE study_list_bindings (
    source TEXT NOT NULL,
    level TEXT NOT NULL,
    listId INTEGER NOT NULL,
    PRIMARY KEY(source, level)
);
CREATE INDEX index_study_list_bindings_listId ON study_list_bindings(listId);
"""

METADATA_SCHEMA = """
CREATE TABLE hanzi_word_forms (
    simplified TEXT NOT NULL,
    traditional TEXT NOT NULL,
    PRIMARY KEY(simplified, traditional)
);
CREATE INDEX index_hanzi_word_forms_traditional ON hanzi_word_forms(traditional);
CREATE TABLE vocabulary_entries (
    source TEXT NOT NULL,
    entryId TEXT NOT NULL,
    level TEXT NOT NULL,
    simplified TEXT NOT NULL,
    traditional TEXT NOT NULL,
    preferredForm TEXT NOT NULL,
    pinyin TEXT NOT NULL,
    partOfSpeech TEXT NOT NULL,
    sortOrder INTEGER NOT NULL,
    PRIMARY KEY(source, entryId)
);
CREATE INDEX index_vocabulary_entries_source_level ON vocabulary_entries(source, level);
CREATE INDEX index_vocabulary_entries_simplified ON vocabulary_entries(simplified);
CREATE INDEX index_vocabulary_entries_traditional ON vocabulary_entries(traditional);
"""


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def require_file(assets: Path, name: str) -> Path:
    path = assets / name
    if not path.is_file() or path.stat().st_size == 0:
        raise FileNotFoundError(f"Required source file is missing or empty: {path}")
    return path


def json_records(payload: Any, dataset_name: str) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        rows = payload
    elif isinstance(payload, dict):
        rows = payload.get("data") or payload.get("rows") or payload.get("entries") or payload.get("words")
    else:
        rows = None
    if not isinstance(rows, list):
        raise ValueError(f"{dataset_name}: expected a top-level list or an object with a data array")
    return [row for row in rows if isinstance(row, dict)]


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, (int, float)):
        return str(value)
    return str(value).strip()


def create_database(path: Path, schema: str, user_version: int) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()
    connection = sqlite3.connect(path)
    connection.executescript(schema)
    connection.execute(f"PRAGMA user_version={int(user_version)}")
    return connection


def add_form(form_set: set[tuple[str, str]], simplified: str, traditional: str) -> None:
    simplified = clean_text(simplified)
    traditional = clean_text(traditional)
    if simplified and traditional:
        form_set.add((simplified, traditional))


def parse_cedict(path: Path, main: sqlite3.Connection) -> tuple[set[tuple[str, str]], dict[str, collections.Counter[str]]]:
    forms: set[tuple[str, str]] = set()
    traditional_counts: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    char_counts: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    rows: list[tuple[str, str, str, str, str, str, str]] = []

    with path.open("r", encoding="utf-8-sig", errors="replace") as handle:
        for line in handle:
            if not line.strip() or line.startswith("#"):
                continue
            match = CEDICT_PATTERN.match(line.rstrip("\r\n"))
            if not match:
                continue
            traditional, simplified, pinyin, definition = match.groups()
            add_form(forms, simplified, traditional)
            traditional_counts[simplified][traditional] += 1
            if len(simplified) == 1 and len(traditional) == 1:
                char_counts[simplified][traditional] += 1
            rows.append((simplified, pinyin, definition.replace("/", ", "), "", "", "", ""))
            if len(rows) >= 2000:
                main.executemany(
                    "INSERT OR REPLACE INTO hanzi_characters "
                    "(character,pinyin,definition,radical,decomposition,matchesJson,mmaDefinition) "
                    "VALUES (?,?,?,?,?,?,?)", rows
                )
                rows.clear()
    if rows:
        main.executemany(
            "INSERT OR REPLACE INTO hanzi_characters "
            "(character,pinyin,definition,radical,decomposition,matchesJson,mmaDefinition) "
            "VALUES (?,?,?,?,?,?,?)", rows
        )

    return forms, traditional_counts


def parse_makemeahanzi_dictionary(path: Path, main: sqlite3.Connection) -> int:
    count = 0
    with path.open("r", encoding="utf-8-sig", errors="replace") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
                character = clean_text(row.get("character"))
                if not character:
                    continue
                pinyin_items = row.get("pinyin") or []
                pinyin = " ".join(clean_text(item) for item in pinyin_items if clean_text(item))
                radical = clean_text(row.get("radical"))
                decomposition = clean_text(row.get("decomposition"))
                matches = json.dumps(row.get("matches", []), ensure_ascii=False, separators=(",", ":"))
                definition = clean_text(row.get("definition"))
                main.execute(
                    "INSERT OR IGNORE INTO hanzi_characters "
                    "(character,pinyin,definition,radical,decomposition,matchesJson,mmaDefinition) "
                    "VALUES (?,?,?,?,?,?,?)",
                    (character, pinyin, "", radical, decomposition, matches, definition)
                )
                main.execute(
                    "UPDATE hanzi_characters SET radical=?, decomposition=?, matchesJson=?, mmaDefinition=? "
                    "WHERE character=?",
                    (radical, decomposition, matches, definition, character)
                )
                count += 1
            except (ValueError, TypeError, json.JSONDecodeError):
                continue
    return count


def parse_graphics(path: Path, main: sqlite3.Connection) -> int:
    count = 0
    with path.open("r", encoding="utf-8-sig", errors="replace") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
                character = clean_text(row.get("character"))
                if not character:
                    continue
                main.execute(
                    "INSERT OR REPLACE INTO hanzi_graphics(character,rawJson) VALUES (?,?)",
                    (character, line)
                )
                count += 1
            except (ValueError, TypeError, json.JSONDecodeError):
                continue
    return count


def upsert_vocabulary_character(
    main: sqlite3.Connection,
    simplified: str,
    pinyin: str,
    definition: str
) -> None:
    """Keep existing CC-CEDICT/MMaH values, while filling missing word entries."""
    simplified = clean_text(simplified)
    if not simplified:
        return
    pinyin = clean_text(pinyin)
    definition = clean_text(definition)
    main.execute(
        "INSERT OR IGNORE INTO hanzi_characters "
        "(character,pinyin,definition,radical,decomposition,matchesJson,mmaDefinition) "
        "VALUES (?,?,?,?,?,?,?)",
        (simplified, pinyin, definition, "", "", "[]", "")
    )
    main.execute(
        "UPDATE hanzi_characters SET "
        "pinyin=CASE WHEN pinyin='' THEN ? ELSE pinyin END, "
        "definition=CASE WHEN definition='' THEN ? ELSE definition END "
        "WHERE character=?",
        (pinyin, definition, simplified)
    )


def traditional_for_simplified(
    simplified: str,
    exact_counts: dict[str, collections.Counter[str]],
    char_map: dict[str, str],
    forms_by_simplified: dict[str, set[str]]
) -> str:
    if not simplified:
        return ""
    exact = exact_counts.get(simplified)
    if exact:
        # Prefer a non-identical Traditional spelling when one is present; ties
        # remain deterministic and are based on CC-CEDICT occurrence counts.
        ordered = sorted(exact.items(), key=lambda item: (-item[1], item[0]))
        changed = next((traditional for traditional, _ in ordered if traditional != simplified), None)
        if changed:
            return changed

    # The indexed mapping avoids scanning the entire CEDICT/TOCFL pair set once
    # for every HSK record (an expensive O(entries * pairs) build-time operation).
    # It also catches cases where CEDICT has only an identity spelling but TOCFL
    # supplies the explicit Traditional counterpart.
    supplied_pairs = sorted(
        traditional for traditional in forms_by_simplified.get(simplified, set())
        if traditional != simplified
    )
    if supplied_pairs:
        return supplied_pairs[0]
    if exact:
        return sorted(exact.items(), key=lambda item: (-item[1], item[0]))[0][0]
    return "".join(char_map.get(char, char) for char in simplified)


def parse_hsk(
    path: Path,
    exact_counts: dict[str, collections.Counter[str]],
    char_map: dict[str, str],
    forms: set[tuple[str, str]],
    forms_by_simplified: dict[str, set[str]],
    main: sqlite3.Connection
) -> list[tuple[Any, ...]]:
    payload = read_json(path)
    records = json_records(payload, "HSK dataset")
    entries: list[tuple[Any, ...]] = []
    for index, row in enumerate(records):
        simplified = clean_text(row.get("hanzi") or row.get("word") or row.get("simplified"))
        if not simplified:
            continue
        raw_level = clean_text(row.get("level") or row.get("band"))
        if raw_level in {"7", "8", "9", "7-9", "7–9"}:
            level = "7-9"
        elif raw_level.isdigit() and 1 <= int(raw_level) <= 6:
            level = str(int(raw_level))
        else:
            raise ValueError(f"HSK dataset row {index + 1}: unexpected level {raw_level!r}")
        traditional = traditional_for_simplified(
            simplified, exact_counts, char_map, forms_by_simplified
        )
        add_form(forms, simplified, traditional)
        forms_by_simplified.setdefault(simplified, set()).add(traditional)
        pinyin = clean_text(row.get("pinyin"))
        meaning = clean_text(row.get("meaning") or row.get("definition"))
        upsert_vocabulary_character(main, simplified, pinyin, meaning)
        part_of_speech = clean_text(row.get("part_of_speech") or row.get("pos"))
        stable_id = clean_text(row.get("id")) or f"{raw_level}-{index + 1:05d}"
        entries.append((
            "HSK", stable_id, level, simplified, traditional, simplified,
            pinyin, part_of_speech, index
        ))
    if not entries:
        raise ValueError("HSK dataset contains no usable vocabulary entries")
    return entries


def _tocfl_level_from_row(row: dict[str, Any], row_number: int) -> str:
    """Accept the LinZi numeric bands and Ivan Kra's L0/L1-L5 source IDs."""
    raw_level = row.get("tocfl_level", row.get("level", row.get("TOCFL Level")))
    try:
        number = float(raw_level)
    except (TypeError, ValueError):
        band = clean_text(row.get("tocfl_band") or row.get("level_label")).lower()
        mapping = {
            "novice 1": "N1", "novice 2": "N2",
            "level 1": "1", "level 2": "2", "level 3": "3",
            "level 4": "4", "level 5": "5"
        }
        if band in mapping:
            return mapping[band]

        source_id = clean_text(row.get("ID") or row.get("id"))
        match = re.match(r"^L0-([12])\d+", source_id, flags=re.IGNORECASE)
        if match:
            return "N1" if match.group(1) == "1" else "N2"
        match = re.match(r"^L([1-5])-\d+", source_id, flags=re.IGNORECASE)
        if match:
            return match.group(1)
        raise ValueError(f"TOCFL dataset row {row_number}: unexpected level {raw_level!r} / ID {source_id!r}")

    if abs(number - 0.1) < 0.00001:
        return "N1"
    if abs(number - 0.2) < 0.00001:
        return "N2"
    if number.is_integer() and 1 <= int(number) <= 5:
        return str(int(number))
    raise ValueError(f"TOCFL dataset row {row_number}: unexpected level {raw_level!r}")


def _parse_tocfl_csv(path: Path) -> list[dict[str, Any]]:
    """Normalize the 2023 TOCFL CSV, including its explicit Variants JSON field."""
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {"ID", "Traditional", "Simplified", "Pinyin"}
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"TOCFL CSV is missing columns: {sorted(missing)}")
        for row_number, row in enumerate(reader, start=2):
            raw_variants = clean_text(row.get("Variants"))
            variants: list[dict[str, Any]] = []
            if raw_variants:
                try:
                    decoded = json.loads(raw_variants)
                    if isinstance(decoded, list):
                        variants = [item for item in decoded if isinstance(item, dict)]
                except json.JSONDecodeError as error:
                    raise ValueError(f"TOCFL CSV row {row_number}: invalid Variants JSON") from error

            if not variants:
                variants = [{
                    "Traditional": row.get("Traditional", ""),
                    "Simplified": row.get("Simplified", ""),
                    "Pinyin": row.get("Pinyin", "")
                }]

            base_id = clean_text(row.get("ID")) or f"tocfl-{row_number:05d}"
            level_row = {"ID": base_id}
            level = _tocfl_level_from_row(level_row, row_number)
            pos = clean_text(row.get("POS"))
            meaning = clean_text(row.get("Meaning") or row.get("Definitions"))
            for variant_index, variant in enumerate(variants):
                traditional = clean_text(variant.get("Traditional") or row.get("Traditional"))
                simplified = clean_text(variant.get("Simplified") or row.get("Simplified"))
                if not simplified and not traditional:
                    continue
                if not simplified:
                    simplified = traditional
                if not traditional:
                    traditional = simplified
                pinyin = clean_text(variant.get("Pinyin") or row.get("Pinyin"))
                records.append({
                    "id": f"{base_id}:v{variant_index + 1}",
                    "level": level,
                    "simplified": simplified,
                    "traditional": traditional,
                    "pinyin": pinyin,
                    "pos": pos,
                    "meaning": meaning
                })
    return records


def parse_tocfl(path: Path, forms: set[tuple[str, str]], main: sqlite3.Connection) -> list[tuple[Any, ...]]:
    if path.suffix.lower() == ".csv":
        records = _parse_tocfl_csv(path)
        normalized_rows = records
        csv_input = True
    else:
        payload = read_json(path)
        source_rows = json_records(payload, "TOCFL dataset")
        normalized_rows = []
        for row_number, row in enumerate(source_rows, start=1):
            level = _tocfl_level_from_row(row, row_number)
            simplified = clean_text(row.get("simp") or row.get("simplified") or row.get("Simplified"))
            traditional = clean_text(row.get("trad") or row.get("traditional") or row.get("Traditional"))
            if not simplified and not traditional:
                continue
            normalized_rows.append({
                "id": clean_text(row.get("id") or row.get("ID")) or f"tocfl-{row_number:05d}",
                "level": level,
                "simplified": simplified or traditional,
                "traditional": traditional or simplified,
                "pinyin": clean_text(row.get("pinyin") or row.get("Pinyin")),
                "pos": clean_text(row.get("pos") or row.get("POS")),
                "meaning": clean_text(row.get("definitions") or row.get("definition"))
            })
        csv_input = False

    entries: list[tuple[Any, ...]] = []
    for index, row in enumerate(normalized_rows):
        simplified = clean_text(row.get("simplified"))
        traditional = clean_text(row.get("traditional"))
        if not simplified and not traditional:
            continue
        simplified = simplified or traditional
        traditional = traditional or simplified
        add_form(forms, simplified, traditional)
        pinyin = clean_text(row.get("pinyin"))
        definitions = clean_text(row.get("meaning"))
        upsert_vocabulary_character(main, simplified, pinyin, definitions)
        stable_id = clean_text(row.get("id")) or f"tocfl-{index + 1:05d}"
        entries.append((
            "TOCFL", stable_id, clean_text(row.get("level")), simplified,
            traditional, traditional, pinyin, clean_text(row.get("pos")), index
        ))

    if not entries:
        raise ValueError("TOCFL dataset contains no usable vocabulary entries")
    if csv_input:
        levels = {row[2] for row in entries}
        expected = {"N1", "N2", "1", "2", "3", "4", "5"}
        if not expected.issubset(levels):
            raise ValueError(f"TOCFL CSV is missing one or more of the seven level bands: {sorted(levels)}")
    return entries


def add_traditional_graphics_aliases(main: sqlite3.Connection, forms: set[tuple[str, str]]) -> int:
    """Create minimal character rows so PracticeViewModel can animate trad glyphs.

    PracticeViewModel requires both a HanziCharacter row and a graphics row. The
    source dictionary is Simplified-first, so traditional glyphs with real
    MakeMeAHanzi graphics receive a minimal alias row. We intentionally leave IDS
    component data empty rather than pretending the simplified decomposition is
    exact for the traditional form.
    """
    aliases: dict[str, tuple[str, str]] = {}
    for simplified, traditional in sorted(forms):
        if len(simplified) != 1 or len(traditional) != 1 or simplified == traditional:
            continue
        has_graphics = main.execute(
            "SELECT 1 FROM hanzi_graphics WHERE character=? LIMIT 1", (traditional,)
        ).fetchone()
        if not has_graphics:
            continue
        source_row = main.execute(
            "SELECT pinyin, definition FROM hanzi_characters WHERE character=? LIMIT 1",
            (simplified,)
        ).fetchone()
        if source_row and traditional not in aliases:
            aliases[traditional] = (clean_text(source_row[0]), clean_text(source_row[1]))

    rows = [
        (character, pinyin, definition, "", "", "[]", "")
        for character, (pinyin, definition) in aliases.items()
    ]
    main.executemany(
        "INSERT OR IGNORE INTO hanzi_characters "
        "(character,pinyin,definition,radical,decomposition,matchesJson,mmaDefinition) "
        "VALUES (?,?,?,?,?,?,?)",
        rows
    )
    return len(rows)


def insert_metadata(forms: set[tuple[str, str]], entries: list[tuple[Any, ...]], *connections: sqlite3.Connection) -> None:
    ordered_forms = sorted(forms)
    form_rows = [(simplified, traditional) for simplified, traditional in ordered_forms]
    for connection in connections:
        connection.executemany(
            "INSERT OR IGNORE INTO hanzi_word_forms(simplified,traditional) VALUES (?,?)",
            form_rows
        )
        connection.executemany(
            "INSERT OR IGNORE INTO vocabulary_entries "
            "(source,entryId,level,simplified,traditional,preferredForm,pinyin,partOfSpeech,sortOrder) "
            "VALUES (?,?,?,?,?,?,?,?,?)",
            entries
        )


def check_db(path: Path, expected_version: int, required_tables: set[str]) -> dict[str, int]:
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Database is missing or empty: {path}")
    connection = sqlite3.connect(path)
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise ValueError(f"Database integrity check failed for {path}: {integrity}")
        version = connection.execute("PRAGMA user_version").fetchone()[0]
        if version != expected_version:
            raise ValueError(f"Unexpected SQLite user_version in {path}: {version} (expected {expected_version})")
        tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        missing = required_tables - tables
        if missing:
            raise ValueError(f"Missing tables in {path}: {sorted(missing)}")
        counts = {}
        for table in sorted(required_tables):
            counts[table] = connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        return counts
    finally:
        connection.close()


def build(assets: Path, main_path: Path, metadata_path: Path) -> None:
    cedict = require_file(assets, "cedict_ts.u8")
    dictionary = require_file(assets, "dictionary.txt")
    graphics = require_file(assets, "graphics.txt")
    hsk_path = require_file(assets, "hsk-3.0-words-all.json")
    tocfl_json = assets / "tocfl-words.json"
    tocfl_csv = assets / "tocfl-202307.csv"
    if tocfl_json.is_file() and tocfl_json.stat().st_size > 0:
        tocfl_path = tocfl_json
    elif tocfl_csv.is_file() and tocfl_csv.stat().st_size > 0:
        tocfl_path = tocfl_csv
    else:
        raise FileNotFoundError(
            f"Missing TOCFL source: expected {tocfl_json} or {tocfl_csv}"
        )

    main = create_database(main_path, MAIN_SCHEMA, ROOM_DATABASE_VERSION)
    metadata = create_database(metadata_path, METADATA_SCHEMA, METADATA_DATABASE_VERSION)
    try:
        forms, exact_counts = parse_cedict(cedict, main)
        character_counts: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
        for simple, trad in forms:
            if len(simple) == 1 and len(trad) == 1:
                character_counts[simple][trad] += 1
        char_map = {
            simple: sorted(counts.items(), key=lambda item: (-item[1], item[0]))[0][0]
            for simple, counts in character_counts.items() if counts
        }

        dictionary_count = parse_makemeahanzi_dictionary(dictionary, main)
        graphics_count = parse_graphics(graphics, main)

        # TOCFL already supplies explicit simp/trad pairs. Parse it first so
        # HSK terms can reuse those exact pairings before character-wise fallback.
        tocfl_entries = parse_tocfl(tocfl_path, forms, main)
        forms_by_simplified: dict[str, set[str]] = collections.defaultdict(set)
        for simple, traditional in forms:
            forms_by_simplified[simple].add(traditional)
        hsk_entries = parse_hsk(
            hsk_path, exact_counts, char_map, forms, forms_by_simplified, main
        )
        all_entries = hsk_entries + tocfl_entries
        traditional_aliases = add_traditional_graphics_aliases(main, forms)

        # Fresh installs receive the complete dataset inside the prebuilt Room DB.
        # Keep an identical companion metadata DB only for in-place upgrades from
        # older installed databases, which cannot be replaced by createFromAsset.
        insert_metadata(forms, all_entries, main, metadata)
        main.commit()
        metadata.commit()

        print(f"CEDICT form pairs: {len(forms):,}")
        print(f"MakeMeAHanzi dictionary rows processed: {dictionary_count:,}")
        print(f"Graphics rows processed: {graphics_count:,}")
        print(f"HSK 2025-syllabus entries: {len(hsk_entries):,}")
        print(f"TOCFL entries: {len(tocfl_entries):,}")
        print(f"Traditional animation aliases with source graphics: {traditional_aliases:,}")
    finally:
        main.close()
        metadata.close()

    validate(main_path, metadata_path)


def validate(main_path: Path, metadata_path: Path) -> None:
    main_counts = check_db(
        main_path,
        ROOM_DATABASE_VERSION,
        {
            "hanzi_characters", "hanzi_graphics", "practice_lists", "list_items",
            "hanzi_word_forms", "vocabulary_entries", "study_list_bindings"
        }
    )
    metadata_counts = check_db(
        metadata_path,
        METADATA_DATABASE_VERSION,
        {"hanzi_word_forms", "vocabulary_entries"}
    )
    if main_counts["hanzi_characters"] <= 0 or main_counts["hanzi_graphics"] <= 0:
        raise ValueError("The main Room database has no dictionary or stroke-graphics records")
    if main_counts["hanzi_word_forms"] <= 0 or main_counts["vocabulary_entries"] <= 0:
        raise ValueError("The main Room database is missing prebuilt script/vocabulary metadata")
    if metadata_counts["hanzi_word_forms"] <= 0:
        raise ValueError("The vocabulary metadata database has no simplified/traditional pairs")
    if metadata_counts["vocabulary_entries"] <= 0:
        raise ValueError("The vocabulary metadata database has no HSK/TOCFL entries")
    print(f"Main DB: {main_path.stat().st_size / (1024 * 1024):.2f} MiB; counts={main_counts}")
    print(f"Metadata DB: {metadata_path.stat().st_size / 1024:.1f} KiB; counts={metadata_counts}")


def main_cli() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets-dir", type=Path, default=Path("app/src/main/assets"))
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/hanzi_database_prebuilt.db"))
    parser.add_argument("--metadata-output", type=Path, default=Path("app/src/main/assets/hanzi_vocabulary_metadata.db"))
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()

    try:
        if args.validate_only:
            validate(args.output, args.metadata_output)
        else:
            build(args.assets_dir, args.output, args.metadata_output)
        return 0
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main_cli())
