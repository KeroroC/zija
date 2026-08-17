package com.zija.ai.internal;

import com.zija.ai.internal.exception.KnowledgeExtractionException;
import com.zija.ai.internal.KnowledgeTextExtractor.TextUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeTextExtractorTest {

    private final KnowledgeTextExtractor extractor = new KnowledgeTextExtractor();

    @Test
    void extractsPdfPageByPageWithPageNumbers() throws Exception {
        byte[] bytes = pdf("First page content", "Second page content");

        var units = extractor.extract("application/pdf", bytes);

        assertThat(units).hasSize(2);
        assertThat(units.get(0).pageNumber()).isEqualTo(1);
        assertThat(units.get(0).text()).contains("First page content");
        assertThat(units.get(1).pageNumber()).isEqualTo(2);
        assertThat(units.get(1).text()).contains("Second page content");
    }

    @Test
    void scannedPdfWithoutTextLayerYieldsNoUnits() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            bytes = toBytes(document);
        }

        assertThat(extractor.extract("application/pdf", bytes)).isEmpty();
    }

    @Test
    void extractsMarkdownSectionsByHeadings() {
        String markdown = """
                # 使用说明

                第一步操作说明。

                ## 保养

                保养周期说明。
                """;

        var units = extractor.extract("text/markdown", markdown.getBytes(StandardCharsets.UTF_8));

        assertThat(units).hasSize(2);
        assertThat(units.get(0).sectionPath()).isEqualTo("使用说明");
        assertThat(units.get(0).text()).contains("第一步操作说明");
        assertThat(units.get(1).sectionPath()).isEqualTo("保养");
        assertThat(units.get(1).text()).contains("保养周期说明");
    }

    @Test
    void extractsPlainTextAsSingleUnit() {
        var units = extractor.extract("text/plain", "纯文本内容".getBytes(StandardCharsets.UTF_8));

        assertThat(units).hasSize(1);
        assertThat(units.getFirst().text()).isEqualTo("纯文本内容");
        assertThat(units.getFirst().pageNumber()).isNull();
        assertThat(units.getFirst().sectionPath()).isNull();
    }

    @Test
    void extractsDocxParagraphsAndTables() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("使用方法段落");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("列一头");
            table.getRow(0).getCell(1).setText("列一值");
            bytes = toBytes(document);
        }

        var units = extractor.extract(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);

        assertThat(units).hasSize(1);
        assertThat(units.getFirst().text()).contains("使用方法段落", "列一头", "列一值");
    }

    @Test
    void extractsXlsxSheetBySheetWithSheetNames() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("规格表");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("型号");
            header.createCell(1).setCellValue("重量");
            var data = sheet.createRow(1);
            data.createCell(0).setCellValue("A-100");
            data.createCell(1).setCellValue(2.5);
            bytes = toBytes(workbook);
        }

        var units = extractor.extract(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        assertThat(units).hasSize(1);
        assertThat(units.getFirst().sectionPath()).isEqualTo("规格表");
        assertThat(units.getFirst().text()).contains("型号", "重量", "A-100", "2.5");
    }

    @Test
    void extractsPptxSlideBySlide() throws Exception {
        byte[] bytes;
        try (XMLSlideShow presentation = new XMLSlideShow()) {
            var slide = presentation.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setText("吸尘器使用说明");
            bytes = toBytes(presentation);
        }

        var units = extractor.extract(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", bytes);

        assertThat(units).hasSize(1);
        assertThat(units.getFirst().sectionPath()).isEqualTo("幻灯片 1");
        assertThat(units.getFirst().text()).contains("吸尘器使用说明");
    }

    @Test
    void rejectsUnsupportedMediaType() {
        assertThatThrownBy(() -> extractor.extract("image/jpeg", new byte[]{1, 2, 3}))
                .isInstanceOf(KnowledgeExtractionException.class)
                .hasMessageContaining("不支持的媒体类型");
    }

    /** 生成 n 页、每页一行文字的 PDF。 */
    private static byte[] pdf(String... pageTexts) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            return toBytes(document);
        }
    }

    private static byte[] toBytes(PDDocument document) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] toBytes(org.apache.poi.ooxml.POIXMLDocument document) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.write(out);
            return out.toByteArray();
        }
    }
}
