package com.cricketprediction.backend.service;

import com.cricketprediction.backend.config.FlaskServiceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelReportExportServiceTest {

    private ExcelReportExportService exportService;
    private PlayerAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new PlayerAnalyticsService();
        org.springframework.test.util.ReflectionTestUtils.setField(analyticsService, "battingCsvPath", "../ml-service/data/batting_performance.csv");
        org.springframework.test.util.ReflectionTestUtils.setField(analyticsService, "bowlingCsvPath", "../ml-service/data/bowling_performance.csv");
        analyticsService.loadData();

        RestTemplate restTemplate = new RestTemplate();
        FlaskServiceProperties props = new FlaskServiceProperties();
        props.getAutostart().setProjectDir("../ml-service");
        ObjectMapper objectMapper = new ObjectMapper();

        exportService = new ExcelReportExportService(
                analyticsService,
                restTemplate,
                "http://localhost:5000",
                props,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should generate Player Analysis Excel workbook with native DrawingML charts")
    void testExportPlayerAnalysisNativeCharts() throws IOException {
        byte[] xlsxBytes = exportService.exportPlayerAnalysis("V Kohli", "ODI", 10);

        assertThat(xlsxBytes).isNotNull();
        assertThat(xlsxBytes.length).isGreaterThan(10000);

        List<String> zipEntries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zipEntries.add(entry.getName());
            }
        }

        // Verify standard OpenXML DrawingML chart structures
        assertThat(zipEntries).anyMatch(e -> e.startsWith("xl/charts/chart") && e.endsWith(".xml"));
        assertThat(zipEntries).anyMatch(e -> e.startsWith("xl/drawings/drawing") && e.endsWith(".xml"));
        assertThat(zipEntries).contains("xl/workbook.xml");

        // Verify filenames
        String filename = exportService.getPlayerAnalysisFilename("V Kohli", "ODI");
        assertThat(filename).startsWith("CricketAI_Player_Analysis_V_Kohli_ODI_");
        assertThat(filename).endsWith(".xlsx");
    }

    @Test
    @DisplayName("Should generate Player Comparison Excel workbook with native DrawingML charts")
    void testExportPlayerComparisonNativeCharts() throws IOException {
        byte[] xlsxBytes = exportService.exportPlayerComparison("V Kohli", "Babar Azam", "ODI", 10, null, null);

        assertThat(xlsxBytes).isNotNull();
        assertThat(xlsxBytes.length).isGreaterThan(10000);

        List<String> zipEntries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zipEntries.add(entry.getName());
            }
        }

        assertThat(zipEntries).anyMatch(e -> e.startsWith("xl/charts/chart") && e.endsWith(".xml"));
        assertThat(zipEntries).anyMatch(e -> e.startsWith("xl/drawings/drawing") && e.endsWith(".xml"));
        assertThat(zipEntries).contains("xl/workbook.xml");

        String filename = exportService.getPlayerComparisonFilename("V Kohli", "Babar Azam", "ODI");
        assertThat(filename).startsWith("CricketAI_Player_Comparison_V_Kohli_vs_Babar_Azam_ODI_");
        assertThat(filename).endsWith(".xlsx");
    }
}
