"""
report_routes.py

Flask Blueprint for generating native Excel reports with embedded DrawingML charts.
Prefix: /api/ml/reports
"""

import io
from flask import Blueprint, request, send_file, jsonify
from api.excel_report_generator import (
    generate_player_analysis_workbook,
    generate_player_comparison_workbook,
    sanitize_filename
)

report_bp = Blueprint('report_bp', __name__)


@report_bp.route('/player-analysis', methods=['POST'])
def export_player_analysis():
    payload = request.get_json(silent=True) or {}
    analytics = payload.get('analytics')
    if not analytics:
        return jsonify({'error': 'analytics data payload is required'}), 400

    selected_format = payload.get('format', 'ALL')
    recent_n = int(payload.get('recentN', 15))
    player_name = analytics.get('playerName', 'Player')

    output = io.BytesIO()
    generate_player_analysis_workbook(
        analytics=analytics,
        output_target=output,
        selected_format=selected_format,
        selected_recent_n=recent_n
    )
    output.seek(0)

    filename = f"CricketAI_Player_Analysis_{sanitize_filename(player_name)}_{selected_format}.xlsx"
    return send_file(
        output,
        mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        as_attachment=True,
        download_name=filename
    )


@report_bp.route('/player-comparison', methods=['POST'])
def export_player_comparison():
    payload = request.get_json(silent=True) or {}
    comparison = payload.get('comparison')
    if not comparison:
        return jsonify({'error': 'comparison data payload is required'}), 400

    selected_format = payload.get('format', 'ALL')
    recent_n = int(payload.get('recentN', 15))
    opposition = payload.get('opposition', '')
    venue = payload.get('venue', '')

    p1_name = comparison.get('player1Name', 'Player1')
    p2_name = comparison.get('player2Name', 'Player2')

    output = io.BytesIO()
    generate_player_comparison_workbook(
        comparison=comparison,
        output_target=output,
        selected_format=selected_format,
        selected_recent_n=recent_n,
        selected_opposition=opposition,
        selected_venue=venue
    )
    output.seek(0)

    filename = f"CricketAI_Player_Comparison_{sanitize_filename(p1_name)}_vs_{sanitize_filename(p2_name)}_{selected_format}.xlsx"
    return send_file(
        output,
        mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        as_attachment=True,
        download_name=filename
    )
