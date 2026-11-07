"""
preprocess_player_strengths.py

Preprocesses raw Cricsheet ball-by-ball JSON match files into clean, pre-aggregated
CSV files for Player Strengths & Weaknesses (head-to-head matchups and match-phase performance).

Preservation rules:
- Does NOT modify existing ML models or Flask prediction endpoints.
- Does NOT overwrite existing batting_performance.csv, bowling_performance.csv, or cricsheet_matches.csv.
- Does NOT include Super Over deliveries in regular match statistics.
- Excludes Test matches from limited-overs phase breakdowns.
- Computes batting and bowling metrics according to strict international cricket scoring standards.

Outputs:
1. player_bowler_matchups.csv  (Batter vs Bowler head-to-head aggregates)
2. bowler_batter_matchups.csv  (Bowler vs Batter head-to-head aggregates)
3. player_phase_performance.csv (Player batting & bowling by match phase: Powerplay, Middle, Death)
"""

import os
import sys
import json
import argparse
from pathlib import Path
from collections import defaultdict
import csv
from datetime import datetime

DEFAULT_DATA_DIR = Path(r"C:\Users\Chamuditha\Desktop\preprocess\cricsheet_data")
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parent

BOWLER_CREDITED_DISMISSALS = {
    'bowled', 'caught', 'caught and bowled', 'lbw', 'stumped', 'hit wicket'
}

def get_match_phase(format_str, over_idx):
    """
    Returns the match phase name for ODI or T20I based on 0-indexed over number.
    Returns None for Test matches.
    """
    fmt = format_str.lower()
    if fmt in ('t20i', 't20'):
        if 0 <= over_idx <= 5:
            return 'POWERPLAY'
        elif 6 <= over_idx <= 14:
            return 'MIDDLE'
        elif 15 <= over_idx <= 19:
            return 'DEATH'
        else:
            return None
    elif fmt == 'odi':
        if 0 <= over_idx <= 9:
            return 'POWERPLAY'
        elif 10 <= over_idx <= 39:
            return 'MIDDLE'
        elif 40 <= over_idx <= 49:
            return 'DEATH'
        else:
            return None
    return None


def run_preprocessing(data_dir=DEFAULT_DATA_DIR, output_dir=DEFAULT_OUTPUT_DIR):
    print("=" * 80)
    print("CRICKETAI — PREPROCESSING PLAYER STRENGTHS & WEAKNESSES")
    print(f"Source JSON Directory: {data_dir}")
    print(f"Output CSV Directory:  {output_dir}")
    print("=" * 80)

    if not data_dir.exists():
        raise FileNotFoundError(f"Source Cricsheet directory not found: {data_dir}")

    output_dir.mkdir(parents=True, exist_ok=True)

    # ── Matchup Accumulator ──────────────────────────────────────────────────
    # Key: (batter, bowler, format)
    # Value: dict of accumulators
    matchup_data = defaultdict(lambda: {
        'matches': set(),
        'innings': set(),
        'balls_faced': 0,
        'legal_balls': 0,
        'total_balls': 0,
        'runs_scored': 0,
        'runs_conceded': 0,
        'fours': 0,
        'sixes': 0,
        'dismissals': 0,
        'dot_balls': 0,
        'dates': []
    })

    # ── Phase Accumulator ───────────────────────────────────────────────────
    # Key: (player, discipline, format, phase)
    # Value: dict of phase metrics
    phase_data = defaultdict(lambda: {
        'matches': set(),
        'innings': set(),
        'balls': 0,
        'runs': 0,
        'fours': 0,
        'sixes': 0,
        'wickets_or_dismissals': 0,
        'dot_balls': 0,
        'total_deliveries': 0
    })

    total_files_read = 0
    total_deliveries_processed = 0
    super_over_deliveries_skipped = 0
    error_files = []

    formats = [("odi", "ODI"), ("t20i", "T20I"), ("test", "TEST")]

    start_time = datetime.now()

    for folder_name, canonical_fmt in formats:
        folder_path = data_dir / folder_name
        if not folder_path.exists():
            print(f"Warning: Folder {folder_path} not found. Skipping.")
            continue

        files = sorted(list(folder_path.glob("*.json")))
        print(f"\nProcessing {canonical_fmt}: {len(files)} files...")

        for f in files:
            total_files_read += 1
            match_id = f.stem

            try:
                with open(f, 'r', encoding='utf-8') as fp:
                    m = json.load(fp)
            except Exception as ex:
                error_files.append((str(f), str(ex)))
                continue

            info = m.get('info', {})
            dates = info.get('dates', [])
            m_date = str(dates[0]) if dates else ""

            innings_list = m.get('innings', [])
            for inn_idx, inn in enumerate(innings_list):
                is_so = bool(inn.get('super_over', False))
                inn_id = f"{match_id}_{inn_idx}"

                for over_obj in inn.get('overs', []):
                    over_idx = over_obj.get('over', 0)
                    phase = get_match_phase(canonical_fmt, over_idx)

                    for d in over_obj.get('deliveries', []):
                        if is_so:
                            super_over_deliveries_skipped += 1
                            continue

                        total_deliveries_processed += 1

                        batter = d.get('batter')
                        bowler = d.get('bowler')
                        if not batter or not bowler:
                            continue

                        runs_dict = d.get('runs', {})
                        bat_runs = runs_dict.get('batter', 0)
                        tot_runs = runs_dict.get('total', 0)
                        extras = d.get('extras', {})

                        has_wide = ('wides' in extras)
                        has_nb = ('noballs' in extras)
                        has_penalty = ('penalty' in extras)

                        # Batter balls faced: delivery counted unless wide or penalty-only
                        is_ball_faced = not has_wide and not (has_penalty and bat_runs == 0 and len(extras) == 1)

                        # Bowler legal ball: delivery without wides or noballs
                        is_legal = not has_wide and not has_nb

                        # Bowler runs conceded: bat runs + wide extras + noball extras
                        runs_conceded = bat_runs + extras.get('wides', 0) + extras.get('noballs', 0)

                        # Dot ball: 0 total runs on delivery
                        is_dot = (tot_runs == 0)

                        is_four = (bat_runs == 4)
                        is_six = (bat_runs == 6)

                        # Dismissals check
                        wkts = d.get('wickets', [])
                        batter_dismissed = False
                        bowler_wicket = False

                        for w in wkts:
                            kind = w.get('kind', '')
                            player_out = w.get('player_out')

                            if player_out == batter and kind != 'retired hurt':
                                batter_dismissed = True

                            if kind in BOWLER_CREDITED_DISMISSALS:
                                bowler_wicket = True

                        # ── 1. Accumulate Head-to-Head Matchup ─────────────────
                        m_key = (batter, bowler, canonical_fmt)
                        acc = matchup_data[m_key]
                        acc['matches'].add(match_id)
                        acc['innings'].add(inn_id)
                        acc['total_balls'] += 1
                        if is_ball_faced:
                            acc['balls_faced'] += 1
                        if is_legal:
                            acc['legal_balls'] += 1
                        acc['runs_scored'] += bat_runs
                        acc['runs_conceded'] += runs_conceded
                        if is_four:
                            acc['fours'] += 1
                        if is_six:
                            acc['sixes'] += 1
                        if bowler_wicket and batter_dismissed:
                            acc['dismissals'] += 1
                        if is_dot:
                            acc['dot_balls'] += 1
                        if m_date:
                            acc['dates'].append(m_date)

                        # ── 2. Accumulate Match Phase Performance ─────────────
                        if phase:
                            # Batter phase
                            bp_key = (batter, 'BATTING', canonical_fmt, phase)
                            bp = phase_data[bp_key]
                            bp['matches'].add(match_id)
                            bp['innings'].add(inn_id)
                            bp['total_deliveries'] += 1
                            if is_ball_faced:
                                bp['balls'] += 1
                            bp['runs'] += bat_runs
                            if is_four:
                                bp['fours'] += 1
                            if is_six:
                                bp['sixes'] += 1
                            if batter_dismissed:
                                bp['wickets_or_dismissals'] += 1
                            if is_dot:
                                bp['dot_balls'] += 1

                            # Bowler phase
                            bw_key = (bowler, 'BOWLING', canonical_fmt, phase)
                            bw = phase_data[bw_key]
                            bw['matches'].add(match_id)
                            bw['innings'].add(inn_id)
                            bw['total_deliveries'] += 1
                            if is_legal:
                                bw['balls'] += 1
                            bw['runs'] += runs_conceded
                            if bowler_wicket:
                                bw['wickets_or_dismissals'] += 1
                            if is_dot:
                                bw['dot_balls'] += 1

    elapsed = (datetime.now() - start_time).total_seconds()
    print(f"\nCompleted raw parsing in {elapsed:.1f}s.")
    print(f"Total files read: {total_files_read}")
    print(f"Total deliveries processed: {total_deliveries_processed:,}")
    print(f"Super Over deliveries excluded: {super_over_deliveries_skipped:,}")
    print(f"Corrupt / Error files: {len(error_files)}")

    # ── Write Output CSV 1: player_bowler_matchups.csv ────────────────────────
    out_file1 = output_dir / "player_bowler_matchups.csv"
    print(f"\nWriting {out_file1.name}...")

    fieldnames1 = [
        "batter", "bowler", "format", "matches", "innings", "balls_faced", "runs",
        "fours", "sixes", "strike_rate", "dismissals", "dot_balls", "dot_pct",
        "boundary_pct", "first_date", "last_date"
    ]

    with open(out_file1, "w", newline="", encoding="utf-8") as f1:
        writer = csv.DictWriter(f1, fieldnames=fieldnames1)
        writer.writeheader()

        for (batter, bowler, fmt), d in matchup_data.items():
            bf = d['balls_faced']
            runs = d['runs_scored']
            sr = round((runs / bf) * 100.0, 2) if bf > 0 else 0.0
            tot_b = d['total_balls']
            dot_pct = round((d['dot_balls'] / tot_b) * 100.0, 1) if tot_b > 0 else 0.0
            bnd_pct = round(((d['fours'] * 4 + d['sixes'] * 6) / runs) * 100.0, 1) if runs > 0 else 0.0
            dates_sorted = sorted(d['dates']) if d['dates'] else []

            writer.writerow({
                "batter": batter,
                "bowler": bowler,
                "format": fmt,
                "matches": len(d['matches']),
                "innings": len(d['innings']),
                "balls_faced": bf,
                "runs": runs,
                "fours": d['fours'],
                "sixes": d['sixes'],
                "strike_rate": sr,
                "dismissals": d['dismissals'],
                "dot_balls": d['dot_balls'],
                "dot_pct": dot_pct,
                "boundary_pct": bnd_pct,
                "first_date": dates_sorted[0] if dates_sorted else "",
                "last_date": dates_sorted[-1] if dates_sorted else ""
            })

    # ── Write Output CSV 2: bowler_batter_matchups.csv ────────────────────────
    out_file2 = output_dir / "bowler_batter_matchups.csv"
    print(f"Writing {out_file2.name}...")

    fieldnames2 = [
        "bowler", "batter", "format", "matches", "innings", "legal_balls", "runs_conceded",
        "wickets", "economy", "bowling_avg", "bowling_sr", "dot_balls", "dot_pct",
        "boundaries_conceded", "first_date", "last_date"
    ]

    with open(out_file2, "w", newline="", encoding="utf-8") as f2:
        writer = csv.DictWriter(f2, fieldnames=fieldnames2)
        writer.writeheader()

        for (batter, bowler, fmt), d in matchup_data.items():
            lb = d['legal_balls']
            rc = d['runs_conceded']
            w = d['dismissals']
            dec_overs = lb / 6.0
            econ = round(rc / dec_overs, 2) if dec_overs > 0 else 0.0
            b_avg = round(rc / w, 2) if w > 0 else -1.0
            b_sr = round(lb / w, 1) if w > 0 else -1.0
            tot_b = d['total_balls']
            dot_pct = round((d['dot_balls'] / tot_b) * 100.0, 1) if tot_b > 0 else 0.0
            dates_sorted = sorted(d['dates']) if d['dates'] else []

            writer.writerow({
                "bowler": bowler,
                "batter": batter,
                "format": fmt,
                "matches": len(d['matches']),
                "innings": len(d['innings']),
                "legal_balls": lb,
                "runs_conceded": rc,
                "wickets": w,
                "economy": econ,
                "bowling_avg": b_avg,
                "bowling_sr": b_sr,
                "dot_balls": d['dot_balls'],
                "dot_pct": dot_pct,
                "boundaries_conceded": d['fours'] + d['sixes'],
                "first_date": dates_sorted[0] if dates_sorted else "",
                "last_date": dates_sorted[-1] if dates_sorted else ""
            })

    # ── Write Output CSV 3: player_phase_performance.csv ──────────────────────
    out_file3 = output_dir / "player_phase_performance.csv"
    print(f"Writing {out_file3.name}...")

    fieldnames3 = [
        "player", "discipline", "format", "phase", "matches", "innings", "balls",
        "runs", "fours", "sixes", "wickets_or_dismissals", "dot_balls", "rate_stat",
        "avg_stat", "dot_pct", "boundary_pct"
    ]

    with open(out_file3, "w", newline="", encoding="utf-8") as f3:
        writer = csv.DictWriter(f3, fieldnames=fieldnames3)
        writer.writeheader()

        for (player, discipline, fmt, phase), d in phase_data.items():
            balls = d['balls']
            runs = d['runs']
            w_or_d = d['wickets_or_dismissals']
            tot_delivs = d['total_deliveries']
            dot_pct = round((d['dot_balls'] / tot_delivs) * 100.0, 1) if tot_delivs > 0 else 0.0

            if discipline == 'BATTING':
                rate_stat = round((runs / balls) * 100.0, 2) if balls > 0 else 0.0
                avg_stat = round(runs / w_or_d, 2) if w_or_d > 0 else -1.0
                bnd_pct = round(((d['fours'] * 4 + d['sixes'] * 6) / runs) * 100.0, 1) if runs > 0 else 0.0
            else:
                dec_overs = balls / 6.0
                rate_stat = round(runs / dec_overs, 2) if dec_overs > 0 else 0.0
                avg_stat = round(runs / w_or_d, 2) if w_or_d > 0 else -1.0
                bnd_pct = 0.0

            writer.writerow({
                "player": player,
                "discipline": discipline,
                "format": fmt,
                "phase": phase,
                "matches": len(d['matches']),
                "innings": len(d['innings']),
                "balls": balls,
                "runs": runs,
                "fours": d['fours'],
                "sixes": d['sixes'],
                "wickets_or_dismissals": w_or_d,
                "dot_balls": d['dot_balls'],
                "rate_stat": rate_stat,
                "avg_stat": avg_stat,
                "dot_pct": dot_pct,
                "boundary_pct": bnd_pct
            })

    print("\n--- PREPROCESSING COMPLETE ---")
    print(f"1. {out_file1.name}: {os.path.getsize(out_file1):,} bytes ({len(matchup_data):,} matchup records)")
    print(f"2. {out_file2.name}: {os.path.getsize(out_file2):,} bytes ({len(matchup_data):,} matchup records)")
    print(f"3. {out_file3.name}: {os.path.getsize(out_file3):,} bytes ({len(phase_data):,} phase records)")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Preprocess Cricsheet JSON files into player strengths CSVs")
    parser.add_argument("--data-dir", default=str(DEFAULT_DATA_DIR), help="Cricsheet dataset directory")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR), help="Output directory for CSV files")
    args = parser.parse_args()

    run_preprocessing(Path(args.data_dir), Path(args.output_dir))
