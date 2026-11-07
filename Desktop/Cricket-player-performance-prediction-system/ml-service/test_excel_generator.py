"""
test_excel_generator.py

Validates excel_report_generator.py by generating realistic Player Analysis and
Player Comparison workbooks and verifying OpenXML DrawingML charts.
"""

import os
import zipfile
from api.excel_report_generator import (
    generate_player_analysis_workbook,
    generate_player_comparison_workbook
)

mock_batsman = {
    'playerName': 'V Kohli',
    'role': 'BATSMAN',
    'totalBattingInnings': 280,
    'totalBowlingInnings': 4,
    'battingSummary': {
        'innings': 280, 'totalRuns': 13848, 'runsPerInnings': 49.46, 'strikeRate': 93.5,
        'fifties': 72, 'hundreds': 50, 'highestScore': 183, 'highestScoreNotOut': False,
        'fours': 1280, 'sixes': 152, 'boundaryPercentage': 43.8, 'coefficientOfVariation': 71.2
    },
    'bowlingSummary': None,
    'recentBattingForm': [
        {'matchId': '101', 'date': '2024-01-15', 'format': 'ODI', 'opposition': 'South Africa', 'venue': 'Newlands', 'runs': 76, 'ballsFaced': 82, 'strikeRate': 92.7, 'fours': 7, 'sixes': 1, 'notOut': False},
        {'matchId': '102', 'date': '2024-02-02', 'format': 'ODI', 'opposition': 'England', 'venue': 'Hyderabad', 'runs': 85, 'ballsFaced': 95, 'strikeRate': 89.5, 'fours': 9, 'sixes': 0, 'notOut': False},
        {'matchId': '103', 'date': '2024-02-18', 'format': 'ODI', 'opposition': 'England', 'venue': 'Rajkot', 'runs': 112, 'ballsFaced': 104, 'strikeRate': 107.7, 'fours': 11, 'sixes': 2, 'notOut': True},
        {'matchId': '104', 'date': '2024-03-05', 'format': 'ODI', 'opposition': 'Australia', 'venue': 'MCG', 'runs': 54, 'ballsFaced': 60, 'strikeRate': 90.0, 'fours': 5, 'sixes': 1, 'notOut': False},
        {'matchId': '105', 'date': '2024-03-20', 'format': 'ODI', 'opposition': 'Australia', 'venue': 'SCG', 'runs': 101, 'ballsFaced': 98, 'strikeRate': 103.1, 'fours': 8, 'sixes': 3, 'notOut': False},
    ],
    'recentBowlingForm': [],
    'scoreDistribution': {
        'ducks': 12, 'underTwenty': 45, 'twenties': 55, 'thirtiesToForties': 46, 'fifties': 72, 'hundreds': 50
    },
    'recentFormSummary': {
        'runTrendDirection': 'UPWARD', 'meanRuns': 85.6, 'rollingBattingAverage': 85.6
    },
    'battingVenueBreakdown': [
        {'venue': 'Melbourne Cricket Ground', 'appearances': 14, 'totalRuns': 820, 'averageRuns': 58.6, 'strikeRate': 94.2, 'highestScore': 183},
        {'venue': 'Eden Gardens', 'appearances': 18, 'totalRuns': 950, 'averageRuns': 52.8, 'strikeRate': 91.5, 'highestScore': 136},
    ],
    'battingOppositionBreakdown': [
        {'opposition': 'Australia', 'appearances': 45, 'totalRuns': 2420, 'averageRuns': 53.8, 'strikeRate': 95.1, 'highestScore': 183},
        {'opposition': 'England', 'appearances': 38, 'totalRuns': 1980, 'averageRuns': 52.1, 'strikeRate': 89.8, 'highestScore': 122},
    ],
}

mock_comparison = {
    'player1Name': 'V Kohli',
    'player2Name': 'Babar Azam',
    'comparisonScope': 'BOTH_PLAYERS_BATTING',
    'player1': mock_batsman,
    'player2': {
        'playerName': 'Babar Azam',
        'role': 'BATSMAN',
        'totalBattingInnings': 115,
        'totalBowlingInnings': 0,
        'battingSummary': {
            'innings': 115, 'totalRuns': 5729, 'runsPerInnings': 49.82, 'strikeRate': 88.6,
            'fifties': 32, 'hundreds': 19, 'highestScore': 158, 'highestScoreNotOut': False,
            'fours': 540, 'sixes': 55, 'boundaryPercentage': 38.4, 'coefficientOfVariation': 64.4
        },
        'recentBattingForm': [
            {'matchId': '201', 'date': '2024-01-10', 'format': 'ODI', 'opposition': 'NZ', 'venue': 'Auckland', 'runs': 57, 'ballsFaced': 65, 'strikeRate': 87.7, 'fours': 6, 'sixes': 0, 'notOut': False},
            {'matchId': '202', 'date': '2024-01-25', 'format': 'ODI', 'opposition': 'NZ', 'venue': 'Hamilton', 'runs': 64, 'ballsFaced': 72, 'strikeRate': 88.9, 'fours': 7, 'sixes': 1, 'notOut': False},
            {'matchId': '203', 'date': '2024-02-12', 'format': 'ODI', 'opposition': 'Australia', 'venue': 'Perth', 'runs': 82, 'ballsFaced': 90, 'strikeRate': 91.1, 'fours': 8, 'sixes': 1, 'notOut': False},
        ],
    },
    'battingComparison': {
        'innings1': 280, 'innings2': 115,
        'totalRuns1': 13848, 'totalRuns2': 5729, 'diffTotalRuns': 8119,
        'runsPerInnings1': 49.46, 'runsPerInnings2': 49.82, 'diffRunsPerInnings': -0.36,
        'strikeRate1': 93.5, 'strikeRate2': 88.6, 'diffStrikeRate': 4.9,
        'fours1': 1280, 'fours2': 540, 'diffFours': 740,
        'sixes1': 152, 'sixes2': 55, 'diffSixes': 97,
        'boundaryPercentage1': 43.8, 'boundaryPercentage2': 38.4, 'diffBoundaryPercentage': 5.4,
        'scoringConsistency1': 71.2, 'scoringConsistency2': 64.4, 'diffScoringConsistency': 6.8,
    },
    'bowlingComparison': None,
    'contextInfo': {
        'commonOppositions': ['Australia', 'England', 'New Zealand', 'South Africa'],
        'commonVenues': ['Melbourne Cricket Ground', 'Sydney Cricket Ground', "Lord's"],
    },
    'writtenInterpretation': 'V Kohli maintains a substantial career longevity and scoring acceleration advantage (SR +4.9), while Babar Azam demonstrates competitive consistency.'
}

out_player = "test_player_analysis_native.xlsx"
out_comp = "test_player_comp_native.xlsx"

print("--- 1. Testing Player Analysis Generator ---")
generate_player_analysis_workbook(mock_batsman, out_player, selected_format="ODI", selected_recent_n=5)
print(f"Generated {out_player} ({os.path.getsize(out_player)} bytes)")

with zipfile.ZipFile(out_player) as z:
    names = z.namelist()
    charts = [n for n in names if 'xl/charts/' in n]
    print(f"Native Charts found in Player Analysis workbook: {len(charts)} ({charts})")
    assert len(charts) >= 4, f"Expected at least 4 native charts, found {len(charts)}"

print("\n--- 2. Testing Player Comparison Generator ---")
generate_player_comparison_workbook(mock_comparison, out_comp, selected_format="ODI", selected_recent_n=5)
print(f"Generated {out_comp} ({os.path.getsize(out_comp)} bytes)")

with zipfile.ZipFile(out_comp) as z:
    names = z.namelist()
    charts = [n for n in names if 'xl/charts/' in n]
    print(f"Native Charts found in Player Comparison workbook: {len(charts)} ({charts})")
    assert len(charts) >= 2, f"Expected at least 2 native charts, found {len(charts)}"

out_bowler = "test_bowler_analysis_native.xlsx"
mock_bowler = {
    'playerName': 'JJ Bumrah',
    'role': 'BOWLER',
    'totalBattingInnings': 25,
    'totalBowlingInnings': 120,
    'battingSummary': None,
    'bowlingSummary': {
        'totalWickets': 149, 'totalBalls': 6120, 'totalRunsConceded': 4680,
        'bowlingAverage': 23.3, 'economyRate': 4.59, 'bowlingStrikeRate': 30.5,
        'threeWicketHauls': 18, 'fourWicketHauls': 6, 'fiveWicketHauls': 2, 'bestBowling': '6/19'
    },
    'recentBattingForm': [],
    'recentBowlingForm': [
        {'matchId': '201', 'date': '2024-01-10', 'format': 'ODI', 'opposition': 'South Africa', 'venue': 'Cape Town', 'overs': '10.0', 'wickets': 3, 'runsConceded': 35, 'economy': 3.5, 'dots': 38},
        {'matchId': '202', 'date': '2024-01-15', 'format': 'ODI', 'opposition': 'South Africa', 'venue': 'Centurion', 'overs': '9.4', 'wickets': 2, 'runsConceded': 42, 'economy': 4.34, 'dots': 32},
        {'matchId': '203', 'date': '2024-02-01', 'format': 'ODI', 'opposition': 'England', 'venue': 'Lord\'s', 'overs': '10.0', 'wickets': 4, 'runsConceded': 28, 'economy': 2.8, 'dots': 42},
    ],
    'wicketDistribution': {'zero': 20, 'one': 35, 'two': 32, 'three': 18, 'fourPlus': 8},
    'recentBowlingSummary': {'meanWickets': 3.0, 'meanEconomy': 3.55, 'wicketTrendDirection': 'UPWARD'},
    'bowlingVenueBreakdown': [
        {'venue': 'Lord\'s', 'appearances': 4, 'totalWickets': 11, 'bowlingAverage': 18.2, 'economyRate': 3.8, 'bestBowling': '4/28'},
    ],
    'bowlingOppositionBreakdown': [
        {'opposition': 'England', 'appearances': 15, 'totalWickets': 28, 'bowlingAverage': 21.4, 'economyRate': 4.1, 'bestBowling': '6/19'},
    ],
}

print("\n--- 3. Testing Bowler Analysis Generator ---")
generate_player_analysis_workbook(mock_bowler, out_bowler, selected_format="ODI", selected_recent_n=5)
print(f"Generated {out_bowler} ({os.path.getsize(out_bowler)} bytes)")

with zipfile.ZipFile(out_bowler) as z:
    names = z.namelist()
    charts = [n for n in names if 'xl/charts/' in n]
    print(f"Native Charts found in Bowler Analysis workbook: {len(charts)} ({charts})")
    assert len(charts) >= 3, f"Expected at least 3 native charts for bowler, found {len(charts)}"

# Clean up
os.remove(out_player)
os.remove(out_comp)
os.remove(out_bowler)
print("\nSUCCESS: Native chart test passed successfully!")
