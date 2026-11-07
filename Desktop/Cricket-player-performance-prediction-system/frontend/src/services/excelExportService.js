/**
 * excelExportService.js
 *
 * CricketAI — Excel (.xlsx) Report Generation Service
 *
 * Generates professionally styled, genuine Excel (.xlsx) workbooks for:
 * 1. Player Performance Analysis Report
 * 2. Player Head-to-Head Comparison Report
 *
 * Uses exceljs with:
 * - Clear worksheets matching displayed UI charts & tables
 * - Styled header rows with dark navy theme accents
 * - Auto-adjusted column widths and frozen header panes
 * - Explicit formatting for dates, integers, decimals, and percentages
 * - Sanitized filenames based on player names, filters, and current date
 */

import ExcelJS from 'exceljs';

// ─── Theme Colors & Styling Constants ────────────────────────────────────────

const STYLES = {
  headerFill: {
    type: 'pattern',
    pattern: 'solid',
    fgColor: { argb: 'FF0D1525' }, // Dark navy #0d1525
  },
  headerFont: {
    name: 'Calibri',
    size: 11,
    bold: true,
    color: { argb: 'FFF0F4FF' }, // Light primary text
  },
  subHeaderFill: {
    type: 'pattern',
    pattern: 'solid',
    fgColor: { argb: 'FF1A2236' }, // Card navy #1a2236
  },
  subHeaderFont: {
    name: 'Calibri',
    size: 10,
    bold: true,
    color: { argb: 'FF8A9AC0' },
  },
  titleFont: {
    name: 'Calibri',
    size: 14,
    bold: true,
    color: { argb: 'FF3B82F6' }, // CricketAI accent blue
  },
  metaLabelFont: {
    name: 'Calibri',
    size: 10,
    bold: true,
    color: { argb: 'FF4F6080' },
  },
  metaValueFont: {
    name: 'Calibri',
    size: 10,
    color: { argb: 'FF1E293B' },
  },
  borderThin: {
    top: { style: 'thin', color: { argb: 'FFE2E8F0' } },
    bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
    left: { style: 'thin', color: { argb: 'FFE2E8F0' } },
    right: { style: 'thin', color: { argb: 'FFE2E8F0' } },
  },
};

// ─── Utility Helpers ─────────────────────────────────────────────────────────

function sanitizeFilename(name) {
  return String(name || '')
    .trim()
    .replace(/[<>:"/\\|?*]+/g, '_')
    .replace(/\s+/g, '_');
}

function sanitizeSheetName(name) {
  return String(name || '')
    .trim()
    .replace(/[\\/?*:[\]]/g, '_')
    .slice(0, 31);
}

function formatDate(date = new Date()) {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function autoFitColumns(sheet, minWidth = 12) {
  if (!sheet) return;

  if (sheet.columns && sheet.columns.length > 0) {
    sheet.columns.forEach((col) => {
      if (!col) return;
      let maxLen = minWidth;
      if (col.header) {
        maxLen = Math.max(maxLen, String(col.header).length + 4);
      }
      col.eachCell({ includeEmpty: false }, (cell) => {
        const val = cell.value;
        if (val != null) {
          const str = typeof val === 'object' && val.text ? val.text : String(val);
          maxLen = Math.max(maxLen, Math.min(str.length + 3, 50));
        }
      });
      col.width = maxLen;
    });
    return;
  }

  // For sheets where rows were added directly without defining sheet.columns
  if (sheet.rowCount > 0) {
    const colWidths = {};
    sheet.eachRow({ includeEmpty: false }, (row) => {
      row.eachCell({ includeEmpty: false }, (cell, colNumber) => {
        const val = cell.value;
        if (val != null) {
          const str = typeof val === 'object' && val.text ? val.text : String(val);
          const len = Math.min(str.length + 3, 50);
          colWidths[colNumber] = Math.max(colWidths[colNumber] || minWidth, len);
        }
      });
    });

    Object.keys(colWidths).forEach((colNumber) => {
      const col = sheet.getColumn(Number(colNumber));
      if (col) {
        col.width = colWidths[colNumber];
      }
    });
  }
}

function applyHeaderStyling(row) {
  row.height = 24;
  row.eachCell((cell) => {
    cell.fill = STYLES.headerFill;
    cell.font = STYLES.headerFont;
    cell.alignment = { vertical: 'middle', horizontal: 'center' };
    cell.border = STYLES.borderThin;
  });
}

async function triggerDownload(workbook, filename) {
  if (typeof window !== 'undefined' && typeof document !== 'undefined') {
    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  }
}

// ─── 1. Player Performance Analysis Workbook ─────────────────────────────────

export async function exportPlayerAnalysisToExcel({
  analytics,
  selectedFormat = 'ALL',
  selectedRecentN = 10,
  returnWorkbook = false,
}) {
  if (!analytics) {
    throw new Error('No analytics data available to export.');
  }

  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'CricketAI Performance Prediction & Analytics System';
  workbook.created = new Date();

  const playerName = analytics.playerName || 'Player';
  const role = analytics.role || 'UNKNOWN';
  const todayStr = formatDate();

  // ── Sheet 1: Report Summary ───────────────────────────────────────────────
  const summarySheet = workbook.addWorksheet('Report Summary', {
    views: [{ state: 'frozen', ySplit: 5 }],
  });

  summarySheet.addRow(['CricketAI — Player Performance Analytics Report']);
  summarySheet.getRow(1).font = STYLES.titleFont;
  summarySheet.addRow([`Generated on: ${todayStr} | Target Player: ${playerName} | Format Filter: ${selectedFormat}`]);
  summarySheet.getRow(2).font = { name: 'Calibri', size: 10, italic: true, color: { argb: 'FF64748B' } };
  summarySheet.addRow([]); // Blank line

  // Metadata block
  const metaRows = [
    ['Player Name', playerName, 'Detected Role', role],
    ['Format Scope', selectedFormat, 'Recent Window (N)', `${selectedRecentN} Innings/Spells`],
    ['Total Batting Innings', analytics.totalBattingInnings ?? 0, 'Total Bowling Innings', analytics.totalBowlingInnings ?? 0],
    ['Dataset Earliest Date', analytics.coverage?.battingEarliestDate || analytics.coverage?.bowlingEarliestDate || '—',
     'Dataset Latest Date', analytics.coverage?.battingLatestDate || analytics.coverage?.bowlingLatestDate || '—'],
  ];

  metaRows.forEach((r) => {
    const row = summarySheet.addRow(r);
    row.getCell(1).font = STYLES.metaLabelFont;
    row.getCell(2).font = STYLES.metaValueFont;
    row.getCell(3).font = STYLES.metaLabelFont;
    row.getCell(4).font = STYLES.metaValueFont;
  });

  summarySheet.addRow([]); // Blank line

  // Key KPI Overview
  summarySheet.addRow(['Key Metrics Summary', '', '', '']);
  summarySheet.getRow(8).font = { name: 'Calibri', size: 12, bold: true, color: { argb: 'FF1E293B' } };

  summarySheet.addRow(['Discipline', 'Metric', 'Format Value', 'Notes / Context']);
  applyHeaderStyling(summarySheet.getRow(9));

  const bat = analytics.battingSummary;
  const bowl = analytics.bowlingSummary;
  const recent = analytics.recentFormSummary;

  if (bat) {
    summarySheet.addRow(['Batting', 'Total Runs', bat.totalRuns ?? 0, `Across ${bat.innings} innings`]);
    summarySheet.addRow(['Batting', 'Runs / Innings', bat.runsPerInnings ?? 0, 'Total runs / participated innings']);
    summarySheet.addRow(['Batting', 'Strike Rate', bat.strikeRate ?? 0, 'Runs per 100 legal balls faced']);
    summarySheet.addRow(['Batting', 'Highest Score', bat.highestScore != null ? `${bat.highestScore}${bat.highestScoreNotOut ? '*' : ''}` : '—', 'Personal best in format']);
    summarySheet.addRow(['Batting', 'Fifties / Hundreds', `${bat.fifties ?? 0} / ${bat.hundreds ?? 0}`, 'Milestone centuries & half-centuries']);
    summarySheet.addRow(['Batting', 'Boundary Run %', bat.boundaryPercentage != null ? `${bat.boundaryPercentage}%` : '—', '% runs scored via 4s & 6s']);
    summarySheet.addRow(['Batting', 'Boundary Count', `${bat.fours ?? 0} fours, ${bat.sixes ?? 0} sixes`, 'Total boundaries accumulated']);
    summarySheet.addRow(['Batting', 'Consistency (CV%)', bat.coefficientOfVariation != null ? `${bat.coefficientOfVariation}%` : '—', 'Lower CV% indicates higher consistency']);
    if (recent?.meanRuns != null) {
      summarySheet.addRow(['Batting Recent', `Last ${selectedRecentN} Innings Mean`, `${recent.meanRuns} runs`, `Trend: ${recent.runTrendDirection || 'STABLE'}`]);
    }
  }

  if (bowl) {
    summarySheet.addRow(['Bowling', 'Total Wickets', bowl.totalWickets ?? 0, `Across ${bowl.innings} bowling spells`]);
    summarySheet.addRow(['Bowling', 'Overs Bowled', bowl.totalOvers ?? 0, `Legal balls: ${bowl.validBallsBowled ?? 0}`]);
    summarySheet.addRow(['Bowling', 'Economy Rate', bowl.economyRate ?? 0, 'Runs conceded per decimal over']);
    summarySheet.addRow(['Bowling', 'Bowling Average', bowl.bowlingAverage != null ? bowl.bowlingAverage : 'N/A', 'Runs conceded per wicket']);
    summarySheet.addRow(['Bowling', 'Bowling Strike Rate', bowl.bowlingStrikeRate != null ? `${bowl.bowlingStrikeRate} balls/wkt` : 'N/A', 'Balls bowled per wicket']);
    summarySheet.addRow(['Bowling', 'Hauls (4W / 5W)', `${bowl.fourWickets ?? 0} (4W) / ${bowl.fiveWickets ?? 0} (5W)`, 'Decisive match-winning spells']);
    if (recent?.meanWickets != null) {
      summarySheet.addRow(['Bowling Recent', `Last ${selectedRecentN} Spells Mean`, `${recent.meanWickets} wkts`, `Trend: ${recent.wicketTrendDirection || 'STABLE'}`]);
    }
  }

  autoFitColumns(summarySheet);

  // ── Sheet 2: Career Statistics (Format Breakdown) ─────────────────────────
  const careerSheet = workbook.addWorksheet('Career Statistics', {
    views: [{ state: 'frozen', ySplit: 1 }],
  });

  careerSheet.columns = [
    { header: 'Format', key: 'format', width: 12 },
    { header: 'Batting Innings', key: 'batInn', width: 15 },
    { header: 'Total Runs', key: 'runs', width: 14 },
    { header: 'Runs / Inn', key: 'rpi', width: 14 },
    { header: 'Batting SR', key: 'sr', width: 14 },
    { header: '50s', key: 'fifties', width: 10 },
    { header: '100s', key: 'hundreds', width: 10 },
    { header: 'Bowling Spells', key: 'bowlInn', width: 15 },
    { header: 'Wickets', key: 'wkts', width: 12 },
    { header: 'Bowling Avg', key: 'bowlAvg', width: 14 },
    { header: 'Economy', key: 'economy', width: 12 },
    { header: '4W Hauls', key: 'fourW', width: 12 },
    { header: '5W Hauls', key: 'fiveW', width: 12 },
  ];
  applyHeaderStyling(careerSheet.getRow(1));

  const formatRows = (analytics.formatBreakdown && analytics.formatBreakdown.length > 0)
    ? analytics.formatBreakdown
    : [{
        format: selectedFormat,
        battingInnings: bat?.innings ?? analytics.totalBattingInnings ?? 0,
        totalRuns: bat?.totalRuns ?? 0,
        runsPerInnings: bat?.runsPerInnings,
        battingStrikeRate: bat?.strikeRate,
        fifties: bat?.fifties ?? 0,
        hundreds: bat?.hundreds ?? 0,
        bowlingInnings: bowl?.innings ?? analytics.totalBowlingInnings ?? 0,
        totalWickets: bowl?.totalWickets ?? 0,
        bowlingAverage: bowl?.bowlingAverage,
        economyRate: bowl?.economyRate,
        fourWickets: bowl?.fourWickets ?? 0,
        fiveWickets: bowl?.fiveWickets ?? 0,
      }];

  formatRows.forEach((fmt) => {
    careerSheet.addRow({
      format: fmt.format,
      batInn: fmt.battingInnings ?? 0,
      runs: fmt.totalRuns ?? 0,
      rpi: fmt.runsPerInnings != null ? Number(fmt.runsPerInnings.toFixed(1)) : '—',
      sr: (fmt.battingStrikeRate != null ? Number(fmt.battingStrikeRate.toFixed(1)) : (fmt.strikeRate != null ? Number(fmt.strikeRate.toFixed(1)) : '—')),
      fifties: fmt.fifties ?? 0,
      hundreds: fmt.hundreds ?? 0,
      bowlInn: fmt.bowlingInnings ?? 0,
      wkts: fmt.totalWickets ?? 0,
      bowlAvg: fmt.bowlingAverage != null ? Number(fmt.bowlingAverage.toFixed(2)) : '—',
      economy: fmt.economyRate != null ? Number(fmt.economyRate.toFixed(2)) : '—',
      fourW: fmt.fourWickets ?? 0,
      fiveW: fmt.fiveWickets ?? 0,
    });
  });

  autoFitColumns(careerSheet);

  // ── Sheet 3: Recent Performance (Match-by-Match Logs) ─────────────────────
  const recentSheet = workbook.addWorksheet('Recent Performance', {
    views: [{ state: 'frozen', ySplit: 1 }],
  });

  const isBattingFocus = role === 'BATSMAN' || role === 'ALL_ROUNDER' || (analytics.recentBattingForm?.length > 0);
  const isBowlingFocus = role === 'BOWLER' || role === 'ALL_ROUNDER' || (analytics.recentBowlingForm?.length > 0);

  if (isBattingFocus) {
    recentSheet.addRow(['Recent Batting Innings (Chronological Latest First)']);
    recentSheet.getRow(1).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF10B981' } };

    recentSheet.addRow([
      'Date', 'Format', 'vs Opposition', 'Venue', 'Runs', 'Balls Faced',
      'Strike Rate', '4s', '6s', 'Not Out', 'In Window (N=' + selectedRecentN + ')'
    ]);
    applyHeaderStyling(recentSheet.getRow(2));

    const batForm = analytics.recentBattingForm || [];
    const windowCutoffIdx = Math.max(0, batForm.length - selectedRecentN);

    // Reversed for latest first
    [...batForm].reverse().forEach((r, idx) => {
      const origIdx = batForm.length - 1 - idx;
      const inWindow = origIdx >= windowCutoffIdx ? 'YES' : 'NO';
      recentSheet.addRow([
        r.date || '—',
        r.format || '—',
        r.opposition || '—',
        r.venue || '—',
        r.runs ?? 0,
        r.ballsFaced ?? 0,
        r.strikeRate != null ? Number(r.strikeRate.toFixed(1)) : '—',
        r.fours ?? 0,
        r.sixes ?? 0,
        r.notOut ? 'YES' : 'NO',
        inWindow,
      ]);
    });

    recentSheet.addRow([]); // Blank spacer
  }

  if (isBowlingFocus) {
    const startRow = recentSheet.rowCount + 1;
    recentSheet.addRow(['Recent Bowling Spells (Chronological Latest First)']);
    recentSheet.getRow(startRow).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FFEF4444' } };

    recentSheet.addRow([
      'Date', 'Format', 'vs Opposition', 'Venue', 'Overs', 'Runs Conceded',
      'Wickets', 'Figures', 'Economy Rate', 'Haul Badge', 'In Window (N=' + selectedRecentN + ')'
    ]);
    applyHeaderStyling(recentSheet.getRow(startRow + 1));

    const bowlForm = analytics.recentBowlingForm || [];
    const windowCutoffIdx = Math.max(0, bowlForm.length - selectedRecentN);

    [...bowlForm].reverse().forEach((r, idx) => {
      const origIdx = bowlForm.length - 1 - idx;
      const inWindow = origIdx >= windowCutoffIdx ? 'YES' : 'NO';
      recentSheet.addRow([
        r.date || '—',
        r.format || '—',
        r.opposition || '—',
        r.venue || '—',
        r.overs != null ? r.overs : '—',
        r.runsConceded != null ? r.runsConceded : '—',
        r.wickets != null ? r.wickets : '—',
        r.figures || `${r.wickets ?? 0}/${r.runsConceded ?? 0}`,
        r.economyRate != null ? Number(r.economyRate.toFixed(2)) : '—',
        r.haulBadge || '—',
        inWindow,
      ]);
    });
  }

  autoFitColumns(recentSheet);

  // ── Sheet 4: Performance Trends (Chart Underlying Datasets) ───────────────
  const trendsSheet = workbook.addWorksheet('Performance Trends', {
    views: [{ state: 'frozen', ySplit: 1 }],
  });

  // Table 1: Batting Runs & Strike Rate Trends
  trendsSheet.addRow([
    'Innings #', 'Date', 'Opposition', 'Venue', 'Format',
    'Runs Scored', 'Window Mean Runs (Ref Line)',
    'Balls Faced', 'Innings Strike Rate', 'Aggregate Format SR (Ref Line)'
  ]);
  applyHeaderStyling(trendsSheet.getRow(1));

  const batRecentSlice = (analytics.recentBattingForm || []).slice(-selectedRecentN);
  const meanRunsVal = recent?.meanRuns ?? null;
  const aggSrVal = bat?.strikeRate ?? null;

  batRecentSlice.forEach((d, i) => {
    trendsSheet.addRow([
      `Innings ${i + 1}`,
      d.date || '—',
      d.opposition || '—',
      d.venue || '—',
      d.format || '—',
      d.runs ?? 0,
      meanRunsVal != null ? Number(meanRunsVal.toFixed(1)) : '—',
      d.ballsFaced ?? 0,
      d.strikeRate != null ? Number(d.strikeRate.toFixed(1)) : '—',
      aggSrVal != null ? Number(aggSrVal.toFixed(1)) : '—',
    ]);
  });

  // Spacer & Table 2: Bowling Wickets & Economy Trends
  trendsSheet.addRow([]);
  const bowlTrendStart = trendsSheet.rowCount + 1;

  trendsSheet.addRow([
    'Spell #', 'Date', 'Opposition', 'Venue', 'Format',
    'Wickets Taken', 'Window Mean Wkts (Ref Line)',
    'Overs Bowled', 'Runs Conceded', 'Spell Economy Rate', 'Aggregate Format Econ (Ref Line)'
  ]);
  applyHeaderStyling(trendsSheet.getRow(bowlTrendStart));

  const bowlRecentSlice = (analytics.recentBowlingForm || []).slice(-selectedRecentN);
  const meanWktsVal = recent?.meanWickets ?? null;
  const aggEconVal = bowl?.economyRate ?? null;

  bowlRecentSlice.forEach((d, i) => {
    trendsSheet.addRow([
      `Spell ${i + 1}`,
      d.date || '—',
      d.opposition || '—',
      d.venue || '—',
      d.format || '—',
      d.wickets ?? 0,
      meanWktsVal != null ? Number(meanWktsVal.toFixed(2)) : '—',
      d.overs != null ? d.overs : '—',
      d.runsConceded != null ? d.runsConceded : '—',
      d.economyRate != null ? Number(d.economyRate.toFixed(2)) : '—',
      aggEconVal != null ? Number(aggEconVal.toFixed(2)) : '—',
    ]);
  });

  autoFitColumns(trendsSheet);

  // ── Sheet 5: Milestone & Boundary Analysis ────────────────────────────────
  const milestoneSheet = workbook.addWorksheet('Milestones & Boundaries', {
    views: [{ state: 'frozen', ySplit: 1 }],
  });

  milestoneSheet.addRow(['Scoring Milestone Distribution (Batting)']);
  milestoneSheet.getRow(1).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF3B82F6' } };

  milestoneSheet.addRow(['Milestone Tier', 'Score Range', 'Innings Frequency', 'Percentage Share (%)']);
  applyHeaderStyling(milestoneSheet.getRow(2));

  const sDist = analytics.scoreDistribution;
  if (sDist) {
    milestoneSheet.addRow(['Single Digits', '0 – 9 runs', sDist.singleDigits ?? 0, sDist.singleDigitsPct != null ? `${sDist.singleDigitsPct.toFixed(1)}%` : '—']);
    milestoneSheet.addRow(['Starts', '10 – 29 runs', sDist.starts ?? 0, sDist.startsPct != null ? `${sDist.startsPct.toFixed(1)}%` : '—']);
    milestoneSheet.addRow(['Solid Starts', '30 – 49 runs', sDist.solidStarts ?? 0, sDist.solidStartsPct != null ? `${sDist.solidStartsPct.toFixed(1)}%` : '—']);
    milestoneSheet.addRow(['Fifties (Half-Centuries)', '50 – 99 runs', sDist.fifties ?? 0, sDist.fiftiesPct != null ? `${sDist.fiftiesPct.toFixed(1)}%` : '—']);
    milestoneSheet.addRow(['Centuries', '100+ runs', sDist.centuries ?? 0, sDist.centuriesPct != null ? `${sDist.centuriesPct.toFixed(1)}%` : '—']);
    if (sDist.patternInterpretation) {
      milestoneSheet.addRow(['Interpretation', sDist.patternInterpretation, '', '']);
    }
  }

  milestoneSheet.addRow([]); // Blank spacer

  // Boundary Contribution Breakdown
  const bStart = milestoneSheet.rowCount + 1;
  milestoneSheet.addRow(['Boundary Run Contribution Analysis']);
  milestoneSheet.getRow(bStart).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF10B981' } };

  milestoneSheet.addRow(['Source of Runs', 'Formula / Multiplier', 'Total Runs', 'Percentage of Career Runs (%)']);
  applyHeaderStyling(milestoneSheet.getRow(bStart + 1));

  if (bat && bat.totalRuns > 0) {
    const r4 = (bat.fours ?? 0) * 4;
    const r6 = (bat.sixes ?? 0) * 6;
    const rRun = Math.max(0, bat.totalRuns - (r4 + r6));
    const tot = bat.totalRuns;

    milestoneSheet.addRow(['Runs via Fours (4s)', `${bat.fours ?? 0} fours × 4`, r4, `${((r4 / tot) * 100).toFixed(1)}%`]);
    milestoneSheet.addRow(['Runs via Sixes (6s)', `${bat.sixes ?? 0} sixes × 6`, r6, `${((r6 / tot) * 100).toFixed(1)}%`]);
    milestoneSheet.addRow(['Running Between Wickets', '1s, 2s, 3s accumulated', rRun, `${((rRun / tot) * 100).toFixed(1)}%`]);
    milestoneSheet.addRow(['Total Format Runs', 'All participated innings', tot, '100.0%']);
  }

  milestoneSheet.addRow([]); // Blank spacer

  // Bowling Hauls Distribution
  const bDist = analytics.bowlingDistribution;
  if (bDist) {
    const bowlDistStart = milestoneSheet.rowCount + 1;
    milestoneSheet.addRow(['Bowling Wicket Distribution per Spell']);
    milestoneSheet.getRow(bowlDistStart).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FFEF4444' } };

    milestoneSheet.addRow(['Wickets in Spell', 'Spells Frequency', 'Percentage Share (%)']);
    applyHeaderStyling(milestoneSheet.getRow(bowlDistStart + 1));

    milestoneSheet.addRow(['0 Wickets', bDist.zeroWickets ?? 0, bDist.zeroWicketsPct != null ? `${bDist.zeroWicketsPct.toFixed(1)}%` : '—']);
    milestoneSheet.addRow(['1 Wicket', bDist.oneWicket ?? 0, bDist.oneWicketPct != null ? `${bDist.oneWicketPct.toFixed(1)}%` : '—']);
    milestoneSheet.addRow(['2 Wickets', bDist.twoWickets ?? 0, bDist.twoWicketsPct != null ? `${bDist.twoWicketsPct.toFixed(1)}%` : '—']);
    milestoneSheet.addRow(['3 Wickets', bDist.threeWickets ?? 0, bDist.threeWicketsPct != null ? `${bDist.threeWicketsPct.toFixed(1)}%` : '—']);
    milestoneSheet.addRow(['4+ Wickets (Hauls)', bDist.fourPlusWickets ?? 0, bDist.fourPlusWicketsPct != null ? `${bDist.fourPlusWicketsPct.toFixed(1)}%` : '—']);
  }

  autoFitColumns(milestoneSheet);

  // ── Sheet 6: Venue & Opposition Breakdown ─────────────────────────────────
  const contextSheet = workbook.addWorksheet('Venue & Opposition Context', {
    views: [{ state: 'frozen', ySplit: 1 }],
  });

  contextSheet.addRow(['Top Batting Venues']);
  contextSheet.getRow(1).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF3B82F6' } };

  contextSheet.addRow(['Venue Name', 'Innings', 'Total Runs', 'Runs / Innings', '50s', '100s']);
  applyHeaderStyling(contextSheet.getRow(2));

  (analytics.venueBreakdown || []).forEach((v) => {
    contextSheet.addRow([
      v.venue,
      v.innings ?? 0,
      v.totalRuns ?? 0,
      v.runsPerInnings != null ? Number(v.runsPerInnings.toFixed(1)) : '—',
      v.fifties ?? 0,
      v.hundreds ?? 0,
    ]);
  });

  contextSheet.addRow([]); // Blank spacer

  const oppStart = contextSheet.rowCount + 1;
  contextSheet.addRow(['Batting vs Opposition Teams']);
  contextSheet.getRow(oppStart).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF10B981' } };

  contextSheet.addRow(['Opposition Team', 'Innings', 'Total Runs', 'Runs / Innings', '50s', '100s']);
  applyHeaderStyling(contextSheet.getRow(oppStart + 1));

  (analytics.oppositionBreakdown || []).forEach((o) => {
    contextSheet.addRow([
      o.opposition,
      o.innings ?? 0,
      o.totalRuns ?? 0,
      o.runsPerInnings != null ? Number(o.runsPerInnings.toFixed(1)) : '—',
      o.fifties ?? 0,
      o.hundreds ?? 0,
    ]);
  });

  // Bowling Venue & Opposition
  if (analytics.bowlingVenueBreakdown?.length > 0 || analytics.bowlingOppositionBreakdown?.length > 0) {
    contextSheet.addRow([]); // Spacer
    const bVenStart = contextSheet.rowCount + 1;
    contextSheet.addRow(['Top Bowling Venues (Economy & Wickets)']);
    contextSheet.getRow(bVenStart).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FFEF4444' } };

    contextSheet.addRow(['Venue Name', 'Appearances', 'Wickets', 'Runs Conceded', 'Economy Rate', 'Bowling Avg']);
    applyHeaderStyling(contextSheet.getRow(bVenStart + 1));

    (analytics.bowlingVenueBreakdown || []).forEach((v) => {
      contextSheet.addRow([
        v.venue,
        v.appearances ?? 0,
        v.totalWickets ?? 0,
        v.totalRunsConceded ?? 0,
        v.economyRate != null ? Number(v.economyRate.toFixed(2)) : '—',
        v.bowlingAverage != null ? Number(v.bowlingAverage.toFixed(2)) : '—',
      ]);
    });

    contextSheet.addRow([]); // Spacer
    const bOppStart = contextSheet.rowCount + 1;
    contextSheet.addRow(['Bowling vs Opposition Teams']);
    contextSheet.getRow(bOppStart).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FFF59E0B' } };

    contextSheet.addRow(['Opposition Team', 'Appearances', 'Wickets', 'Runs Conceded', 'Economy Rate', 'Bowling Avg']);
    applyHeaderStyling(contextSheet.getRow(bOppStart + 1));

    (analytics.bowlingOppositionBreakdown || []).forEach((o) => {
      contextSheet.addRow([
        o.opposition,
        o.appearances ?? 0,
        o.totalWickets ?? 0,
        o.totalRunsConceded ?? 0,
        o.economyRate != null ? Number(o.economyRate.toFixed(2)) : '—',
        o.bowlingAverage != null ? Number(o.bowlingAverage.toFixed(2)) : '—',
      ]);
    });
  }

  autoFitColumns(contextSheet);

  // ── Download File ─────────────────────────────────────────────────────────
  const filename = `CricketAI_Player_Analysis_${sanitizeFilename(playerName)}_${selectedFormat}_${todayStr}.xlsx`;
  await triggerDownload(workbook, filename);
  return returnWorkbook ? { filename, workbook } : filename;
}

// ─── 2. Player Head-to-Head Comparison Workbook ─────────────────────────────

export async function exportPlayerComparisonToExcel({
  comparison,
  selectedFormat = 'ALL',
  selectedRecentN = 15,
  selectedOpposition = '',
  selectedVenue = '',
  returnWorkbook = false,
}) {
  if (!comparison) {
    throw new Error('No player comparison data available to export.');
  }

  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'CricketAI Performance Prediction & Analytics System';
  workbook.created = new Date();

  const p1Name = comparison.player1Name || 'Player 1';
  const p2Name = comparison.player2Name || 'Player 2';
  const todayStr = formatDate();

  // ── Sheet 1: Comparison Summary ───────────────────────────────────────────
  const summarySheet = workbook.addWorksheet('Comparison Summary', {
    views: [{ state: 'frozen', ySplit: 5 }],
  });

  summarySheet.addRow(['CricketAI — Head-to-Head Player Comparison Report']);
  summarySheet.getRow(1).font = STYLES.titleFont;
  summarySheet.addRow([`Generated on: ${todayStr} | ${p1Name} vs ${p2Name} | Format: ${selectedFormat}`]);
  summarySheet.getRow(2).font = { name: 'Calibri', size: 10, italic: true, color: { argb: 'FF64748B' } };
  summarySheet.addRow([]);

  // Comparison Metadata Block
  const metaRows = [
    ['Player 1', p1Name, 'Player 2', p2Name],
    ['Player 1 Role', comparison.player1?.role || 'UNKNOWN', 'Player 2 Role', comparison.player2?.role || 'UNKNOWN'],
    ['Comparison Scope', comparison.comparisonScope || 'ALL', 'Recent Window (N)', `${selectedRecentN} Innings/Spells`],
    ['Scope Explanation', comparison.scopeExplanation || '—', 'Active Opponent Filter', selectedOpposition || 'All Opponents'],
    ['Active Venue Filter', selectedVenue || 'All Venues', 'Format Analyzed', selectedFormat],
  ];

  metaRows.forEach((r) => {
    const row = summarySheet.addRow(r);
    row.getCell(1).font = STYLES.metaLabelFont;
    row.getCell(2).font = STYLES.metaValueFont;
    row.getCell(3).font = STYLES.metaLabelFont;
    row.getCell(4).font = STYLES.metaValueFont;
  });

  summarySheet.addRow([]);

  // Written Evidence-Based Synthesis
  if (comparison.writtenInterpretation) {
    summarySheet.addRow(['Evidence-Based Analytical Synthesis']);
    summarySheet.getRow(summarySheet.rowCount).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF3B82F6' } };
    summarySheet.addRow([comparison.writtenInterpretation]);
    summarySheet.getRow(summarySheet.rowCount).font = { name: 'Calibri', size: 10, italic: true };
    summarySheet.addRow([]);
  }

  // Sample Scope & Dataset Integrity
  summarySheet.addRow(['Dataset Scope & Source Boundaries']);
  summarySheet.getRow(summarySheet.rowCount).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF64748B' } };
  summarySheet.addRow([
    `${p1Name}: ${comparison.player1?.coverage?.battingInningsForFormat ?? 0} batting inn, ${comparison.player1?.coverage?.bowlingInningsForFormat ?? 0} bowling inn | ` +
    `${p2Name}: ${comparison.player2?.coverage?.battingInningsForFormat ?? 0} batting inn, ${comparison.player2?.coverage?.bowlingInningsForFormat ?? 0} bowling inn.`
  ]);
  summarySheet.addRow(['Statistics represent the verified international Cricsheet ball-by-ball subset, not lifetime domestic career totals.']);

  autoFitColumns(summarySheet);

  // ── Sheet 2: Side by Side Metric Comparison ───────────────────────────────
  const sideBySideSheet = workbook.addWorksheet('Side by Side', {
    views: [{ state: 'frozen', ySplit: 1 }],
  });

  sideBySideSheet.columns = [
    { header: 'Discipline', key: 'discipline', width: 14 },
    { header: 'Metric', key: 'metric', width: 24 },
    { header: p1Name, key: 'val1', width: 18 },
    { header: p2Name, key: 'val2', width: 18 },
    { header: 'Absolute Diff', key: 'diff', width: 15 },
    { header: '% Difference', key: 'pctDiff', width: 14 },
    { header: 'Advantage', key: 'adv', width: 16 },
    { header: 'Criterion', key: 'crit', width: 18 },
    { header: 'Analytical Interpretation', key: 'interp', width: 36 },
  ];
  applyHeaderStyling(sideBySideSheet.getRow(1));

  const details = comparison.metricAdvantagesDetailed || {};
  const p1Obj = comparison.player1 || comparison.player1Analytics;
  const p2Obj = comparison.player2 || comparison.player2Analytics;
  const batComp = comparison.battingComparison;
  const bowlComp = comparison.bowlingComparison;

  // Standard metric list to export in clean logical order with key aliases and aggregate fallbacks
  const metricKeys = [
    { key: 'runs', altKeys: ['totalRuns', 'bat_total_runs'], label: 'Total Runs', disc: 'Batting',
      fallback: () => ({ val1: batComp?.totalRuns1, val2: batComp?.totalRuns2, diff: batComp?.diffTotalRuns, crit: 'Higher is Better' }) },
    { key: 'runsPerInnings', altKeys: ['rpi', 'bat_runs_per_innings'], label: 'Runs per Innings', disc: 'Batting',
      fallback: () => ({ val1: batComp?.runsPerInnings1, val2: batComp?.runsPerInnings2, diff: batComp?.diffRunsPerInnings, crit: 'Higher is Better' }) },
    { key: 'battingAverage', altKeys: ['avg', 'bat_avg'], label: 'Batting Average', disc: 'Batting',
      fallback: () => ({ val1: batComp?.battingAverage1, val2: batComp?.battingAverage2, diff: batComp?.diffBattingAverage, crit: 'Higher is Better' }) },
    { key: 'battingStrikeRate', altKeys: ['strikeRate', 'bat_strike_rate'], label: 'Batting Strike Rate', disc: 'Batting',
      fallback: () => ({ val1: batComp?.strikeRate1, val2: batComp?.strikeRate2, diff: batComp?.diffStrikeRate, crit: 'Higher is Better' }) },
    { key: 'highestScore', altKeys: ['highScore', 'bat_highest_score'], label: 'Highest Score', disc: 'Batting',
      fallback: () => ({ val1: batComp?.highestScore1, val2: batComp?.highestScore2, crit: 'Higher is Better' }) },
    { key: 'boundaryPercentage', altKeys: ['boundaryRunPercentage', 'bat_boundary_pct'], label: 'Boundary Run %', disc: 'Batting',
      fallback: () => ({ val1: batComp?.boundaryPercentage1, val2: batComp?.boundaryPercentage2, diff: batComp?.diffBoundaryPercentage, crit: 'Higher is Better' }) },
    { key: 'boundaryBallPercentage', altKeys: ['boundaryBallPct'], label: 'Boundary Ball %', disc: 'Batting',
      fallback: () => ({ val1: batComp?.boundaryBallPercentage1, val2: batComp?.boundaryBallPercentage2, crit: 'Higher is Better' }) },
    { key: 'fours', altKeys: ['foursCount'], label: 'Fours (4s)', disc: 'Batting',
      fallback: () => ({ val1: batComp?.fours1, val2: batComp?.fours2, diff: batComp?.diffFours, crit: 'Higher is Better' }) },
    { key: 'sixes', altKeys: ['sixesCount'], label: 'Sixes (6s)', disc: 'Batting',
      fallback: () => ({ val1: batComp?.sixes1, val2: batComp?.sixes2, diff: batComp?.diffSixes, crit: 'Higher is Better' }) },
    { key: 'fifties', altKeys: ['halfCenturies'], label: 'Fifties (50s)', disc: 'Batting',
      fallback: () => ({ val1: batComp?.fifties1, val2: batComp?.fifties2, diff: batComp?.diffFifties, crit: 'Higher is Better' }) },
    { key: 'hundreds', altKeys: ['centuries'], label: 'Hundreds (100s)', disc: 'Batting',
      fallback: () => ({ val1: batComp?.hundreds1, val2: batComp?.hundreds2, diff: batComp?.diffHundreds, crit: 'Higher is Better' }) },
    { key: 'consistency', altKeys: ['coefficientOfVariation', 'bat_consistency'], label: 'Consistency (CV%)', disc: 'Batting Consistency',
      fallback: () => ({ val1: batComp?.scoringConsistency1, val2: batComp?.scoringConsistency2, diff: batComp?.diffScoringConsistency, crit: 'Lower Variance' }) },
    { key: 'wickets', altKeys: ['totalWickets', 'bowl_total_wickets'], label: 'Total Wickets', disc: 'Bowling',
      fallback: () => ({ val1: bowlComp?.totalWickets1, val2: bowlComp?.totalWickets2, diff: bowlComp?.diffTotalWickets, crit: 'Higher is Better' }) },
    { key: 'wicketsPerInnings', altKeys: ['wpi', 'bowl_wickets_per_inn'], label: 'Wickets per Innings', disc: 'Bowling',
      fallback: () => ({ val1: bowlComp?.wicketsPerInnings1, val2: bowlComp?.wicketsPerInnings2, diff: bowlComp?.diffWicketsPerInnings, crit: 'Higher is Better' }) },
    { key: 'economyRate', altKeys: ['economy', 'bowl_economy_rate'], label: 'Economy Rate', disc: 'Bowling',
      fallback: () => ({ val1: bowlComp?.economyRate1, val2: bowlComp?.economyRate2, diff: bowlComp?.diffEconomyRate, crit: 'Lower is Better' }) },
    { key: 'bowlingAverage', altKeys: ['bowlAvg', 'bowl_average'], label: 'Bowling Average', disc: 'Bowling',
      fallback: () => ({ val1: bowlComp?.bowlingAverage1, val2: bowlComp?.bowlingAverage2, diff: bowlComp?.diffBowlingAverage, crit: 'Lower is Better' }) },
    { key: 'bowlingStrikeRate', altKeys: ['bowlSr', 'bowl_strike_rate'], label: 'Bowling Strike Rate', disc: 'Bowling',
      fallback: () => ({ val1: bowlComp?.bowlingStrikeRate1, val2: bowlComp?.bowlingStrikeRate2, diff: bowlComp?.diffBowlingStrikeRate, crit: 'Lower is Better' }) },
    { key: 'totalOvers', altKeys: ['overs'], label: 'Overs Bowled', disc: 'Bowling',
      fallback: () => ({ val1: bowlComp?.totalOvers1, val2: bowlComp?.totalOvers2, crit: 'Higher is Better' }) },
    { key: 'fourWickets', altKeys: ['fourW'], label: '4W Hauls', disc: 'Bowling',
      fallback: () => ({ val1: bowlComp?.fourWickets1, val2: bowlComp?.fourWickets2, crit: 'Higher is Better' }) },
    { key: 'fiveWickets', altKeys: ['fiveW'], label: '5W Hauls', disc: 'Bowling',
      fallback: () => ({ val1: bowlComp?.fiveWickets1, val2: bowlComp?.fiveWickets2, crit: 'Higher is Better' }) },
  ];

  const processedKeys = new Set();

  metricKeys.forEach((m) => {
    let d = details[m.key];
    if (!d && m.altKeys) {
      for (const ak of m.altKeys) {
        if (details[ak]) {
          d = details[ak];
          processedKeys.add(ak);
          break;
        }
      }
    } else if (d) {
      processedKeys.add(m.key);
    }

    if (d) {
      let advText = 'Tie';
      if (d.advantage === 'player1') advText = p1Name;
      else if (d.advantage === 'player2') advText = p2Name;
      else if (d.advantage === 'na') advText = 'N/A';

      sideBySideSheet.addRow({
        discipline: m.disc,
        metric: d.displayName || m.label,
        val1: d.value1 != null ? d.value1 : '—',
        val2: d.value2 != null ? d.value2 : '—',
        diff: d.absoluteDiff != null ? d.absoluteDiff : (d.difference != null ? Math.abs(d.difference) : '—'),
        pctDiff: d.percentageDifference != null ? `${d.percentageDifference}%` : '—',
        adv: advText,
        crit: d.criterion === 'LOWER_IS_BETTER' ? 'Lower is Better' : (d.criterion === 'LOWER_VARIABILITY' ? 'Lower Variance' : 'Higher is Better'),
        interp: d.interpretation || d.formulaDescription || '—',
      });
    } else if (m.fallback) {
      const fb = m.fallback();
      if (fb && (fb.val1 != null || fb.val2 != null)) {
        const v1 = fb.val1;
        const v2 = fb.val2;
        let diffVal = fb.diff != null ? Math.abs(fb.diff) : (v1 != null && v2 != null ? Math.abs(Number((v1 - v2).toFixed(2))) : null);
        let advText = 'Tie';
        if (v1 != null && v2 != null) {
          if (fb.crit === 'Lower is Better' || fb.crit === 'Lower Variance') {
            if (v1 < v2) advText = p1Name;
            else if (v2 < v1) advText = p2Name;
          } else {
            if (v1 > v2) advText = p1Name;
            else if (v2 > v1) advText = p2Name;
          }
        } else if (v1 != null) {
          advText = p1Name;
        } else if (v2 != null) {
          advText = p2Name;
        }

        sideBySideSheet.addRow({
          discipline: m.disc,
          metric: m.label,
          val1: v1 != null ? v1 : '—',
          val2: v2 != null ? v2 : '—',
          diff: diffVal != null ? diffVal : '—',
          pctDiff: '—',
          adv: advText,
          crit: fb.crit,
          interp: 'Direct career comparison',
        });
      }
    }
  });

  // Also include any other metrics that were present in details but not covered by metricKeys
  Object.keys(details).forEach((k) => {
    if (!processedKeys.has(k)) {
      const d = details[k];
      let advText = 'Tie';
      if (d.advantage === 'player1') advText = p1Name;
      else if (d.advantage === 'player2') advText = p2Name;
      else if (d.advantage === 'na') advText = 'N/A';

      sideBySideSheet.addRow({
        discipline: 'Additional Metric',
        metric: d.displayName || k,
        val1: d.value1 != null ? d.value1 : '—',
        val2: d.value2 != null ? d.value2 : '—',
        diff: d.absoluteDiff != null ? d.absoluteDiff : (d.difference != null ? Math.abs(d.difference) : '—'),
        pctDiff: d.percentageDifference != null ? `${d.percentageDifference}%` : '—',
        adv: advText,
        crit: d.criterion || '—',
        interp: d.interpretation || d.formulaDescription || '—',
      });
    }
  });

  autoFitColumns(sideBySideSheet);

  // ── Sheet 3: Player 1 Statistics ──────────────────────────────────────────
  const p1Sheet = workbook.addWorksheet(sanitizeSheetName(`${p1Name} Stats`), {
    views: [{ state: 'frozen', ySplit: 1 }],
  });
  populatePlayerStatsSheet(p1Sheet, p1Obj, p1Name, selectedFormat);
  autoFitColumns(p1Sheet);

  // ── Sheet 4: Player 2 Statistics ──────────────────────────────────────────
  const p2Sheet = workbook.addWorksheet(sanitizeSheetName(`${p2Name} Stats`), {
    views: [{ state: 'frozen', ySplit: 1 }],
  });
  populatePlayerStatsSheet(p2Sheet, p2Obj, p2Name, selectedFormat);
  autoFitColumns(p2Sheet);

  // ── Sheet 5: Recent Performance Match Logs ────────────────────────────────
  const recentSheet = workbook.addWorksheet('Recent Performance', {
    views: [{ state: 'frozen', ySplit: 1 }],
  });

  recentSheet.columns = [
    { header: 'Player', key: 'player', width: 16 },
    { header: 'Innings/Spell #', key: 'innNum', width: 15 },
    { header: 'Date', key: 'date', width: 13 },
    { header: 'Format', key: 'format', width: 10 },
    { header: 'Opposition', key: 'opp', width: 16 },
    { header: 'Venue', key: 'venue', width: 24 },
    { header: 'Runs', key: 'runs', width: 10 },
    { header: 'Balls Faced', key: 'balls', width: 12 },
    { header: 'Strike Rate', key: 'sr', width: 12 },
    { header: 'Overs Bowled', key: 'overs', width: 13 },
    { header: 'Runs Conceded', key: 'runsConc', width: 14 },
    { header: 'Wickets', key: 'wkts', width: 10 },
    { header: 'Economy', key: 'econ', width: 12 },
  ];
  applyHeaderStyling(recentSheet.getRow(1));

  // Player 1 Recent Batting & Bowling
  const p1Bat = (p1Obj?.recentBattingForm || []).slice(-selectedRecentN);
  p1Bat.forEach((m, idx) => {
    recentSheet.addRow({
      player: p1Name,
      innNum: `Bat Inn ${idx + 1}`,
      date: m.date || '—',
      format: m.format || '—',
      opp: m.opposition || '—',
      venue: m.venue || '—',
      runs: m.runs ?? 0,
      balls: m.ballsFaced ?? 0,
      sr: m.strikeRate != null ? Number(m.strikeRate.toFixed(1)) : '—',
      overs: '—',
      runsConc: '—',
      wkts: '—',
      econ: '—',
    });
  });

  const p1Bowl = (p1Obj?.recentBowlingForm || []).slice(-selectedRecentN);
  p1Bowl.forEach((m, idx) => {
    recentSheet.addRow({
      player: p1Name,
      innNum: `Bowl Spell ${idx + 1}`,
      date: m.date || '—',
      format: m.format || '—',
      opp: m.opposition || '—',
      venue: m.venue || '—',
      runs: '—',
      balls: '—',
      sr: '—',
      overs: m.overs != null ? m.overs : '—',
      runsConc: m.runsConceded != null ? m.runsConceded : '—',
      wkts: m.wickets != null ? m.wickets : '—',
      econ: m.economyRate != null ? Number(m.economyRate.toFixed(2)) : '—',
    });
  });

  // Player 2 Recent Batting & Bowling
  const p2Bat = (p2Obj?.recentBattingForm || []).slice(-selectedRecentN);
  p2Bat.forEach((m, idx) => {
    recentSheet.addRow({
      player: p2Name,
      innNum: `Bat Inn ${idx + 1}`,
      date: m.date || '—',
      format: m.format || '—',
      opp: m.opposition || '—',
      venue: m.venue || '—',
      runs: m.runs ?? 0,
      balls: m.ballsFaced ?? 0,
      sr: m.strikeRate != null ? Number(m.strikeRate.toFixed(1)) : '—',
      overs: '—',
      runsConc: '—',
      wkts: '—',
      econ: '—',
    });
  });

  const p2Bowl = (p2Obj?.recentBowlingForm || []).slice(-selectedRecentN);
  p2Bowl.forEach((m, idx) => {
    recentSheet.addRow({
      player: p2Name,
      innNum: `Bowl Spell ${idx + 1}`,
      date: m.date || '—',
      format: m.format || '—',
      opp: m.opposition || '—',
      venue: m.venue || '—',
      runs: '—',
      balls: '—',
      sr: '—',
      overs: m.overs != null ? m.overs : '—',
      runsConc: m.runsConceded != null ? m.runsConceded : '—',
      wkts: m.wickets != null ? m.wickets : '—',
      econ: m.economyRate != null ? Number(m.economyRate.toFixed(2)) : '—',
    });
  });

  autoFitColumns(recentSheet);

  // ── Sheet 6: Chart Underlying Datasets ────────────────────────────────────
  const chartSheet = workbook.addWorksheet('Chart Data', {
    views: [{ state: 'frozen', ySplit: 1 }],
  });

  // Chart 1: Batting Overview Bar Chart
  chartSheet.addRow(['Chart 1: Batting Overview Bar Chart Data']);
  chartSheet.getRow(1).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF3B82F6' } };

  chartSheet.addRow(['Metric', p1Name, p2Name, 'Absolute Difference', 'Leader']);
  applyHeaderStyling(chartSheet.getRow(2));

  const batC = comparison.battingComparison;
  if (batC) {
    const batRows = [
      ['Total Runs', batC.totalRuns1, batC.totalRuns2],
      ['Strike Rate', batC.strikeRate1 != null ? Number(batC.strikeRate1.toFixed(1)) : 0, batC.strikeRate2 != null ? Number(batC.strikeRate2.toFixed(1)) : 0],
      ['Runs / Innings', batC.runsPerInnings1 != null ? Number(batC.runsPerInnings1.toFixed(1)) : 0, batC.runsPerInnings2 != null ? Number(batC.runsPerInnings2.toFixed(1)) : 0],
      ['Boundary Run %', batC.boundaryPercentage1 != null ? Number(batC.boundaryPercentage1.toFixed(1)) : 0, batC.boundaryPercentage2 != null ? Number(batC.boundaryPercentage2.toFixed(1)) : 0],
      ['Fours (4s)', batC.fours1, batC.fours2],
      ['Sixes (6s)', batC.sixes1, batC.sixes2],
    ];

    batRows.forEach(([m, v1, v2]) => {
      const diff = Math.abs(Number(v1) - Number(v2));
      const leader = v1 > v2 ? p1Name : v2 > v1 ? p2Name : 'Tie';
      chartSheet.addRow([m, v1, v2, Number(diff.toFixed(1)), leader]);
    });
  }

  chartSheet.addRow([]); // Blank spacer

  // Chart 2: Bowling Overview Bar Chart
  const bowlChartStart = chartSheet.rowCount + 1;
  chartSheet.addRow(['Chart 2: Bowling Overview Bar Chart Data']);
  chartSheet.getRow(bowlChartStart).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FFEF4444' } };

  chartSheet.addRow(['Metric', p1Name, p2Name, 'Absolute Difference', 'Better Performance (Lower is better for rate/avg)']);
  applyHeaderStyling(chartSheet.getRow(bowlChartStart + 1));

  const bowlC = comparison.bowlingComparison;
  if (bowlC) {
    const bowlRows = [
      ['Total Wickets', bowlC.totalWickets1, bowlC.totalWickets2, false],
      ['Overs Bowled', bowlC.totalOvers1, bowlC.totalOvers2, false],
      ['Economy Rate', bowlC.economyRate1 != null ? Number(bowlC.economyRate1.toFixed(2)) : 0, bowlC.economyRate2 != null ? Number(bowlC.economyRate2.toFixed(2)) : 0, true],
      ['Bowling Average', bowlC.bowlingAverage1 != null ? Number(bowlC.bowlingAverage1.toFixed(2)) : 0, bowlC.bowlingAverage2 != null ? Number(bowlC.bowlingAverage2.toFixed(2)) : 0, true],
      ['Bowling Strike Rate (balls/wkt)', bowlC.bowlingStrikeRate1 != null ? Number(bowlC.bowlingStrikeRate1.toFixed(1)) : 0, bowlC.bowlingStrikeRate2 != null ? Number(bowlC.bowlingStrikeRate2.toFixed(1)) : 0, true],
    ];

    bowlRows.forEach(([m, v1, v2, lowerIsBetter]) => {
      const diff = Math.abs(Number(v1) - Number(v2));
      let leader = 'Tie';
      if (lowerIsBetter) {
        leader = v1 > 0 && (v2 === 0 || v1 < v2) ? p1Name : v2 > 0 && (v1 === 0 || v2 < v1) ? p2Name : 'Tie';
      } else {
        leader = v1 > v2 ? p1Name : v2 > v1 ? p2Name : 'Tie';
      }
      chartSheet.addRow([m, v1, v2, Number(diff.toFixed(2)), leader]);
    });
  }

  chartSheet.addRow([]); // Blank spacer

  // Chart 3: Recent Form Trend Line Chart Data
  const trendChartStart = chartSheet.rowCount + 1;
  chartSheet.addRow(['Chart 3: Recent Form Trend Line Chart Data']);
  chartSheet.getRow(trendChartStart).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF8B5CF6' } };

  chartSheet.addRow([
    'Match Window Index',
    `${p1Name} Date`, `${p1Name} Output (Runs/Wkts)`, `${p1Name} Opposition`,
    `${p2Name} Date`, `${p2Name} Output (Runs/Wkts)`, `${p2Name} Opposition`
  ]);
  applyHeaderStyling(chartSheet.getRow(trendChartStart + 1));

  const isBowlingFocus = comparison.recentFormSummary?.focusDiscipline === 'BOWLING';
  const f1 = isBowlingFocus ? (comparison.player1?.recentBowlingForm || []) : (comparison.player1?.recentBattingForm || []);
  const f2 = isBowlingFocus ? (comparison.player2?.recentBowlingForm || []) : (comparison.player2?.recentBattingForm || []);

  const s1 = f1.slice(-selectedRecentN);
  const s2 = f2.slice(-selectedRecentN);
  const maxL = Math.max(s1.length, s2.length);

  for (let i = 0; i < maxL; i++) {
    const e1 = s1[i];
    const e2 = s2[i];
    const out1 = e1 ? (isBowlingFocus ? e1.wickets : e1.runs) : '—';
    const out2 = e2 ? (isBowlingFocus ? e2.wickets : e2.runs) : '—';

    chartSheet.addRow([
      `Innings/Spell ${i + 1}`,
      e1?.date || '—',
      out1,
      e1?.opposition ? `vs ${e1.opposition}` : '—',
      e2?.date || '—',
      out2,
      e2?.opposition ? `vs ${e2.opposition}` : '—',
    ]);
  }

  autoFitColumns(chartSheet);

  // ── Sheet 7: Context Conditions Analysis (if filter active) ───────────────
  if (selectedOpposition || selectedVenue || comparison.contextInfo?.contextualBatting || comparison.contextInfo?.contextualBowling) {
    const ctxSheet = workbook.addWorksheet('Context Match Conditions', {
      views: [{ state: 'frozen', ySplit: 1 }],
    });

    ctxSheet.addRow([`Isolated Match Conditions: ${selectedOpposition ? `vs ${selectedOpposition}` : ''} ${selectedVenue ? `@ ${selectedVenue}` : ''}`]);
    ctxSheet.getRow(1).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF3B82F6' } };

    ctxSheet.addRow(['Discipline', 'Metric', p1Name, p2Name, 'Context Difference']);
    applyHeaderStyling(ctxSheet.getRow(2));

    const cb = comparison.contextInfo?.contextualBatting;
    if (cb) {
      ctxSheet.addRow(['Context Batting', 'Innings Played', cb.innings1 ?? 0, cb.innings2 ?? 0, Math.abs((cb.innings1 ?? 0) - (cb.innings2 ?? 0))]);
      ctxSheet.addRow(['Context Batting', 'Total Runs', cb.totalRuns1 ?? 0, cb.totalRuns2 ?? 0, Math.abs((cb.totalRuns1 ?? 0) - (cb.totalRuns2 ?? 0))]);
      ctxSheet.addRow(['Context Batting', 'Runs / Innings', cb.runsPerInnings1 ?? '—', cb.runsPerInnings2 ?? '—', '—']);
      ctxSheet.addRow(['Context Batting', 'Strike Rate', cb.strikeRate1 ?? '—', cb.strikeRate2 ?? '—', '—']);
      ctxSheet.addRow(['Context Batting', 'Highest Score', cb.highestScore1 ?? '—', cb.highestScore2 ?? '—', '—']);
    }

    const cbowl = comparison.contextInfo?.contextualBowling;
    if (cbowl) {
      ctxSheet.addRow(['Context Bowling', 'Innings Bowled', cbowl.innings1 ?? 0, cbowl.innings2 ?? 0, Math.abs((cbowl.innings1 ?? 0) - (cbowl.innings2 ?? 0))]);
      ctxSheet.addRow(['Context Bowling', 'Total Wickets', cbowl.totalWickets1 ?? 0, cbowl.totalWickets2 ?? 0, Math.abs((cbowl.totalWickets1 ?? 0) - (cbowl.totalWickets2 ?? 0))]);
      ctxSheet.addRow(['Context Bowling', 'Economy Rate', cbowl.economyRate1 ?? '—', cbowl.economyRate2 ?? '—', '—']);
      ctxSheet.addRow(['Context Bowling', 'Bowling Average', cbowl.bowlingAverage1 ?? '—', cbowl.bowlingAverage2 ?? '—', '—']);
      ctxSheet.addRow(['Context Bowling', 'Bowling Strike Rate', cbowl.bowlingStrikeRate1 ?? '—', cbowl.bowlingStrikeRate2 ?? '—', '—']);
    }

    if (comparison.contextInfo?.contextualSampleSizeWarning) {
      ctxSheet.addRow([]);
      ctxSheet.addRow(['Notice:', comparison.contextInfo.contextualSampleSizeWarning]);
    }

    autoFitColumns(ctxSheet);
  }

  // ── Download File ─────────────────────────────────────────────────────────
  const filename = `CricketAI_Player_Comparison_${sanitizeFilename(p1Name)}_vs_${sanitizeFilename(p2Name)}_${selectedFormat}_${todayStr}.xlsx`;
  await triggerDownload(workbook, filename);
  return returnWorkbook ? { filename, workbook } : filename;
}

// ─── Shared Helper: Populate Individual Player Stats Sheet ───────────────────

function populatePlayerStatsSheet(sheet, playerAnalytics, playerName, format) {
  sheet.addRow([`Detailed Statistics: ${playerName} (${format})`]);
  sheet.getRow(1).font = { name: 'Calibri', size: 11, bold: true, color: { argb: 'FF3B82F6' } };

  sheet.addRow(['Metric Category', 'Metric Name', 'Recorded Value', 'Technical Description']);
  applyHeaderStyling(sheet.getRow(2));

  if (!playerAnalytics) {
    sheet.addRow(['General', 'Status', 'No records found', 'Player has no recorded data in selected format.']);
    return;
  }

  const bat = playerAnalytics.battingSummary;
  const bowl = playerAnalytics.bowlingSummary;

  sheet.addRow(['General', 'Detected Role', playerAnalytics.role || 'UNKNOWN', 'Role derived from career appearance distribution']);
  sheet.addRow(['General', 'Total Batting Innings', playerAnalytics.totalBattingInnings ?? 0, 'Career batting appearances in dataset']);
  sheet.addRow(['General', 'Total Bowling Innings', playerAnalytics.totalBowlingInnings ?? 0, 'Career bowling appearances in dataset']);

  if (bat) {
    sheet.addRow(['Batting', 'Total Runs', bat.totalRuns ?? 0, 'Aggregate format runs scored']);
    sheet.addRow(['Batting', 'Runs per Innings', bat.runsPerInnings != null ? Number(bat.runsPerInnings.toFixed(1)) : '—', 'Total runs / innings']);
    sheet.addRow(['Batting', 'Strike Rate', bat.strikeRate != null ? Number(bat.strikeRate.toFixed(1)) : '—', 'Runs per 100 balls']);
    sheet.addRow(['Batting', 'Fours (4s)', bat.fours ?? 0, 'Total boundary fours']);
    sheet.addRow(['Batting', 'Sixes (6s)', bat.sixes ?? 0, 'Total boundary sixes']);
    sheet.addRow(['Batting', 'Boundary Run %', bat.boundaryPercentage != null ? `${bat.boundaryPercentage}%` : '—', '% of total runs from boundaries']);
    sheet.addRow(['Batting', 'Highest Score', bat.highestScore != null ? `${bat.highestScore}${bat.highestScoreNotOut ? '*' : ''}` : '—', 'Highest score in format']);
    sheet.addRow(['Batting', 'Fifties / Hundreds', `${bat.fifties ?? 0} / ${bat.hundreds ?? 0}`, 'Milestones']);
    sheet.addRow(['Batting', 'Consistency (CV%)', bat.coefficientOfVariation != null ? `${bat.coefficientOfVariation}%` : '—', 'CV% = σ / mean * 100']);
  }

  if (bowl) {
    sheet.addRow(['Bowling', 'Total Wickets', bowl.totalWickets ?? 0, 'Aggregate format wickets taken']);
    sheet.addRow(['Bowling', 'Overs Bowled', bowl.totalOvers ?? 0, 'Total overs bowled']);
    sheet.addRow(['Bowling', 'Runs Conceded', bowl.runsConceded ?? 0, 'Total runs conceded']);
    sheet.addRow(['Bowling', 'Economy Rate', bowl.economyRate != null ? Number(bowl.economyRate.toFixed(2)) : '—', 'Runs per over']);
    sheet.addRow(['Bowling', 'Bowling Average', bowl.bowlingAverage != null ? Number(bowl.bowlingAverage.toFixed(2)) : '—', 'Runs per wicket']);
    sheet.addRow(['Bowling', 'Bowling Strike Rate', bowl.bowlingStrikeRate != null ? `${bowl.bowlingStrikeRate} b/w` : '—', 'Balls bowled per wicket']);
    sheet.addRow(['Bowling', '4W Hauls', bowl.fourWickets ?? 0, 'Spells with 4 wickets']);
    sheet.addRow(['Bowling', '5W Hauls', bowl.fiveWickets ?? 0, 'Spells with 5+ wickets']);
  }
}
