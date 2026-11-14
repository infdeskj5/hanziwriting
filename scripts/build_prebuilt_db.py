#!/usr/bin/env python3
"""Build the pre-populated SQLite database used by HanziWriting's Room layer.

Only Python's standard library is required. It mirrors AssetParser.kt:
- CEDICT rows are inserted/replaced by the simplified word key.
- MakeMeAHanzi dictionary data inserts missing characters, then updates radicals,
  decomposition, matchesJson and mmaDefinition without replacing CEDICT definitions.
- Graphics rows retain their original JSON line.
"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
from pathlib import Path
from typing import Any, Iterable

ROOM_DB_VERSION = 2
CEDICT_PATTERN = re.compile(r"^(.+?)\s+(.+?)\s+\[(.+?)\]\s+/(.+)/$")

SCHEMA = """
CREATE TABLE IF NOT EXISTS `hanzi_characters` (
    `character` TEXT NOT NULL,
    `pinyin` TEXT NOT NULL,
    `definition` TEXT NOT NULL,
    `radical` TEXT NOT NULL,
    `decomposition` TEXT NOT NULL,
    `matchesJson` TEXT NOT NULL,
    `mmaDefinition` TEXT NOT NULL,
    PRIMARY KEY(`character`)
);

CREATE TABLE IF NOT EXISTS `hanzi_graphics` (
    `character` TEXT NOT NULL,
    `rawJson` TEXT NOT NULL,
    PRIMARY KEY(`character`)
);

CREATE TABLE IF NOT EXISTS `practice_lists` (
    `listId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `name` TEXT NOT NULL,
    `createdAt` INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS `list_items` (
    `listId` INTEGER NOT NULL,
    `character` TEXT NOT NULL,
    PRIMARY KEY(`listId`, `character`)
);
"""


def as_gson_string(value: Any, default: str = "") -> str:
    """Approximate Gson JsonElement.asString for primitive JSON values."""
    if value is None:
        return default
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (str, int, float)):
        return str(value)
    return default


def flush_rows(connection: sqlite3.Connection, sql: str, rows: list[tuple], batch_size: int = 2000) -> None:
    if rows:
        connection.executemany(sql, rows)
        rows.clear()


def parse_cedict(connection: sqlite3.Connection, source: Path) -> int:
    insert_sql = """
        INSERT OR REPLACE INTO hanzi_characters
        (character, pinyin, definition, radical, decomposition, matchesJson, mmaDefinition)
        VALUES (?, ?, ?, '', '', '', '')
    """
    batch: list[tuple] = []
    inserted = 0

    with source.open("r", encoding="utf-8", errors="replace", newline="") as handle:
        for line in handle:
            line = line.rstrip("\r\n")
            if not line or line.startswith("#"):
                continue
            match = CEDICT_PATTERN.match(line)
            if not match:
                continue
            _traditional, simplified, pinyin, definition = match.groups()
            batch.append((simplified, pinyin, definition.replace("/", ", ")))
            inserted += 1
            if len(batch) >= 2000:
                connection.executemany(insert_sql, batch)
                batch.clear()

    if batch:
        connection.executemany(insert_sql, batch)
    return inserted


def parse_dictionary(connection: sqlite3.Connection, source: Path) -> int:
    insert_sql = """
        INSERT OR IGNORE INTO hanzi_characters
        (character, pinyin, definition, radical, decomposition, matchesJson, mmaDefinition)
        VALUES (?, ?, '', '', '', '', '')
    """
    update_sql = """
        UPDATE hanzi_characters
        SET radical = ?, decomposition = ?, matchesJson = ?, mmaDefinition = ?
        WHERE character = ?
    """
    insert_batch: list[tuple] = []
    update_batch: list[tuple] = []
    processed = 0

    with source.open("r", encoding="utf-8", errors="replace", newline="") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
                if not isinstance(entry, dict):
                    continue
                character = as_gson_string(entry.get("character"))
                if not character:
                    continue

                pinyin_values = entry.get("pinyin")
                if isinstance(pinyin_values, list):
                    pinyin = " ".join(as_gson_string(item) for item in pinyin_values)
                else:
                    pinyin = ""

                matches = (
                    json.dumps(entry["matches"], ensure_ascii=False, separators=(",", ":"))
                    if "matches" in entry
                    else ""
                )

                radical = as_gson_string(entry.get("radical"))
                decomposition = as_gson_string(entry.get("decomposition"))
                mma_definition = as_gson_string(entry.get("definition"))

                insert_batch.append((character, pinyin))
                update_batch.append((radical, decomposition, matches, mma_definition, character))
                processed += 1

                if len(insert_batch) >= 2000:
                    connection.executemany(insert_sql, insert_batch)
                    connection.executemany(update_sql, update_batch)
                    insert_batch.clear()
                    update_batch.clear()
            except (json.JSONDecodeError, TypeError, ValueError):
                continue

    if insert_batch:
        connection.executemany(insert_sql, insert_batch)
        connection.executemany(update_sql, update_batch)
    return processed


def parse_graphics(connection: sqlite3.Connection, source: Path) -> int:
    insert_sql = """
        INSERT OR REPLACE INTO hanzi_graphics (character, rawJson)
        VALUES (?, ?)
    """
    batch: list[tuple] = []
    processed = 0

    with source.open("r", encoding="utf-8", errors="replace", newline="") as handle:
        for line in handle:
            raw_line = line.rstrip("\r\n")
            if not raw_line.strip():
                continue
            try:
                entry = json.loads(raw_line)
                if not isinstance(entry, dict):
                    continue
                character = as_gson_string(entry.get("character"))
                if not character:
                    continue

                # Match AssetParser exactly: keep the source JSON line verbatim.
                batch.append((character, raw_line))
                processed += 1
                if len(batch) >= 2000:
                    connection.executemany(insert_sql, batch)
                    batch.clear()
            except (json.JSONDecodeError, TypeError, ValueError):
                continue

    if batch:
        connection.executemany(insert_sql, batch)
    return processed


def validate_database(database_path: Path) -> tuple[int, int, int]:
    if not database_path.is_file() or database_path.stat().st_size == 0:
        raise RuntimeError(f"Prebuilt database does not exist or is empty: {database_path}")

    connection = sqlite3.connect(f"file:{database_path}?mode=ro", uri=True)
    try:
        version = connection.execute("PRAGMA user_version").fetchone()[0]
        if version != ROOM_DB_VERSION:
            raise RuntimeError(f"Expected Room DB version {ROOM_DB_VERSION}, found {version}")

        expected_tables = {"hanzi_characters", "hanzi_graphics", "practice_lists", "list_items"}
        actual_tables = {
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            ).fetchall()
        }
        missing = expected_tables - actual_tables
        if missing:
            raise RuntimeError(f"Missing Room table(s): {', '.join(sorted(missing))}")

        characters = connection.execute("SELECT COUNT(*) FROM hanzi_characters").fetchone()[0]
        graphics = connection.execute("SELECT COUNT(*) FROM hanzi_graphics").fetchone()[0]
        lists = connection.execute("SELECT COUNT(*) FROM practice_lists").fetchone()[0]
        items = connection.execute("SELECT COUNT(*) FROM list_items").fetchone()[0]
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]

        if characters <= 0 or graphics <= 0:
            raise RuntimeError(
                f"Database is incomplete: {characters} characters, {graphics} graphics"
            )
        if lists != 0 or items != 0:
            raise RuntimeError("A fresh prebuilt database must not contain user practice lists.")
        if integrity != "ok":
            raise RuntimeError(f"SQLite integrity_check failed: {integrity}")

        return characters, graphics, version
    finally:
        connection.close()


def build_database(assets_dir: Path, output: Path) -> None:
    cedict = assets_dir / "cedict_ts.u8"
    dictionary = assets_dir / "dictionary.txt"
    graphics = assets_dir / "graphics.txt"

    for source in (cedict, dictionary, graphics):
        if not source.is_file() or source.stat().st_size == 0:
            raise FileNotFoundError(f"Required input asset is missing or empty: {source}")

    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()

    connection = sqlite3.connect(output)
    try:
        connection.execute("PRAGMA journal_mode=DELETE")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute("PRAGMA temp_store=MEMORY")
        connection.execute("PRAGMA foreign_keys=ON")
        connection.executescript(SCHEMA)

        print("Importing CC-CEDICT…", flush=True)
        cedict_rows = parse_cedict(connection, cedict)
        connection.commit()
        print(f"  Parsed {cedict_rows:,} CEDICT rows", flush=True)

        print("Importing MakeMeAHanzi radicals and components…", flush=True)
        dictionary_rows = parse_dictionary(connection, dictionary)
        connection.commit()
        print(f"  Processed {dictionary_rows:,} dictionary records", flush=True)

        print("Importing original stroke graphics…", flush=True)
        graphics_rows = parse_graphics(connection, graphics)
        connection.commit()
        print(f"  Processed {graphics_rows:,} graphics records", flush=True)

        # Match @Database(version = 2). Room will create and populate
        # room_master_table itself after validating this prepackaged schema.
        connection.execute(f"PRAGMA user_version={ROOM_DB_VERSION}")
        connection.commit()
    finally:
        connection.close()

    characters, graphics_count, version = validate_database(output)
    print(
        f"Prebuilt Room database ready: {characters:,} characters, "
        f"{graphics_count:,} graphics, schema version {version}. "
        f"File size: {output.stat().st_size / (1024 * 1024):.1f} MiB",
        flush=True,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()

    try:
        if args.validate_only:
            characters, graphics, version = validate_database(args.output)
            print(
                f"Validated prebuilt DB: {characters:,} characters, "
                f"{graphics:,} graphics, Room version {version}"
            )
        else:
            build_database(args.assets_dir, args.output)
        return 0
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
