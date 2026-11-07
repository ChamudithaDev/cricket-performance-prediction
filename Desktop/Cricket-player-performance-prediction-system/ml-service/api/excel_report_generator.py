"""
excel_report_generator.py

CricketAI — Professional Native Excel (.xlsx) Report Generator
Uses xlsxwriter to generate workbooks with:
- Structured worksheets matching displayed UI metrics and tables
- 100% genuine, native, editable DrawingML Excel charts (Line, Column, Bar, Pie)
- Chart series formulas bound directly to worksheet data ranges
- Professional Dark Navy & Accent styling adhering to CricketAI theme
- Frozen panes, bold headers, and custom numeric/percentage formatting
"""

import io
import sys
import json
import argparse
from datetime import datetime
import xlsxwriter


# ─── Color Palette & Format Definitions ──────────────────────────────────────

COLORS = {
    'header_fill': '#0D1525',
    'header_font': '#F0F4FF',
    'subheader_fill': '#1A2236',
    'subheader_font': '#8A9AC0',
    'accent_blue': '#3B82F6',
    'p1_line': '#6366F1',       # Indigo
    'p2_line': '#EC4899',       # Pink
    'baseline_orange': '#F59E0B',
    'baseline_red': '#EF4444',
    'bowling_red': '#EF4444',
    'green': '#10B981',
    'border': '#E2E8F0',
}


def sanitize_sheet_name(name):
    clean = str(name or '').strip().replace('\\', '_').replace('/', '_').replace('?', '_')
    clean = clean.replace('*', '_').replace(':', '_').replace('[', '_').replace(']', '_')
    return clean[:31] if clean else 'Sheet'


def sanitize_filename(name):
    clean = str(name or '').strip()
    for ch in ['<', '>', ':', '"', '/', '\\', '|', '?', '*']:
        clean = clean.replace(ch, '_')
    return '_'.join(clean.split())


# ─── Helper: Setup Formats ───────────────────────────────────────────────────

def create_formats(wb):
    return {
        'title': wb.add_format({
            'bold': True, 'font_size': 14, 'font_name': 'Calibri',
            'font_color': COLORS['accent_blue']
        }),
        'section_title': wb.add_format({
            'bold': True, 'font_size': 11, 'font_name': 'Calibri',
            'font_color': COLORS['accent_blue']
        }),
        'header': wb.add_format({
            'bold': True, 'font_size': 11, 'font_name': 'Calibri',
            'font_color': COLORS['header_font'], 'bg_color': COLORS['header_fill'],
            'align': 'center', 'valign': 'vcenter', 'border': 1
        }),
        'sub_header': wb.add_format({
            'bold': True, 'font_size': 10, 'font_name': 'Calibri',
            'font_color': COLORS['subheader_font'], 'bg_color': COLORS['subheader_fill'],
            'align': 'center', 'valign': 'vcenter', 'border': 1
        }),
        'cell': wb.add_format({
            'font_size': 10, 'font_name': 'Calibri',
            'valign': 'vcenter', 'border': 1
        }),
        'cell_center': wb.add_format({
            'font_size': 10, 'font_name': 'Calibri',
            'align': 'center', 'valign': 'vcenter', 'border': 1
        }),
        'cell_num': wb.add_format({
            'font_size': 10, 'font_name': 'Calibri',
            'align': 'right', 'valign': 'vcenter', 'border': 1,
            'num_format': '#,##0'
        }),
        'cell_dec1': wb.add_format({
            'font_size': 10, 'font_name': 'Calibri',
            'align': 'right', 'valign': 'vcenter', 'border': 1,
            'num_format': '0.0'
        }),
        'cell_dec2': wb.add_format({
            'font_size': 10, 'font_name': 'Calibri',
            'align': 'right', 'valign': 'vcenter', 'border': 1,
            'num_format': '0.00'
        }),
        'cell_pct': wb.add_format({
            'font_size': 10, 'font_name': 'Calibri',
            'align': 'right', 'valign': 'vcenter', 'border': 1
        }),
        'meta_label': wb.add_format({
            'bold': True, 'font_size': 10, 'font_name': 'Calibri',
            'font_color': '#475569'
        }),
        'meta_val': wb.add_format({
            'font_size': 10, 'font_name': 'Calibri',
            'font_color': '#0F172A'
        }),
    }


# ═════════════════════════════════════════════════════════════════════════════
# 1. PLAYER ANALYSIS WORKBOOK GENERATOR
# ═════════════════════════════════════════════════════════════════════════════

def generate_player_analysis_workbook(analytics, output_target, selected_format="ALL", selected_recent_n=15):
    """
    Generates a genuine .xlsx workbook containing all player analysis metrics,
    tables, and genuine native Excel charts (Line, Column, Bar, Doughnut).
    """
    wb = xlsxwriter.Workbook(output_target)
    fmt = create_formats(wb)

    player_name = analytics.get('playerName', 'Unknown Player')
    role = analytics.get('role', 'UNKNOWN')
    bat_summary = analytics.get('battingSummary') or {}
    bowl_summary = analytics.get('bowlingSummary') or {}
    has_batting = bool(analytics.get('recentBattingForm') or bat_summary)
    has_bowling = bool(analytics.get('recentBowlingForm') or bowl_summary)

    is_bat = role in ['BATSMAN', 'ALL_ROUNDER'] or (role == 'UNKNOWN' and has_batting)
    is_bowl = role in ['BOWLER', 'ALL_ROUNDER'] or (role == 'UNKNOWN' and has_bowling and not has_batting)

    recent_bat = analytics.get('recentBattingForm', [])
    recent_bowl = analytics.get('recentBowlingForm', [])
    recent_summary = analytics.get('recentFormSummary') or {}
    score_dist = analytics.get('scoreDistribution') or {}
    bowl_dist = analytics.get('bowlingDistribution') or {}

    # ── Sheet 1: Report Summary ──────────────────────────────────────────────
    ws_sum = wb.add_worksheet('Report Summary')
    ws_sum.freeze_panes(1, 0)
    ws_sum.write(0, 0, f"CricketAI — Player Performance Analysis: {player_name}", fmt['title'])
    ws_sum.write(1, 0, "Generated by CricketAI Performance Prediction and Analytics System", fmt['meta_label'])

    ws_sum.write(3, 0, "Report Parameters & Filter Context", fmt['section_title'])
    meta_rows = [
        ("Player Name", player_name),
        ("Detected Discipline Role", f"{role} (Dual-facet)" if role == 'ALL_ROUNDER' else role),
        ("Selected Format Filter", selected_format),
        ("Recent Appearance Window", f"Last {selected_recent_n} appearances"),
        ("Export Timestamp", datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
        ("Total Career Batting Innings", analytics.get('totalBattingInnings', 0)),
        ("Total Career Bowling Innings", analytics.get('totalBowlingInnings', 0)),
        ("Dataset Source", "Verified International Ball-by-Ball Cricsheet Dataset"),
    ]
    for r_idx, (k, v) in enumerate(meta_rows, start=4):
        ws_sum.write(r_idx, 0, k, fmt['meta_label'])
        ws_sum.write(r_idx, 1, v, fmt['meta_val'])

    curr_row = 13
    if is_bat and bat_summary:
        ws_sum.write(curr_row, 0, f"Batting Summary Overview ({selected_format})", fmt['section_title'])
        curr_row += 1
        bat_kpis = [
            ("Innings Batted", bat_summary.get('innings', 0)),
            ("Total Runs Scored", bat_summary.get('totalRuns', 0)),
            ("Runs per Innings", round(bat_summary.get('runsPerInnings', 0), 1) if bat_summary.get('runsPerInnings') is not None else "—"),
            ("Batting Strike Rate", round(bat_summary.get('strikeRate', 0), 1) if bat_summary.get('strikeRate') is not None else "—"),
            ("Highest Score", f"{bat_summary.get('highestScore', '—')}{'*' if bat_summary.get('highestScoreNotOut') else ''}"),
            ("Centuries / Fifties", f"{bat_summary.get('hundreds', 0)} / {bat_summary.get('fifties', 0)}"),
            ("Boundary Fours / Sixes", f"{bat_summary.get('fours', 0)} / {bat_summary.get('sixes', 0)}"),
            ("Boundary Run Contribution", f"{bat_summary.get('boundaryPercentage', 0)}%"),
            ("Scoring Consistency (CV%)", f"{bat_summary.get('coefficientOfVariation', '—')}%"),
        ]
        for k, v in bat_kpis:
            ws_sum.write(curr_row, 0, k, fmt['meta_label'])
            ws_sum.write(curr_row, 1, v, fmt['meta_val'])
            curr_row += 1
        curr_row += 1

    if is_bowl and bowl_summary:
        ws_sum.write(curr_row, 0, f"Bowling Summary Overview ({selected_format})", fmt['section_title'])
        curr_row += 1
        bowl_kpis = [
            ("Bowling Spells", bowl_summary.get('innings', 0)),
            ("Total Wickets Taken", bowl_summary.get('totalWickets', 0)),
            ("Total Overs Bowled", bowl_summary.get('totalOvers', 0)),
            ("Total Runs Conceded", bowl_summary.get('runsConceded', 0)),
            ("Economy Rate", round(bowl_summary.get('economyRate', 0), 2) if bowl_summary.get('economyRate') is not None else "—"),
            ("Bowling Average", round(bowl_summary.get('bowlingAverage', 0), 2) if bowl_summary.get('bowlingAverage') is not None else "N/A"),
            ("Bowling Strike Rate", f"{bowl_summary.get('bowlingStrikeRate', '—')} b/w"),
            ("4-Wicket / 5-Wicket Hauls", f"{bowl_summary.get('fourWickets', 0)} / {bowl_summary.get('fiveWickets', 0)}"),
        ]
        for k, v in bowl_kpis:
            ws_sum.write(curr_row, 0, k, fmt['meta_label'])
            ws_sum.write(curr_row, 1, v, fmt['meta_val'])
            curr_row += 1

    ws_sum.set_column(0, 0, 28)
    ws_sum.set_column(1, 1, 35)

    # ── Sheet 2: Career Statistics ───────────────────────────────────────────
    ws_car = wb.add_worksheet('Career Statistics')
    ws_car.freeze_panes(1, 0)
    car_cols = [
        ('Format', 12), ('Batting Innings', 16), ('Total Runs', 14), ('Runs / Inn', 14),
        ('Batting SR', 14), ('50s', 10), ('100s', 10), ('Bowling Spells', 16),
        ('Wickets', 12), ('Bowling Avg', 14), ('Economy', 12), ('4W Hauls', 12), ('5W Hauls', 12)
    ]
    for c_idx, (h, w) in enumerate(car_cols):
        ws_car.write(0, c_idx, h, fmt['header'])
        ws_car.set_column(c_idx, c_idx, w)

    format_rows = analytics.get('formatBreakdown') or [
        {
            'format': selected_format,
            'battingInnings': bat_summary.get('innings') or analytics.get('totalBattingInnings', 0),
            'totalRuns': bat_summary.get('totalRuns', 0),
            'runsPerInnings': bat_summary.get('runsPerInnings'),
            'battingStrikeRate': bat_summary.get('strikeRate'),
            'fifties': bat_summary.get('fifties', 0),
            'hundreds': bat_summary.get('hundreds', 0),
            'bowlingInnings': bowl_summary.get('innings') or analytics.get('totalBowlingInnings', 0),
            'totalWickets': bowl_summary.get('totalWickets', 0),
            'bowlingAverage': bowl_summary.get('bowlingAverage'),
            'economyRate': bowl_summary.get('economyRate'),
            'fourWickets': bowl_summary.get('fourWickets', 0),
            'fiveWickets': bowl_summary.get('fiveWickets', 0),
        }
    ]

    for r_idx, r in enumerate(format_rows, start=1):
        ws_car.write(r_idx, 0, r.get('format', '—'), fmt['cell_center'])
        ws_car.write(r_idx, 1, r.get('battingInnings', 0), fmt['cell_num'])
        ws_car.write(r_idx, 2, r.get('totalRuns', 0), fmt['cell_num'])
        ws_car.write(r_idx, 3, r.get('runsPerInnings') or '—', fmt['cell_dec1'] if isinstance(r.get('runsPerInnings'), (int, float)) else fmt['cell_center'])
        sr = r.get('battingStrikeRate') or r.get('strikeRate')
        ws_car.write(r_idx, 4, sr or '—', fmt['cell_dec1'] if isinstance(sr, (int, float)) else fmt['cell_center'])
        ws_car.write(r_idx, 5, r.get('fifties', 0), fmt['cell_num'])
        ws_car.write(r_idx, 6, r.get('hundreds', 0), fmt['cell_num'])
        ws_car.write(r_idx, 7, r.get('bowlingInnings', 0), fmt['cell_num'])
        ws_car.write(r_idx, 8, r.get('totalWickets', 0), fmt['cell_num'])
        ba = r.get('bowlingAverage')
        ws_car.write(r_idx, 9, ba or '—', fmt['cell_dec2'] if isinstance(ba, (int, float)) else fmt['cell_center'])
        ec = r.get('economyRate')
        ws_car.write(r_idx, 10, ec or '—', fmt['cell_dec2'] if isinstance(ec, (int, float)) else fmt['cell_center'])
        ws_car.write(r_idx, 11, r.get('fourWickets', 0), fmt['cell_num'])
        ws_car.write(r_idx, 12, r.get('fiveWickets', 0), fmt['cell_num'])

    # ── Sheet 3: Recent Performance (Match Logs) ──────────────────────────────
    ws_rec = wb.add_worksheet('Recent Performance')
    ws_rec.freeze_panes(2, 0)
    rec_r = 0

    if is_bat:
        ws_rec.write(rec_r, 0, "Recent Batting Innings (Latest Appearances First)", fmt['section_title'])
        rec_r += 1
        bat_log_cols = [
            ('Date', 13), ('Format', 10), ('vs Opposition', 16), ('Venue', 24),
            ('Runs', 10), ('Balls Faced', 12), ('Strike Rate', 12), ('4s', 8), ('6s', 8),
            ('Not Out', 10), (f'In Window (N={selected_recent_n})', 16)
        ]
        for c_idx, (h, w) in enumerate(bat_log_cols):
            ws_rec.write(rec_r, c_idx, h, fmt['header'])
            ws_rec.set_column(c_idx, c_idx, w)
        rec_r += 1

        b_list = list(reversed(recent_bat)) if recent_bat else []
        cutoff = max(0, len(recent_bat) - selected_recent_n) if recent_bat else 0
        for idx, m in enumerate(b_list):
            orig_idx = len(recent_bat) - 1 - idx
            in_win = 'YES' if orig_idx >= cutoff else 'NO'
            ws_rec.write(rec_r, 0, m.get('date', '—'), fmt['cell_center'])
            ws_rec.write(rec_r, 1, m.get('format', selected_format), fmt['cell_center'])
            ws_rec.write(rec_r, 2, m.get('opposition', '—'), fmt['cell'])
            ws_rec.write(rec_r, 3, m.get('venue', '—'), fmt['cell'])
            ws_rec.write(rec_r, 4, m.get('runs', 0), fmt['cell_num'])
            ws_rec.write(rec_r, 5, m.get('ballsFaced') or m.get('balls') or 0, fmt['cell_num'])
            ws_rec.write(rec_r, 6, m.get('strikeRate') or '—', fmt['cell_dec1'] if isinstance(m.get('strikeRate'), (int, float)) else fmt['cell_center'])
            ws_rec.write(rec_r, 7, m.get('fours', 0), fmt['cell_num'])
            ws_rec.write(rec_r, 8, m.get('sixes', 0), fmt['cell_num'])
            ws_rec.write(rec_r, 9, 'YES' if m.get('notOut') else 'NO', fmt['cell_center'])
            ws_rec.write(rec_r, 10, in_win, fmt['cell_center'])
            rec_r += 1
        rec_r += 2

    if is_bowl:
        ws_rec.write(rec_r, 0, "Recent Bowling Spells (Latest Appearances First)", fmt['section_title'])
        rec_r += 1
        bowl_log_cols = [
            ('Date', 13), ('Format', 10), ('vs Opposition', 16), ('Venue', 24),
            ('Overs', 10), ('Runs Conceded', 14), ('Wickets', 10), ('Economy', 12),
            ('Haul Badge', 12), (f'In Window (N={selected_recent_n})', 16)
        ]
        for c_idx, (h, w) in enumerate(bowl_log_cols):
            ws_rec.write(rec_r, c_idx, h, fmt['header'])
            ws_rec.set_column(c_idx, c_idx, w)
        rec_r += 1

        bw_list = list(reversed(recent_bowl)) if recent_bowl else []
        cutoff = max(0, len(recent_bowl) - selected_recent_n) if recent_bowl else 0
        for idx, m in enumerate(bw_list):
            orig_idx = len(recent_bowl) - 1 - idx
            in_win = 'YES' if orig_idx >= cutoff else 'NO'
            ws_rec.write(rec_r, 0, m.get('date', '—'), fmt['cell_center'])
            ws_rec.write(rec_r, 1, m.get('format', selected_format), fmt['cell_center'])
            ws_rec.write(rec_r, 2, m.get('opposition', '—'), fmt['cell'])
            ws_rec.write(rec_r, 3, m.get('venue', '—'), fmt['cell'])
            ws_rec.write(rec_r, 4, m.get('overs', 0), fmt['cell_dec1'] if isinstance(m.get('overs'), (int, float)) else fmt['cell_center'])
            ws_rec.write(rec_r, 5, m.get('runsConceded', 0), fmt['cell_num'])
            ws_rec.write(rec_r, 6, m.get('wickets', 0), fmt['cell_num'])
            ws_rec.write(rec_r, 7, m.get('economyRate') or '—', fmt['cell_dec2'] if isinstance(m.get('economyRate'), (int, float)) else fmt['cell_center'])
            ws_rec.write(rec_r, 8, m.get('haulBadge') or f"{m.get('wickets', 0)}W", fmt['cell_center'])
            ws_rec.write(rec_r, 9, in_win, fmt['cell_center'])
            rec_r += 1

    # ── Sheet 4: Performance Trends + NATIVE CHARTS 1 & 2 (and 3, 4, 5) ──────
    ws_trends = wb.add_worksheet('Performance Trends')
    ws_trends.freeze_panes(2, 0)

    # Batting Trends Table
    bat_window = recent_bat[-selected_recent_n:] if recent_bat else []
    mean_runs = recent_summary.get('meanRuns') or (sum(m.get('runs', 0) for m in bat_window) / len(bat_window) if bat_window else 0)
    agg_sr = bat_summary.get('strikeRate', 0)

    ws_trends.write(0, 0, f"Batting Trendline Data ({player_name})", fmt['section_title'])
    trend_cols = [
        'Innings #', 'Date', 'Opposition', 'Venue', 'Runs Scored',
        'Window Mean Runs (Baseline)', 'Innings Strike Rate', 'Aggregate Format SR (Baseline)'
    ]
    for c_idx, h in enumerate(trend_cols):
        ws_trends.write(1, c_idx, h, fmt['header'])

    for idx, m in enumerate(bat_window):
        r_num = 2 + idx
        ws_trends.write(r_num, 0, f"Inn {idx + 1}", fmt['cell_center'])
        ws_trends.write(r_num, 1, m.get('date', '—'), fmt['cell_center'])
        ws_trends.write(r_num, 2, m.get('opposition', '—'), fmt['cell'])
        ws_trends.write(r_num, 3, m.get('venue', '—'), fmt['cell'])
        ws_trends.write(r_num, 4, m.get('runs', 0), fmt['cell_num'])
        ws_trends.write(r_num, 5, round(mean_runs, 1), fmt['cell_dec1'])
        sr = m.get('strikeRate', 0)
        ws_trends.write(r_num, 6, round(sr, 1) if sr else 0, fmt['cell_dec1'])
        ws_trends.write(r_num, 7, round(agg_sr, 1) if agg_sr else 0, fmt['cell_dec1'])

    ws_trends.set_column(0, 0, 12)
    ws_trends.set_column(1, 1, 13)
    ws_trends.set_column(2, 2, 16)
    ws_trends.set_column(3, 3, 22)
    ws_trends.set_column(4, 7, 16)

    # Insert Native Charts for Batting
    if len(bat_window) > 0:
        max_r = 1 + len(bat_window)

        # CHART 1: Runs per Innings Trendline (Native Line Chart)
        chart_runs = wb.add_chart({'type': 'line'})
        chart_runs.add_series({
            'name': 'Runs Scored',
            'categories': ['Performance Trends', 2, 0, max_r, 0],
            'values':     ['Performance Trends', 2, 4, max_r, 4],
            'line':       {'color': COLORS['p1_line'], 'width': 2.5},
            'marker':     {'type': 'circle', 'size': 6, 'border': {'color': COLORS['p1_line']}, 'fill': {'color': '#FFFFFF'}},
        })
        chart_runs.add_series({
            'name': f'Window Mean ({round(mean_runs, 1)})',
            'categories': ['Performance Trends', 2, 0, max_r, 0],
            'values':     ['Performance Trends', 2, 5, max_r, 5],
            'line':       {'color': COLORS['baseline_orange'], 'width': 1.75, 'dash_type': 'dash'},
        })
        chart_runs.set_title({'name': f"Runs Scored per Innings & Reference Baseline — {player_name}"})
        chart_runs.set_x_axis({'name': 'Chronological Innings'})
        chart_runs.set_y_axis({'name': 'Runs Scored'})
        chart_runs.set_style(10)
        chart_runs.set_size({'width': 600, 'height': 340})
        ws_trends.insert_chart('J2', chart_runs)

        # CHART 2: Batting Strike Rate Trendline (Native Line Chart)
        chart_sr = wb.add_chart({'type': 'line'})
        chart_sr.add_series({
            'name': 'Innings Strike Rate',
            'categories': ['Performance Trends', 2, 0, max_r, 0],
            'values':     ['Performance Trends', 2, 6, max_r, 6],
            'line':       {'color': '#38BDF8', 'width': 2.5},
            'marker':     {'type': 'square', 'size': 6, 'border': {'color': '#38BDF8'}, 'fill': {'color': '#FFFFFF'}},
        })
        chart_sr.add_series({
            'name': f'Aggregate SR ({round(agg_sr, 1)})',
            'categories': ['Performance Trends', 2, 0, max_r, 0],
            'values':     ['Performance Trends', 2, 7, max_r, 7],
            'line':       {'color': '#94A3B8', 'width': 1.75, 'dash_type': 'dash'},
        })
        chart_sr.set_title({'name': f"Innings Strike Rate vs Career Format Baseline — {player_name}"})
        chart_sr.set_x_axis({'name': 'Innings'})
        chart_sr.set_y_axis({'name': 'Strike Rate (Runs / 100 Balls)'})
        chart_sr.set_style(10)
        chart_sr.set_size({'width': 600, 'height': 340})
        ws_trends.insert_chart('J20', chart_sr)

    # Bowling Trends Table (if bowler or all-rounder)
    bowl_window = recent_bowl[-selected_recent_n:] if recent_bowl else []
    if is_bowl and bowl_window:
        b_start_row = max(18, 4 + len(bat_window))
        mean_wkts = recent_summary.get('meanWickets') or (sum(m.get('wickets', 0) for m in bowl_window) / len(bowl_window))
        agg_econ = bowl_summary.get('economyRate', 0)

        ws_trends.write(b_start_row, 0, f"Bowling Trendline Data ({player_name})", fmt['section_title'])
        bowl_cols = [
            'Spell #', 'Date', 'Opposition', 'Venue', 'Wickets Taken',
            'Window Mean Wkts (Baseline)', 'Economy Rate', 'Format Economy (Baseline)', 'Runs Conceded'
        ]
        for c_idx, h in enumerate(bowl_cols):
            ws_trends.write(b_start_row + 1, c_idx, h, fmt['header'])

        for idx, m in enumerate(bowl_window):
            r_num = b_start_row + 2 + idx
            ws_trends.write(r_num, 0, f"Spell {idx + 1}", fmt['cell_center'])
            ws_trends.write(r_num, 1, m.get('date', '—'), fmt['cell_center'])
            ws_trends.write(r_num, 2, m.get('opposition', '—'), fmt['cell'])
            ws_trends.write(r_num, 3, m.get('venue', '—'), fmt['cell'])
            ws_trends.write(r_num, 4, m.get('wickets', 0), fmt['cell_num'])
            ws_trends.write(r_num, 5, round(mean_wkts, 2), fmt['cell_dec2'])
            econ = m.get('economyRate', 0)
            ws_trends.write(r_num, 6, round(econ, 2) if econ else 0, fmt['cell_dec2'])
            ws_trends.write(r_num, 7, round(agg_econ, 2) if agg_econ else 0, fmt['cell_dec2'])
            ws_trends.write(r_num, 8, m.get('runsConceded', 0), fmt['cell_num'])

        b_max_r = b_start_row + 1 + len(bowl_window)

        # CHART 3: Wickets Trendline (Native Line Chart)
        chart_wkts = wb.add_chart({'type': 'line'})
        chart_wkts.add_series({
            'name': 'Wickets Taken',
            'categories': ['Performance Trends', b_start_row + 2, 0, b_max_r, 0],
            'values':     ['Performance Trends', b_start_row + 2, 4, b_max_r, 4],
            'line':       {'color': COLORS['bowling_red'], 'width': 2.5},
            'marker':     {'type': 'diamond', 'size': 6, 'border': {'color': COLORS['bowling_red']}, 'fill': {'color': '#FFFFFF'}},
        })
        chart_wkts.add_series({
            'name': f'Mean Wickets ({round(mean_wkts, 2)})',
            'categories': ['Performance Trends', b_start_row + 2, 0, b_max_r, 0],
            'values':     ['Performance Trends', b_start_row + 2, 5, b_max_r, 5],
            'line':       {'color': COLORS['baseline_orange'], 'width': 1.75, 'dash_type': 'dash'},
        })
        chart_wkts.set_title({'name': f"Wickets per Spell & Reference Baseline — {player_name}"})
        chart_wkts.set_x_axis({'name': 'Chronological Spells'})
        chart_wkts.set_y_axis({'name': 'Wickets'})
        chart_wkts.set_size({'width': 600, 'height': 340})
        ws_trends.insert_chart(f'J{b_start_row + 2}', chart_wkts)

        # CHART 4: Bowling Economy Trendline (Native Line Chart)
        chart_econ = wb.add_chart({'type': 'line'})
        chart_econ.add_series({
            'name': 'Economy Rate',
            'categories': ['Performance Trends', b_start_row + 2, 0, b_max_r, 0],
            'values':     ['Performance Trends', b_start_row + 2, 6, b_max_r, 6],
            'line':       {'color': COLORS['green'], 'width': 2.5},
            'marker':     {'type': 'circle', 'size': 6, 'border': {'color': COLORS['green']}, 'fill': {'color': '#FFFFFF'}},
        })
        chart_econ.add_series({
            'name': f'Format Economy Baseline ({round(agg_econ, 2)})',
            'categories': ['Performance Trends', b_start_row + 2, 0, b_max_r, 0],
            'values':     ['Performance Trends', b_start_row + 2, 7, b_max_r, 7],
            'line':       {'color': '#94A3B8', 'width': 1.75, 'dash_type': 'dash'},
        })
        chart_econ.set_title({'name': f"Bowling Economy Rate vs Career Format Baseline — {player_name}"})
        chart_econ.set_x_axis({'name': 'Spells'})
        chart_econ.set_y_axis({'name': 'Economy Rate (RPO)'})
        chart_econ.set_size({'width': 600, 'height': 340})
        ws_trends.insert_chart(f'J{b_start_row + 20}', chart_econ)

        # CHART 5: Wickets vs Runs Conceded (Native Clustered Column Chart)
        chart_combo = wb.add_chart({'type': 'column'})
        chart_combo.add_series({
            'name': 'Wickets',
            'categories': ['Performance Trends', b_start_row + 2, 0, b_max_r, 0],
            'values':     ['Performance Trends', b_start_row + 2, 4, b_max_r, 4],
            'fill':       {'color': COLORS['bowling_red']},
        })
        chart_combo.add_series({
            'name': 'Runs Conceded',
            'categories': ['Performance Trends', b_start_row + 2, 0, b_max_r, 0],
            'values':     ['Performance Trends', b_start_row + 2, 8, b_max_r, 8],
            'fill':       {'color': '#F59E0B'},
        })
        chart_combo.set_title({'name': f"Wickets Taken & Runs Conceded Comparison — {player_name}"})
        chart_combo.set_size({'width': 600, 'height': 340})
        ws_trends.insert_chart(f'J{b_start_row + 38}', chart_combo)

    # ── Sheet 5: Milestones & Boundaries + NATIVE CHARTS 6, 7 & 8 ─────────────
    ws_dist = wb.add_worksheet('Milestones & Boundaries')
    ws_dist.freeze_panes(2, 0)

    # Table 1: Scoring Pattern Distribution
    ws_dist.write(0, 0, "Career Scoring Pattern & Milestone Frequency", fmt['section_title'])
    ws_dist.write(1, 0, "Score Range Bracket", fmt['header'])
    ws_dist.write(1, 1, "Innings Count", fmt['header'])
    ws_dist.write(1, 2, "Percentage (%)", fmt['header'])

    total_bat_inn = bat_summary.get('innings') or analytics.get('totalBattingInnings', 1)
    if total_bat_inn <= 0: total_bat_inn = 1

    dist_items = [
        ("Ducks (0)", score_dist.get('ducks', 0)),
        ("Under 20 (1–19)", score_dist.get('underTwenty', 0)),
        ("Twenties (20–29)", score_dist.get('twenties', 0)),
        ("Thirties & Forties (30–49)", score_dist.get('thirtiesToForties', 0)),
        ("Fifties (50–99)", score_dist.get('fifties', 0)),
        ("Centuries (100+)", score_dist.get('hundreds', 0)),
    ]

    for idx, (lbl, cnt) in enumerate(dist_items, start=2):
        ws_dist.write(idx, 0, lbl, fmt['cell'])
        ws_dist.write(idx, 1, cnt, fmt['cell_num'])
        pct = round((cnt / total_bat_inn) * 100, 1)
        ws_dist.write(idx, 2, f"{pct}%", fmt['cell_pct'])

    # CHART 6: Scoring Distribution (Native Column Chart)
    chart_dist = wb.add_chart({'type': 'column'})
    chart_dist.add_series({
        'name': 'Innings Count',
        'categories': ['Milestones & Boundaries', 2, 0, 7, 0],
        'values':     ['Milestones & Boundaries', 2, 1, 7, 1],
        'fill':       {'color': '#6366F1'},
        'data_labels': {'value': True},
    })
    chart_dist.set_title({'name': f"Scoring Milestone Distribution — {player_name}"})
    chart_dist.set_x_axis({'name': 'Score Range'})
    chart_dist.set_y_axis({'name': 'Innings'})
    chart_dist.set_legend({'none': True})
    chart_dist.set_size({'width': 560, 'height': 320})
    ws_dist.insert_chart('E2', chart_dist)

    # Table 2: Boundary Run Contribution
    b_runs_4 = (bat_summary.get('fours', 0) or 0) * 4
    b_runs_6 = (bat_summary.get('sixes', 0) or 0) * 6
    total_runs = bat_summary.get('totalRuns', 0) or 0
    non_boundary_runs = max(0, total_runs - (b_runs_4 + b_runs_6))

    ws_dist.write(9, 0, "Boundary Run Contribution Breakdown", fmt['section_title'])
    ws_dist.write(10, 0, "Contribution Source", fmt['header'])
    ws_dist.write(10, 1, "Runs Scored", fmt['header'])
    ws_dist.write(10, 2, "Share of Total (%)", fmt['header'])

    b_items = [
        ("Runs from Fours (4s)", b_runs_4),
        ("Runs from Sixes (6s)", b_runs_6),
        ("Non-Boundary Runs (Running)", non_boundary_runs),
    ]

    for idx, (lbl, r_val) in enumerate(b_items, start=11):
        ws_dist.write(idx, 0, lbl, fmt['cell'])
        ws_dist.write(idx, 1, r_val, fmt['cell_num'])
        pct = round((r_val / total_runs) * 100, 1) if total_runs > 0 else 0
        ws_dist.write(idx, 2, f"{pct}%", fmt['cell_pct'])

    # CHART 7: Boundary Contribution (Native Doughnut/Pie Chart)
    chart_pie = wb.add_chart({'type': 'doughnut'})
    chart_pie.add_series({
        'name': 'Boundary Run Share',
        'categories': ['Milestones & Boundaries', 11, 0, 13, 0],
        'values':     ['Milestones & Boundaries', 11, 1, 13, 1],
        'points': [
            {'fill': {'color': '#3B82F6'}},
            {'fill': {'color': '#F59E0B'}},
            {'fill': {'color': '#10B981'}},
        ],
        'data_labels': {'percentage': True, 'category': True},
    })
    chart_pie.set_title({'name': f"Boundary Run Share — {player_name}"})
    chart_pie.set_size({'width': 560, 'height': 320})
    ws_dist.insert_chart('E11', chart_pie)

    # Table 3 & CHART 8: Bowling Wickets Distribution (if bowler)
    if is_bowl and bowl_dist:
        ws_dist.write(16, 0, "Bowling Wicket Haul Distribution", fmt['section_title'])
        ws_dist.write(17, 0, "Haul Bracket", fmt['header'])
        ws_dist.write(17, 1, "Spell Count", fmt['header'])

        w_items = [
            ("0 Wickets", bowl_dist.get('zeroWickets', 0)),
            ("1 Wicket", bowl_dist.get('oneWicket', 0)),
            ("2 Wickets", bowl_dist.get('twoWickets', 0)),
            ("3 Wickets", bowl_dist.get('threeWickets', 0)),
            ("4 Wickets", bowl_dist.get('fourWickets', 0)),
            ("5+ Wickets", bowl_dist.get('fivePlusWickets', 0)),
        ]
        for idx, (lbl, cnt) in enumerate(w_items, start=18):
            ws_dist.write(idx, 0, lbl, fmt['cell'])
            ws_dist.write(idx, 1, cnt, fmt['cell_num'])

        chart_bowl_dist = wb.add_chart({'type': 'column'})
        chart_bowl_dist.add_series({
            'name': 'Spell Count',
            'categories': ['Milestones & Boundaries', 18, 0, 23, 0],
            'values':     ['Milestones & Boundaries', 18, 1, 23, 1],
            'fill':       {'color': COLORS['bowling_red']},
            'data_labels': {'value': True},
        })
        chart_bowl_dist.set_title({'name': f"Wicket Haul Distribution — {player_name}"})
        chart_bowl_dist.set_x_axis({'name': 'Wickets in Spell'})
        chart_bowl_dist.set_y_axis({'name': 'Spells'})
        chart_bowl_dist.set_legend({'none': True})
        chart_bowl_dist.set_size({'width': 560, 'height': 320})
        ws_dist.insert_chart('E18', chart_bowl_dist)

    ws_dist.set_column(0, 0, 30)
    ws_dist.set_column(1, 1, 14)
    ws_dist.set_column(2, 2, 16)

    # ── Sheet 6: Venue & Opposition Context + NATIVE CHARTS 9, 10, 11, 12 ──────
    ws_ctx = wb.add_worksheet('Venue & Opposition Context')
    ws_ctx.freeze_panes(2, 0)
    c_row = 0

    bat_venues = analytics.get('battingVenueBreakdown') or []
    if bat_venues:
        ws_ctx.write(c_row, 0, "Top Batting Grounds (Runs Scored & Strike Rate)", fmt['section_title'])
        c_row += 1
        v_cols = ['Venue Name', 'Appearances', 'Runs Scored', 'Average Runs', 'Strike Rate', 'Highest Score']
        for c_idx, h in enumerate(v_cols):
            ws_ctx.write(c_row, c_idx, h, fmt['header'])
        c_row += 1

        v_start = c_row
        for v in bat_venues[:10]:
            ws_ctx.write(c_row, 0, v.get('venue', '—'), fmt['cell'])
            ws_ctx.write(c_row, 1, v.get('appearances', 0), fmt['cell_num'])
            ws_ctx.write(c_row, 2, v.get('totalRuns', 0), fmt['cell_num'])
            ws_ctx.write(c_row, 3, v.get('averageRuns') or '—', fmt['cell_dec1'] if isinstance(v.get('averageRuns'), (int, float)) else fmt['cell_center'])
            ws_ctx.write(c_row, 4, v.get('strikeRate') or '—', fmt['cell_dec1'] if isinstance(v.get('strikeRate'), (int, float)) else fmt['cell_center'])
            ws_ctx.write(c_row, 5, v.get('highestScore', '—'), fmt['cell_center'])
            c_row += 1

        v_end = c_row - 1
        # CHART 9: Top Venues by Runs (Native Bar Chart)
        chart_v = wb.add_chart({'type': 'bar'})
        chart_v.add_series({
            'name': 'Runs Scored',
            'categories': ['Venue & Opposition Context', v_start, 0, v_end, 0],
            'values':     ['Venue & Opposition Context', v_start, 2, v_end, 2],
            'fill':       {'color': '#6366F1'},
        })
        chart_v.set_title({'name': f"Top Grounds by Runs Scored — {player_name}"})
        chart_v.set_x_axis({'name': 'Runs'})
        chart_v.set_size({'width': 580, 'height': 340})
        ws_ctx.insert_chart('H2', chart_v)
        c_row += 2

    bat_opps = analytics.get('battingOppositionBreakdown') or []
    if bat_opps:
        ws_ctx.write(c_row, 0, "Record Against Oppositions (Runs & Strike Rate)", fmt['section_title'])
        c_row += 1
        o_cols = ['Opposition', 'Appearances', 'Runs Scored', 'Average Runs', 'Strike Rate', 'Highest Score']
        for c_idx, h in enumerate(o_cols):
            ws_ctx.write(c_row, c_idx, h, fmt['header'])
        c_row += 1

        o_start = c_row
        for o in bat_opps[:10]:
            ws_ctx.write(c_row, 0, o.get('opposition', '—'), fmt['cell'])
            ws_ctx.write(c_row, 1, o.get('appearances', 0), fmt['cell_num'])
            ws_ctx.write(c_row, 2, o.get('totalRuns', 0), fmt['cell_num'])
            ws_ctx.write(c_row, 3, o.get('averageRuns') or '—', fmt['cell_dec1'] if isinstance(o.get('averageRuns'), (int, float)) else fmt['cell_center'])
            ws_ctx.write(c_row, 4, o.get('strikeRate') or '—', fmt['cell_dec1'] if isinstance(o.get('strikeRate'), (int, float)) else fmt['cell_center'])
            ws_ctx.write(c_row, 5, o.get('highestScore', '—'), fmt['cell_center'])
            c_row += 1

        o_end = c_row - 1
        # CHART 10: Record by Opposition (Native Bar Chart)
        chart_o = wb.add_chart({'type': 'bar'})
        chart_o.add_series({
            'name': 'Runs Scored',
            'categories': ['Venue & Opposition Context', o_start, 0, o_end, 0],
            'values':     ['Venue & Opposition Context', o_start, 2, o_end, 2],
            'fill':       {'color': '#3B82F6'},
        })
        chart_o.set_title({'name': f"Batting Record by Opposition — {player_name}"})
        chart_o.set_x_axis({'name': 'Runs'})
        chart_o.set_size({'width': 580, 'height': 340})
        ws_ctx.insert_chart(f'H{o_start + 1}', chart_o)
        c_row += 2

    # Bowling Venues & Oppositions (if bowler)
    bowl_venues = analytics.get('bowlingVenueBreakdown') or []
    if is_bowl and bowl_venues:
        ws_ctx.write(c_row, 0, "Top Bowling Grounds (Wickets Taken & Economy)", fmt['section_title'])
        c_row += 1
        bv_cols = ['Venue Name', 'Appearances', 'Wickets Taken', 'Runs Conceded', 'Economy Rate', 'Bowling Avg']
        for c_idx, h in enumerate(bv_cols):
            ws_ctx.write(c_row, c_idx, h, fmt['header'])
        c_row += 1

        bv_start = c_row
        for v in bowl_venues[:10]:
            ws_ctx.write(c_row, 0, v.get('venue', '—'), fmt['cell'])
            ws_ctx.write(c_row, 1, v.get('appearances', 0), fmt['cell_num'])
            ws_ctx.write(c_row, 2, v.get('totalWickets', 0), fmt['cell_num'])
            ws_ctx.write(c_row, 3, v.get('totalRunsConceded', 0), fmt['cell_num'])
            ws_ctx.write(c_row, 4, v.get('economyRate') or '—', fmt['cell_dec2'] if isinstance(v.get('economyRate'), (int, float)) else fmt['cell_center'])
            ws_ctx.write(c_row, 5, v.get('bowlingAverage') or '—', fmt['cell_dec2'] if isinstance(v.get('bowlingAverage'), (int, float)) else fmt['cell_center'])
            c_row += 1

        bv_end = c_row - 1
        # CHART 11: Top Venues by Wickets (Native Column Chart)
        chart_bv = wb.add_chart({'type': 'column'})
        chart_bv.add_series({
            'name': 'Wickets Taken',
            'categories': ['Venue & Opposition Context', bv_start, 0, bv_end, 0],
            'values':     ['Venue & Opposition Context', bv_start, 2, bv_end, 2],
            'fill':       {'color': COLORS['bowling_red']},
        })
        chart_bv.set_title({'name': f"Top Bowling Grounds by Wickets — {player_name}"})
        chart_bv.set_size({'width': 580, 'height': 340})
        ws_ctx.insert_chart(f'H{bv_start + 1}', chart_bv)

    ws_ctx.set_column(0, 0, 26)
    ws_ctx.set_column(1, 1, 13)
    ws_ctx.set_column(2, 2, 14)
    ws_ctx.set_column(3, 5, 14)

    wb.close()


# ═════════════════════════════════════════════════════════════════════════════
# 2. PLAYER COMPARISON WORKBOOK GENERATOR
# ═════════════════════════════════════════════════════════════════════════════

def generate_player_comparison_workbook(comparison, output_target, selected_format="ALL", selected_recent_n=15, selected_opposition="", selected_venue=""):
    """
    Generates a genuine .xlsx workbook containing head-to-head comparison metrics,
    side-by-side analysis, and genuine native Excel charts (Clustered Column, Trajectory Line).
    """
    wb = xlsxwriter.Workbook(output_target)
    fmt = create_formats(wb)

    p1_name = comparison.get('player1Name', 'Player 1')
    p2_name = comparison.get('player2Name', 'Player 2')
    scope = comparison.get('comparisonScope', 'BOTH')
    bat_comp = comparison.get('battingComparison') or {}
    bowl_comp = comparison.get('bowlingComparison') or {}
    details = comparison.get('metricAdvantagesDetailed') or {}
    analytical_sum = comparison.get('analyticalSummary') or {}

    p1_obj = comparison.get('player1') or comparison.get('player1Analytics') or {}
    p2_obj = comparison.get('player2') or comparison.get('player2Analytics') or {}

    # ── Sheet 1: Comparison Summary ──────────────────────────────────────────
    ws_sum = wb.add_worksheet('Comparison Summary')
    ws_sum.freeze_panes(1, 0)
    ws_sum.write(0, 0, f"CricketAI — Head-to-Head Comparison: {p1_name} vs {p2_name}", fmt['title'])
    ws_sum.write(1, 0, "Verified format-specific comparative analytics & AI evaluation", fmt['meta_label'])

    ws_sum.write(3, 0, "Comparison Parameters & Environment", fmt['section_title'])
    meta_rows = [
        ("Player 1", p1_name),
        ("Player 2", p2_name),
        ("Format Scope", selected_format),
        ("Comparison Discipline Scope", scope),
        ("Recent Match Window", f"Last {selected_recent_n} appearances"),
        ("Active Opposition Filter", selected_opposition if selected_opposition else "All Oppositions (Unrestricted)"),
        ("Active Venue Filter", selected_venue if selected_venue else "All Venues (Unrestricted)"),
        ("Export Timestamp", datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
    ]
    for r_idx, (k, v) in enumerate(meta_rows, start=4):
        ws_sum.write(r_idx, 0, k, fmt['meta_label'])
        ws_sum.write(r_idx, 1, v, fmt['meta_val'])

    curr_row = 13
    if comparison.get('writtenInterpretation') or analytical_sum.get('verdict'):
        ws_sum.write(curr_row, 0, "Evidence-Based Analytical Synthesis", fmt['section_title'])
        curr_row += 1
        verdict = comparison.get('writtenInterpretation') or analytical_sum.get('verdict')
        ws_sum.write(curr_row, 0, verdict, fmt['cell'])
        curr_row += 2

    ws_sum.set_column(0, 0, 28)
    ws_sum.set_column(1, 1, 45)

    # ── Sheet 2: Side by Side Metric Comparison + NATIVE CHART 1 ──────────────
    ws_side = wb.add_worksheet('Side by Side')
    ws_side.freeze_panes(1, 0)

    side_cols = [
        ('Discipline', 14), ('Metric', 24), (p1_name, 18), (p2_name, 18),
        ('Absolute Diff', 15), ('% Diff', 12), ('Advantage', 16), ('Criterion', 18), ('Interpretation', 38)
    ]
    for c_idx, (h, w) in enumerate(side_cols):
        ws_side.write(0, c_idx, h, fmt['header'])
        ws_side.set_column(c_idx, c_idx, w)

    metric_keys = [
        ('runs', ['totalRuns', 'bat_total_runs'], 'Total Runs Scored', 'Batting',
         lambda: (bat_comp.get('totalRuns1'), bat_comp.get('totalRuns2'), bat_comp.get('diffTotalRuns'), 'Higher is Better')),
        ('runsPerInnings', ['rpi', 'bat_runs_per_innings'], 'Runs per Innings', 'Batting',
         lambda: (bat_comp.get('runsPerInnings1'), bat_comp.get('runsPerInnings2'), bat_comp.get('diffRunsPerInnings'), 'Higher is Better')),
        ('battingStrikeRate', ['strikeRate', 'bat_strike_rate'], 'Batting Strike Rate', 'Batting',
         lambda: (bat_comp.get('strikeRate1'), bat_comp.get('strikeRate2'), bat_comp.get('diffStrikeRate'), 'Higher is Better')),
        ('boundaryPercentage', ['boundaryRunPercentage', 'bat_boundary_pct'], 'Boundary Run %', 'Batting',
         lambda: (bat_comp.get('boundaryPercentage1'), bat_comp.get('boundaryPercentage2'), bat_comp.get('diffBoundaryPercentage'), 'Higher is Better')),
        ('fours', ['foursCount'], 'Total Fours (4s)', 'Batting',
         lambda: (bat_comp.get('fours1'), bat_comp.get('fours2'), bat_comp.get('diffFours'), 'Higher is Better')),
        ('sixes', ['sixesCount'], 'Total Sixes (6s)', 'Batting',
         lambda: (bat_comp.get('sixes1'), bat_comp.get('sixes2'), bat_comp.get('diffSixes'), 'Higher is Better')),
        ('consistency', ['coefficientOfVariation', 'bat_consistency'], 'Consistency (CV%)', 'Batting Consistency',
         lambda: (bat_comp.get('scoringConsistency1'), bat_comp.get('scoringConsistency2'), bat_comp.get('diffScoringConsistency'), 'Lower Variance')),
        ('wickets', ['totalWickets', 'bowl_total_wickets'], 'Total Wickets Taken', 'Bowling',
         lambda: (bowl_comp.get('totalWickets1'), bowl_comp.get('totalWickets2'), bowl_comp.get('diffTotalWickets'), 'Higher is Better')),
        ('economyRate', ['economy', 'bowl_economy_rate'], 'Economy Rate', 'Bowling',
         lambda: (bowl_comp.get('economyRate1'), bowl_comp.get('economyRate2'), bowl_comp.get('diffEconomyRate'), 'Lower is Better')),
        ('bowlingAverage', ['bowlAvg', 'bowl_average'], 'Bowling Average', 'Bowling',
         lambda: (bowl_comp.get('bowlingAverage1'), bowl_comp.get('bowlingAverage2'), bowl_comp.get('diffBowlingAverage'), 'Lower is Better')),
        ('bowlingStrikeRate', ['bowlSr', 'bowl_strike_rate'], 'Bowling Strike Rate', 'Bowling',
         lambda: (bowl_comp.get('bowlingStrikeRate1'), bowl_comp.get('bowlingStrikeRate2'), bowl_comp.get('diffBowlingStrikeRate'), 'Lower is Better')),
    ]

    r_idx = 1
    processed = set()
    for key, alts, lbl, disc, fb in metric_keys:
        d = details.get(key)
        if not d:
            for ak in alts:
                if ak in details:
                    d = details[ak]
                    processed.add(ak)
                    break
        else:
            processed.add(key)

        if d:
            v1 = d.get('value1')
            v2 = d.get('value2')
            diff = d.get('absoluteDiff') or d.get('difference')
            pct = d.get('percentageDifference')
            adv = p1_name if d.get('advantage') == 'player1' else (p2_name if d.get('advantage') == 'player2' else ('Tie' if d.get('advantage') == 'tie' else 'N/A'))
            crit = 'Lower is Better' if d.get('criterion') == 'LOWER_IS_BETTER' else ('Lower Variance' if d.get('criterion') == 'LOWER_VARIABILITY' else 'Higher is Better')
            interp = d.get('interpretation') or d.get('formulaDescription') or '—'

            ws_side.write(r_idx, 0, disc, fmt['cell'])
            ws_side.write(r_idx, 1, d.get('displayName', lbl), fmt['cell'])
            ws_side.write(r_idx, 2, v1 if v1 is not None else '—', fmt['cell_num'] if isinstance(v1, (int, float)) else fmt['cell'])
            ws_side.write(r_idx, 3, v2 if v2 is not None else '—', fmt['cell_num'] if isinstance(v2, (int, float)) else fmt['cell'])
            ws_side.write(r_idx, 4, abs(diff) if isinstance(diff, (int, float)) else '—', fmt['cell_num'] if isinstance(diff, (int, float)) else fmt['cell'])
            ws_side.write(r_idx, 5, f"{pct}%" if pct is not None else '—', fmt['cell_pct'])
            ws_side.write(r_idx, 6, adv, fmt['cell_center'])
            ws_side.write(r_idx, 7, crit, fmt['cell'])
            ws_side.write(r_idx, 8, interp, fmt['cell'])
            r_idx += 1
        elif fb:
            v1, v2, diff, crit = fb()
            if v1 is not None or v2 is not None:
                adv = 'Tie'
                if v1 is not None and v2 is not None:
                    if 'Lower' in crit:
                        adv = p1_name if v1 < v2 else (p2_name if v2 < v1 else 'Tie')
                    else:
                        adv = p1_name if v1 > v2 else (p2_name if v2 > v1 else 'Tie')
                elif v1 is not None: adv = p1_name
                elif v2 is not None: adv = p2_name

                ws_side.write(r_idx, 0, disc, fmt['cell'])
                ws_side.write(r_idx, 1, lbl, fmt['cell'])
                ws_side.write(r_idx, 2, v1 if v1 is not None else '—', fmt['cell_num'] if isinstance(v1, (int, float)) else fmt['cell'])
                ws_side.write(r_idx, 3, v2 if v2 is not None else '—', fmt['cell_num'] if isinstance(v2, (int, float)) else fmt['cell'])
                ws_side.write(r_idx, 4, abs(diff) if isinstance(diff, (int, float)) else '—', fmt['cell_num'] if isinstance(diff, (int, float)) else fmt['cell'])
                ws_side.write(r_idx, 5, '—', fmt['cell_pct'])
                ws_side.write(r_idx, 6, adv, fmt['cell_center'])
                ws_side.write(r_idx, 7, crit, fmt['cell'])
                ws_side.write(r_idx, 8, 'Direct career comparison', fmt['cell'])
                r_idx += 1

    # ── Sheet 3: Player 1 Statistics ──────────────────────────────────────────
    ws_p1 = wb.add_worksheet(sanitize_sheet_name(f"{p1_name} Stats"))
    populate_player_stats_sheet(ws_p1, p1_obj, p1_name, selected_format, fmt)

    # ── Sheet 4: Player 2 Statistics ──────────────────────────────────────────
    ws_p2 = wb.add_worksheet(sanitize_sheet_name(f"{p2_name} Stats"))
    populate_player_stats_sheet(ws_p2, p2_obj, p2_name, selected_format, fmt)

    # ── Sheet 5: Recent Performance Match Logs ────────────────────────────────
    ws_rec = wb.add_worksheet('Recent Performance')
    ws_rec.freeze_panes(1, 0)
    rec_cols = [
        ('Player', 16), ('Innings/Spell #', 15), ('Date', 13), ('Format', 10),
        ('Opposition', 16), ('Venue', 24), ('Runs', 10), ('Balls Faced', 12),
        ('Strike Rate', 12), ('Overs Bowled', 13), ('Runs Conceded', 14), ('Wickets', 10), ('Economy', 12)
    ]
    for c_idx, (h, w) in enumerate(rec_cols):
        ws_rec.write(0, c_idx, h, fmt['header'])
        ws_rec.set_column(c_idx, c_idx, w)

    rec_r = 1
    # P1 Recent
    for idx, m in enumerate((p1_obj.get('recentBattingForm') or [])[-selected_recent_n:]):
        ws_rec.write(rec_r, 0, p1_name, fmt['cell'])
        ws_rec.write(rec_r, 1, f"Bat Inn {idx + 1}", fmt['cell_center'])
        ws_rec.write(rec_r, 2, m.get('date', '—'), fmt['cell_center'])
        ws_rec.write(rec_r, 3, m.get('format', selected_format), fmt['cell_center'])
        ws_rec.write(rec_r, 4, m.get('opposition', '—'), fmt['cell'])
        ws_rec.write(rec_r, 5, m.get('venue', '—'), fmt['cell'])
        ws_rec.write(rec_r, 6, m.get('runs', 0), fmt['cell_num'])
        ws_rec.write(rec_r, 7, m.get('ballsFaced') or m.get('balls', 0), fmt['cell_num'])
        ws_rec.write(rec_r, 8, m.get('strikeRate', '—'), fmt['cell_dec1'] if isinstance(m.get('strikeRate'), (int, float)) else fmt['cell_center'])
        ws_rec.write(rec_r, 9, '—', fmt['cell_center'])
        ws_rec.write(rec_r, 10, '—', fmt['cell_center'])
        ws_rec.write(rec_r, 11, '—', fmt['cell_center'])
        ws_rec.write(rec_r, 12, '—', fmt['cell_center'])
        rec_r += 1

    # P2 Recent
    for idx, m in enumerate((p2_obj.get('recentBattingForm') or [])[-selected_recent_n:]):
        ws_rec.write(rec_r, 0, p2_name, fmt['cell'])
        ws_rec.write(rec_r, 1, f"Bat Inn {idx + 1}", fmt['cell_center'])
        ws_rec.write(rec_r, 2, m.get('date', '—'), fmt['cell_center'])
        ws_rec.write(rec_r, 3, m.get('format', selected_format), fmt['cell_center'])
        ws_rec.write(rec_r, 4, m.get('opposition', '—'), fmt['cell'])
        ws_rec.write(rec_r, 5, m.get('venue', '—'), fmt['cell'])
        ws_rec.write(rec_r, 6, m.get('runs', 0), fmt['cell_num'])
        ws_rec.write(rec_r, 7, m.get('ballsFaced') or m.get('balls', 0), fmt['cell_num'])
        ws_rec.write(rec_r, 8, m.get('strikeRate', '—'), fmt['cell_dec1'] if isinstance(m.get('strikeRate'), (int, float)) else fmt['cell_center'])
        ws_rec.write(rec_r, 9, '—', fmt['cell_center'])
        ws_rec.write(rec_r, 10, '—', fmt['cell_center'])
        ws_rec.write(rec_r, 11, '—', fmt['cell_center'])
        ws_rec.write(rec_r, 12, '—', fmt['cell_center'])
        rec_r += 1

    # ── Sheet 6: Chart Data + NATIVE COMPARISON CHARTS 1, 2, 3 ────────────────
    ws_ch = wb.add_worksheet('Chart Data')
    ws_ch.freeze_panes(2, 0)

    # Section A: Batting Overview Clustered Chart Data
    ws_ch.write(0, 0, "Batting Overview Comparison Chart Dataset", fmt['section_title'])
    ws_ch.write(1, 0, "Comparison Metric", fmt['header'])
    ws_ch.write(1, 1, p1_name, fmt['header'])
    ws_ch.write(1, 2, p2_name, fmt['header'])

    bat_metrics = [
        ("Total Runs", bat_comp.get('totalRuns1', 0), bat_comp.get('totalRuns2', 0)),
        ("Strike Rate", bat_comp.get('strikeRate1', 0), bat_comp.get('strikeRate2', 0)),
        ("Runs / Inn", bat_comp.get('runsPerInnings1', 0), bat_comp.get('runsPerInnings2', 0)),
        ("Boundary Run %", bat_comp.get('boundaryPercentage1', 0), bat_comp.get('boundaryPercentage2', 0)),
        ("Fours (4s)", bat_comp.get('fours1', 0), bat_comp.get('fours2', 0)),
        ("Sixes (6s)", bat_comp.get('sixes1', 0), bat_comp.get('sixes2', 0)),
    ]
    for idx, (m_lbl, v1, v2) in enumerate(bat_metrics, start=2):
        ws_ch.write(idx, 0, m_lbl, fmt['cell'])
        ws_ch.write(idx, 1, v1 if v1 is not None else 0, fmt['cell_num'])
        ws_ch.write(idx, 2, v2 if v2 is not None else 0, fmt['cell_num'])

    # CHART 1: Batting Overview (Native Clustered Column Chart)
    chart_bat_comp = wb.add_chart({'type': 'column'})
    chart_bat_comp.add_series({
        'name': ['Chart Data', 1, 1],
        'categories': ['Chart Data', 2, 0, 7, 0],
        'values':     ['Chart Data', 2, 1, 7, 1],
        'fill':       {'color': COLORS['p1_line']},
    })
    chart_bat_comp.add_series({
        'name': ['Chart Data', 1, 2],
        'categories': ['Chart Data', 2, 0, 7, 0],
        'values':     ['Chart Data', 2, 2, 7, 2],
        'fill':       {'color': COLORS['p2_line']},
    })
    chart_bat_comp.set_title({'name': f"Batting Overview Comparison — {p1_name} vs {p2_name}"})
    chart_bat_comp.set_x_axis({'name': 'Metric'})
    chart_bat_comp.set_size({'width': 600, 'height': 340})
    ws_ch.insert_chart('E2', chart_bat_comp)

    # Section B: Bowling Overview Clustered Chart Data
    ws_ch.write(9, 0, "Bowling Overview Comparison Chart Dataset", fmt['section_title'])
    ws_ch.write(10, 0, "Comparison Metric", fmt['header'])
    ws_ch.write(10, 1, p1_name, fmt['header'])
    ws_ch.write(10, 2, p2_name, fmt['header'])

    bowl_metrics = [
        ("Total Wickets", bowl_comp.get('totalWickets1', 0), bowl_comp.get('totalWickets2', 0)),
        ("Overs Bowled", bowl_comp.get('totalOvers1', 0), bowl_comp.get('totalOvers2', 0)),
        ("Economy Rate", bowl_comp.get('economyRate1', 0), bowl_comp.get('economyRate2', 0)),
        ("Bowling Average", bowl_comp.get('bowlingAverage1', 0), bowl_comp.get('bowlingAverage2', 0)),
        ("Bowling Strike Rate", bowl_comp.get('bowlingStrikeRate1', 0), bowl_comp.get('bowlingStrikeRate2', 0)),
    ]
    for idx, (m_lbl, v1, v2) in enumerate(bowl_metrics, start=11):
        ws_ch.write(idx, 0, m_lbl, fmt['cell'])
        ws_ch.write(idx, 1, v1 if v1 is not None else 0, fmt['cell_num'])
        ws_ch.write(idx, 2, v2 if v2 is not None else 0, fmt['cell_num'])

    # CHART 2: Bowling Overview (Native Clustered Column Chart)
    chart_bowl_comp = wb.add_chart({'type': 'column'})
    chart_bowl_comp.add_series({
        'name': ['Chart Data', 10, 1],
        'categories': ['Chart Data', 11, 0, 15, 0],
        'values':     ['Chart Data', 11, 1, 15, 1],
        'fill':       {'color': COLORS['p1_line']},
    })
    chart_bowl_comp.add_series({
        'name': ['Chart Data', 10, 2],
        'categories': ['Chart Data', 11, 0, 15, 0],
        'values':     ['Chart Data', 11, 2, 15, 2],
        'fill':       {'color': COLORS['p2_line']},
    })
    chart_bowl_comp.set_title({'name': f"Bowling Overview Comparison — {p1_name} vs {p2_name}"})
    chart_bowl_comp.set_x_axis({'name': 'Metric'})
    chart_bowl_comp.set_size({'width': 600, 'height': 340})
    ws_ch.insert_chart('E11', chart_bowl_comp)

    # Section C: Recent Form Trajectory Comparison
    p1_rec_bat = (p1_obj.get('recentBattingForm') or [])[-selected_recent_n:]
    p2_rec_bat = (p2_obj.get('recentBattingForm') or [])[-selected_recent_n:]
    max_len = max(len(p1_rec_bat), len(p2_rec_bat))

    if max_len > 0:
        c_start = 18
        ws_ch.write(c_start, 0, "Recent Form Trajectory Trendline Dataset", fmt['section_title'])
        ws_ch.write(c_start + 1, 0, "Appearance Index", fmt['header'])
        ws_ch.write(c_start + 1, 1, f"{p1_name} Runs", fmt['header'])
        ws_ch.write(c_start + 1, 2, f"{p2_name} Runs", fmt['header'])

        for idx in range(max_len):
            r_num = c_start + 2 + idx
            ws_ch.write(r_num, 0, f"Match {idx + 1}", fmt['cell_center'])
            v1 = p1_rec_bat[idx].get('runs', 0) if idx < len(p1_rec_bat) else 0
            v2 = p2_rec_bat[idx].get('runs', 0) if idx < len(p2_rec_bat) else 0
            ws_ch.write(r_num, 1, v1, fmt['cell_num'])
            ws_ch.write(r_num, 2, v2, fmt['cell_num'])

        c_end = c_start + 1 + max_len

        # CHART 3: Recent Form Trajectory (Native Multi-line Chart)
        chart_traj = wb.add_chart({'type': 'line'})
        chart_traj.add_series({
            'name': ['Chart Data', c_start + 1, 1],
            'categories': ['Chart Data', c_start + 2, 0, c_end, 0],
            'values':     ['Chart Data', c_start + 2, 1, c_end, 1],
            'line':       {'color': COLORS['p1_line'], 'width': 2.5},
            'marker':     {'type': 'circle', 'size': 6, 'border': {'color': COLORS['p1_line']}, 'fill': {'color': '#FFFFFF'}},
        })
        chart_traj.add_series({
            'name': ['Chart Data', c_start + 1, 2],
            'categories': ['Chart Data', c_start + 2, 0, c_end, 0],
            'values':     ['Chart Data', c_start + 2, 2, c_end, 2],
            'line':       {'color': COLORS['p2_line'], 'width': 2.5},
            'marker':     {'type': 'square', 'size': 6, 'border': {'color': COLORS['p2_line']}, 'fill': {'color': '#FFFFFF'}},
        })
        chart_traj.set_title({'name': f"Recent Form Trajectory Comparison — {p1_name} vs {p2_name}"})
        chart_traj.set_x_axis({'name': 'Recent Matches (Chronological)'})
        chart_traj.set_y_axis({'name': 'Runs Scored'})
        chart_traj.set_size({'width': 600, 'height': 340})
        ws_ch.insert_chart(f'E{c_start + 2}', chart_traj)

    ws_ch.set_column(0, 0, 24)
    ws_ch.set_column(1, 2, 18)

    # ── Sheet 7: Context Match Conditions ─────────────────────────────────────
    ws_ctx = wb.add_worksheet('Context Match Conditions')
    ws_ctx.freeze_panes(1, 0)
    ctx_info = comparison.get('contextInfo') or {}

    ws_ctx.write(0, 0, "Active Environmental & Context Filters", fmt['section_title'])
    ws_ctx.write(1, 0, "Filter Category", fmt['header'])
    ws_ctx.write(1, 1, "Applied Condition", fmt['header'])

    ws_ctx.write(2, 0, "Opposition Filter", fmt['cell'])
    ws_ctx.write(2, 1, selected_opposition if selected_opposition else "All Oppositions", fmt['cell'])
    ws_ctx.write(3, 0, "Venue Filter", fmt['cell'])
    ws_ctx.write(3, 1, selected_venue if selected_venue else "All Grounds", fmt['cell'])
    ws_ctx.write(4, 0, "Sample Size Advisory", fmt['cell'])
    ws_ctx.write(4, 1, ctx_info.get('contextualSampleSizeWarning') or "Sample size verified sufficient for comparative evaluation.", fmt['cell'])

    common_opps = ", ".join(ctx_info.get('commonOppositions') or [])
    common_vens = ", ".join((ctx_info.get('commonVenues') or [])[:8])
    ws_ctx.write(5, 0, "Common Head-to-Head Opponents", fmt['cell'])
    ws_ctx.write(5, 1, common_opps if common_opps else "No common opponents recorded", fmt['cell'])
    ws_ctx.write(6, 0, "Shared International Venues", fmt['cell'])
    ws_ctx.write(6, 1, common_vens if common_vens else "No common venues recorded", fmt['cell'])

    ws_ctx.set_column(0, 0, 28)
    ws_ctx.set_column(1, 1, 55)

    wb.close()


def populate_player_stats_sheet(ws, p_analytics, p_name, fmt_name, fmt):
    ws.freeze_panes(2, 0)
    ws.write(0, 0, f"Detailed Career Profile: {p_name} ({fmt_name})", fmt['section_title'])
    headers = ['Metric Category', 'Metric Name', 'Recorded Value', 'Technical Description']
    for c_idx, h in enumerate(headers):
        ws.write(1, c_idx, h, fmt['header'])

    ws.set_column(0, 0, 16)
    ws.set_column(1, 1, 24)
    ws.set_column(2, 2, 18)
    ws.set_column(3, 3, 40)

    if not p_analytics:
        ws.write(2, 0, "General", fmt['cell'])
        ws.write(2, 1, "Status", fmt['cell'])
        ws.write(2, 2, "No records", fmt['cell'])
        ws.write(2, 3, "No historical data recorded in dataset", fmt['cell'])
        return

    bat = p_analytics.get('battingSummary') or {}
    bowl = p_analytics.get('bowlingSummary') or {}

    rows = [
        ("General", "Detected Role", p_analytics.get('role', 'UNKNOWN'), "Classified from career appearance distribution"),
        ("General", "Total Batting Innings", p_analytics.get('totalBattingInnings', 0), "Historical batting appearances"),
        ("General", "Total Bowling Innings", p_analytics.get('totalBowlingInnings', 0), "Historical bowling appearances"),
    ]

    if bat:
        rows.extend([
            ("Batting", "Total Runs", bat.get('totalRuns', 0), "Aggregate format runs scored"),
            ("Batting", "Runs per Innings", round(bat.get('runsPerInnings', 0), 1) if bat.get('runsPerInnings') is not None else "—", "Total runs / innings"),
            ("Batting", "Strike Rate", round(bat.get('strikeRate', 0), 1) if bat.get('strikeRate') is not None else "—", "Runs scored per 100 balls"),
            ("Batting", "Fours (4s)", bat.get('fours', 0), "Total boundary fours"),
            ("Batting", "Sixes (6s)", bat.get('sixes', 0), "Total boundary sixes"),
            ("Batting", "Boundary Run %", f"{bat.get('boundaryPercentage', 0)}%", "% of total runs from boundaries"),
            ("Batting", "Highest Score", f"{bat.get('highestScore', '—')}{'*' if bat.get('highestScoreNotOut') else ''}", "Highest format innings score"),
            ("Batting", "50s / 100s", f"{bat.get('fifties', 0)} / {bat.get('hundreds', 0)}", "Milestones achieved"),
            ("Batting", "Consistency (CV%)", f"{bat.get('coefficientOfVariation', '—')}%", "Relative variability σ/mean"),
        ])

    if bowl:
        rows.extend([
            ("Bowling", "Total Wickets", bowl.get('totalWickets', 0), "Aggregate format wickets taken"),
            ("Bowling", "Overs Bowled", bowl.get('totalOvers', 0), "Total overs bowled"),
            ("Bowling", "Runs Conceded", bowl.get('runsConceded', 0), "Total runs conceded"),
            ("Bowling", "Economy Rate", round(bowl.get('economyRate', 0), 2) if bowl.get('economyRate') is not None else "—", "Runs conceded per over"),
            ("Bowling", "Bowling Average", round(bowl.get('bowlingAverage', 0), 2) if bowl.get('bowlingAverage') is not None else "N/A", "Runs conceded per wicket"),
            ("Bowling", "Bowling Strike Rate", f"{bowl.get('bowlingStrikeRate', '—')} b/w", "Balls bowled per wicket"),
            ("Bowling", "4W Hauls", bowl.get('fourWickets', 0), "Spells with 4 wickets"),
            ("Bowling", "5W Hauls", bowl.get('fiveWickets', 0), "Spells with 5+ wickets"),
        ])

    for r_idx, (cat, name, val, desc) in enumerate(rows, start=2):
        ws.write(r_idx, 0, cat, fmt['cell'])
        ws.write(r_idx, 1, name, fmt['cell'])
        ws.write(r_idx, 2, val, fmt['cell_num'] if isinstance(val, (int, float)) else fmt['cell'])
        ws.write(r_idx, 3, desc, fmt['cell'])


# ─── CLI Entrypoint for Testing and ProcessBuilder Invocation ─────────────────

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="CricketAI Native Excel Report Generator")
    parser.add_argument('--type', required=True, choices=['player', 'compare'], help="Report type")
    parser.add_argument('--input', required=True, help="Input JSON file path")
    parser.add_argument('--output', required=True, help="Output .xlsx file path")
    parser.add_argument('--format', default="ALL", help="Format filter")
    parser.add_argument('--recentN', type=int, default=15, help="Recent match count")
    parser.add_argument('--opposition', default="", help="Opposition filter")
    parser.add_argument('--venue', default="", help="Venue filter")

    args = parser.parse_args()

    with open(args.input, 'r', encoding='utf-8') as f:
        data = json.load(f)

    if args.type == 'player':
        analytics_obj = data.get('analytics', data) if isinstance(data, dict) and 'analytics' in data else data
        generate_player_analysis_workbook(analytics_obj, args.output, selected_format=args.format, selected_recent_n=args.recentN)
    else:
        comparison_obj = data.get('comparison', data) if isinstance(data, dict) and 'comparison' in data else data
        generate_player_comparison_workbook(
            comparison_obj, args.output,
            selected_format=args.format,
            selected_recent_n=args.recentN,
            selected_opposition=args.opposition,
            selected_venue=args.venue
        )
    print(f"SUCCESS: Generated {args.output}")
