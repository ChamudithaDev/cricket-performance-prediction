"""
preprocess_cricsheet_matches.py

Preprocesses raw Cricsheet ball-by-ball JSON match files into a clean, match-level
dataset (cricsheet_matches.csv) optimized for the Toss Decision Advisor.

Features:
- Handles all international formats (ODI, T20I, Test) and domestic formats.
- Extracts match metadata, venue, toss winner, toss decision, innings order, winner.
- Resolves Super Overs (eliminators), bowl-outs, DLS methods, and awarded matches.
- Preserves ties, draws, and no-results with `is_decisive = False` to prevent skewing
  decisive-match win-rate denominators.
- Flags incomplete or ambiguous matches (e.g. single-inning rain abandonments).
- Deduplicates on match_id across directories.
- Provides a comprehensive summary of processed files, excluded records, duplicates,
  and data quality flags.
"""

import os
import sys
import json
import argparse
import datetime
from pathlib import Path
from collections import Counter, defaultdict
import pandas as pd

FULL_MEMBERS = {
    "Afghanistan", "Australia", "Bangladesh", "England", "India",
    "Ireland", "New Zealand", "Pakistan", "South Africa", "Sri Lanka",
    "West Indies", "Zimbabwe",
}

DEFAULT_INPUT_DIR = Path(r"C:\Users\Chamuditha\Desktop\preprocess\cricsheet_data")
DEFAULT_OUTPUT_FILE = Path(__file__).resolve().parent / "cricsheet_matches.csv"


def is_full_member(teams):
    """Return True if both teams belong to ICC Full Members."""
    return len(teams) >= 2 and all(t in FULL_MEMBERS for t in teams[:2])


def extract_match_record(file_path: Path, match_id: str, format_hint: str):
    """
    Parses a single Cricsheet match JSON file and extracts a normalized record.
    Returns (record_dict, status_str, quality_flag).
    """
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return None, f"corrupt_file: {e}", "corrupt_file"

    info = data.get("info", {})
    if not info:
        return None, "missing_info_block", "invalid_record"

    dates = info.get("dates", [])
    if not dates:
        return None, "missing_dates", "invalid_record"
    match_date = str(dates[0])
    match_year = int(match_date.split("-")[0]) if "-" in match_date else None

    teams = info.get("teams", [])
    if len(teams) < 2:
        return None, "less_than_two_teams", "invalid_record"
    team1, team2 = teams[0], teams[1]
    is_fm = is_full_member(teams)

    # Format
    raw_match_type = info.get("match_type", "")
    team_type = info.get("team_type", "international")
    if format_hint in ("odi", "t20i", "test"):
        fmt = format_hint
    else:
        if raw_match_type == "ODI":
            fmt = "odi"
        elif raw_match_type == "T20":
            fmt = "t20i" if team_type == "international" else "t20_domestic"
        elif raw_match_type == "Test":
            fmt = "test"
        else:
            fmt = raw_match_type.lower()

    venue = info.get("venue", "").strip()
    city = info.get("city", "")
    if city:
        city = city.strip()
    else:
        city = ""

    # Toss
    toss = info.get("toss")
    if not toss or "winner" not in toss or "decision" not in toss:
        return None, "missing_toss_data", "invalid_record"
    toss_winner = toss.get("winner", "").strip()
    toss_decision = toss.get("decision", "").strip().lower()
    toss_winner_choice = f"{toss_winner}:{toss_decision}"

    # Innings analysis
    innings = data.get("innings", [])
    innings_count = len(innings)
    super_over_occurred = any(inn.get("super_over") for inn in innings)

    # Determine first innings and second innings teams
    if innings_count > 0:
        first_innings_team = innings[0].get("team", "").strip()
        second_innings_team = team2 if first_innings_team == team1 else team1
    else:
        # Fallback to toss decision if no innings were bowled
        first_innings_team = toss_winner if toss_decision == "bat" else (team2 if toss_winner == team1 else team1)
        second_innings_team = team2 if first_innings_team == team1 else team1

    # Outcome analysis
    outcome = info.get("outcome", {})
    method = outcome.get("method", "").strip()
    raw_result = outcome.get("result", "").strip().lower()
    winner = outcome.get("winner")
    eliminator = outcome.get("eliminator")
    bowl_out = outcome.get("bowl_out")
    by_dict = outcome.get("by", {})

    win_by_type = "none"
    win_by_margin = 0.0

    if by_dict:
        if "runs" in by_dict:
            win_by_type = "runs"
            win_by_margin = float(by_dict["runs"])
        elif "wickets" in by_dict:
            win_by_type = "wickets"
            win_by_margin = float(by_dict["wickets"])
        elif "innings" in by_dict:
            win_by_type = "innings"
            win_by_margin = float(by_dict.get("runs", 0))

    # Resolve result_type, winner, and decisive flags
    quality_flag = "clean"
    is_decisive = False
    effective_winner = None

    if eliminator:
        result_type = "tie_super_over"
        effective_winner = eliminator.strip()
        win_by_type = "eliminator"
        is_decisive = True
        quality_flag = "super_over_finish"
    elif bowl_out:
        result_type = "tie_bowl_out"
        effective_winner = bowl_out.strip()
        win_by_type = "bowl_out"
        is_decisive = True
        quality_flag = "bowl_out_finish"
    elif winner:
        effective_winner = winner.strip()
        if method == "Awarded":
            result_type = "awarded"
            win_by_type = "awarded"
            is_decisive = True
            quality_flag = "awarded_match"
        elif method in ("D/L", "DLS"):
            result_type = "dls"
            is_decisive = True
            quality_flag = "rain_rule_dls"
        else:
            result_type = "normal"
            is_decisive = True
            quality_flag = "clean"
    elif raw_result == "tie":
        result_type = "tie"
        effective_winner = None
        is_decisive = False
        quality_flag = "tie_unresolved"
    elif raw_result == "draw":
        result_type = "draw"
        effective_winner = None
        is_decisive = False
        quality_flag = "draw"
    elif raw_result == "no result":
        result_type = "no_result"
        effective_winner = None
        is_decisive = False
        quality_flag = "no_result"
    else:
        # Fallback for unexpected or incomplete outcome
        result_type = raw_result if raw_result else "incomplete"
        effective_winner = None
        is_decisive = False
        quality_flag = "ambiguous_outcome"

    # Flag incomplete single-inning matches
    if innings_count == 1 and quality_flag not in ("awarded_match", "clean"):
        quality_flag = "incomplete_single_inning"

    # Bat first won / Chase won
    if is_decisive and effective_winner:
        if effective_winner == first_innings_team:
            bat_first_won = 1
            chase_won = 0
        elif effective_winner == second_innings_team:
            bat_first_won = 0
            chase_won = 1
        else:
            # Winner name mismatch with teams
            bat_first_won = 0
            chase_won = 0
            quality_flag = "winner_name_mismatch"
    else:
        bat_first_won = 0
        chase_won = 0

    record = {
        "match_id": str(match_id),
        "date": match_date,
        "year": match_year,
        "format": fmt,
        "match_type": raw_match_type,
        "venue": venue,
        "city": city,
        "team1": team1,
        "team2": team2,
        "is_full_member_match": is_fm,
        "toss_winner": toss_winner,
        "toss_decision": toss_decision,
        "toss_winner_choice": toss_winner_choice,
        "first_innings_team": first_innings_team,
        "second_innings_team": second_innings_team,
        "winner": effective_winner if effective_winner else "",
        "result_type": result_type,
        "win_by_type": win_by_type,
        "win_by_margin": win_by_margin,
        "method": method,
        "is_super_over": super_over_occurred,
        "innings_count": innings_count,
        "bat_first_won": bat_first_won,
        "chase_won": chase_won,
        "is_decisive": is_decisive,
        "data_quality_flag": quality_flag,
    }

    return record, "valid", quality_flag


def process_cricsheet_data(input_dir: Path, output_file: Path, years: int = None, full_members_only: bool = False):
    """
    Iterates through Cricsheet format subdirectories, parses JSON files,
    applies optional date/member filters, deduplicates, and saves cricsheet_matches.csv.
    """
    print(f"Starting Cricsheet preprocessing from: {input_dir}")
    print(f"Target output file: {output_file}")
    if years:
        cutoff_date = (datetime.date.today() - datetime.timedelta(days=years * 365)).isoformat()
        print(f"Filtering for matches on or after: {cutoff_date} ({years} years)")
    else:
        cutoff_date = None
        print("Processing all historical records (no year cutoff applied).")

    if full_members_only:
        print("Filtering for ICC Full Member matches only.")

    seen_match_ids = set()
    records = []
    
    stats = {
        "total_files_scanned": 0,
        "duplicates_skipped": 0,
        "invalid_records_skipped": 0,
        "date_filtered_skipped": 0,
        "full_member_filtered_skipped": 0,
        "valid_records_written": 0,
        "decisive_matches": 0,
        "non_decisive_matches": 0,
    }
    format_counts = Counter()
    quality_flag_counts = Counter()
    result_type_counts = Counter()

    # Discover format folders
    format_folders = []
    if (input_dir / "odi").is_dir():
        format_folders.append(("odi", input_dir / "odi"))
    if (input_dir / "t20i").is_dir():
        format_folders.append(("t20i", input_dir / "t20i"))
    if (input_dir / "test").is_dir():
        format_folders.append(("test", input_dir / "test"))

    # Also handle if flat directory of JSONs
    if not format_folders:
        format_folders = [("unknown", input_dir)]

    for fmt_hint, folder in format_folders:
        json_files = sorted(folder.glob("*.json"))
        print(f"Scanning format [{fmt_hint}]: {len(json_files)} files in {folder.name}/")

        for file_path in json_files:
            stats["total_files_scanned"] += 1
            match_id = file_path.stem

            if match_id in seen_match_ids:
                stats["duplicates_skipped"] += 1
                continue

            record, status, quality_flag = extract_match_record(file_path, match_id, fmt_hint)
            if not record:
                stats["invalid_records_skipped"] += 1
                continue

            # Date filter
            if cutoff_date and record["date"] < cutoff_date:
                stats["date_filtered_skipped"] += 1
                continue

            # Full Member filter
            if full_members_only and not record["is_full_member_match"]:
                stats["full_member_filtered_skipped"] += 1
                continue

            seen_match_ids.add(match_id)
            records.append(record)

            stats["valid_records_written"] += 1
            if record["is_decisive"]:
                stats["decisive_matches"] += 1
            else:
                stats["non_decisive_matches"] += 1

            format_counts[record["format"]] += 1
            quality_flag_counts[quality_flag] += 1
            result_type_counts[record["result_type"]] += 1

    # Convert to DataFrame and sort chronologically
    df = pd.DataFrame(records)
    if not df.empty:
        df = df.sort_values(by=["date", "match_id"], ascending=[True, True]).reset_index(drop=True)

    output_file.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(output_file, index=False)
    print(f"\nSuccessfully wrote {len(df)} records to: {output_file}")

    print("\n" + "=" * 50)
    print("CRICSHEET PREPROCESSING SUMMARY")
    print("=" * 50)
    print(f"Total files scanned          : {stats['total_files_scanned']}")
    print(f"Duplicate files skipped      : {stats['duplicates_skipped']}")
    print(f"Invalid / corrupt skipped    : {stats['invalid_records_skipped']}")
    if cutoff_date:
        print(f"Date-filtered skipped (<{cutoff_date}): {stats['date_filtered_skipped']}")
    if full_members_only:
        print(f"Non-Full-Member skipped      : {stats['full_member_filtered_skipped']}")
    print(f"Final output match rows      : {stats['valid_records_written']}")
    print(f"  • Decisive matches (winner): {stats['decisive_matches']} ({stats['decisive_matches']/max(1, stats['valid_records_written']):.1%})")
    print(f"  • Non-decisive (tie/draw/NR): {stats['non_decisive_matches']} ({stats['non_decisive_matches']/max(1, stats['valid_records_written']):.1%})")

    print("\nFormat Breakdown:")
    for fmt, cnt in format_counts.most_common():
        print(f"  {fmt.upper():<10}: {cnt:>5} matches")

    print("\nResult Type Breakdown:")
    for res, cnt in result_type_counts.most_common():
        print(f"  {res:<16}: {cnt:>5} matches")

    print("\nData Quality Flags:")
    for flag, cnt in quality_flag_counts.most_common():
        print(f"  {flag:<26}: {cnt:>5} matches")
    print("=" * 50)

    return df, stats


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Preprocess Cricsheet JSON files into cricsheet_matches.csv")
    parser.add_argument("--input-dir", type=Path, default=DEFAULT_INPUT_DIR, help="Path to cricsheet_data directory")
    parser.add_argument("--output-file", type=Path, default=DEFAULT_OUTPUT_FILE, help="Path to output CSV file")
    parser.add_argument("--years", type=int, default=None, help="Optional cutoff in years from today")
    parser.add_argument("--full-members-only", action="store_true", help="Include only ICC Full Member nations")

    args = parser.parse_args()
    process_cricsheet_data(
        input_dir=args.input_dir,
        output_file=args.output_file,
        years=args.years,
        full_members_only=args.full_members_only
    )
