package com.zija.ai.internal;

import com.zija.ai.internal.exception.KnowledgeExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 附件正文抽取：把可提取文字的格式转换为带定位信息的文本单元。
 *
 * <p>定位信息：PDF 逐页抽取带页码；XLSX 按工作表、PPTX 按幻灯片、Markdown 按标题分节；
 * DOCX/TXT 为单单元。扫描版 PDF（无文字层）与空文档返回空列表，由调用方映射为
 * {@code TEXT_NOT_EXTRACTABLE}。图片/HEIC/旧版 Office 由媒体类型路由直接拒绝。</p>
 */
@Component
class KnowledgeTextExtractor {

    /** 一个可定位文本单元：正文 + 页码（PDF）或章节路径（工作表/幻灯片/标题）。 */
    record TextUnit(String text, Integer pageNumber, String sectionPath) {
    }

    /**
     * 按检测媒体类型抽取正文（格式词汇路由见 {@link KnowledgeSourceFormat}）。
     *
     * @throws KnowledgeExtractionException 解析异常或媒体类型不在首期支持范围内
     */
    List<TextUnit> extract(String mediaType, byte[] content) {
        KnowledgeSourceFormat format = KnowledgeSourceFormat.fromMediaType(mediaType);
        if (format == null) {
            throw new KnowledgeExtractionException("不支持的媒体类型: " + mediaType);
        }
        return switch (format) {
            case PDF -> extractPdf(content);
            case MARKDOWN -> extractMarkdown(content);
            case PLAIN -> extractPlain(content);
            case DOCX -> extractDocx(content);
            case XLSX -> extractXlsx(content);
            case PPTX -> extractPptx(content);
        };
    }

    private List<TextUnit> extractPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<TextUnit> units = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (!text.isBlank()) {
                    units.add(new TextUnit(text, page, null));
                }
            }
            return units;
        } catch (Exception e) {
            throw new KnowledgeExtractionException("PDF 正文抽取失败", e);
        }
    }

    private List<TextUnit> extractMarkdown(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return List.of();
        }
        List<TextUnit> units = new ArrayList<>();
        String currentSection = null;
        StringBuilder buffer = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s+.*")) {
                if (!buffer.isEmpty()) {
                    units.add(new TextUnit(buffer.toString().stripTrailing(), null, currentSection));
                    buffer = new StringBuilder();
                }
                currentSection = trimmed.replaceFirst("^#{1,6}\\s+", "");
            } else if (!trimmed.isEmpty() || !buffer.isEmpty()) {
                buffer.append(line).append('\n');
            }
        }
        if (!buffer.isEmpty()) {
            units.add(new TextUnit(buffer.toString().stripTrailing(), null, currentSection));
        }
        return units;
    }

    private List<TextUnit> extractPlain(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(new TextUnit(text.strip(), null, null));
    }

    private List<TextUnit> extractDocx(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder buffer = new StringBuilder();
            for (var element : document.getBodyElements()) {
                if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        buffer.append(text).append('\n');
                    }
                } else if (element instanceof XWPFTable table) {
                    for (var row : table.getRows()) {
                        for (var cell : row.getTableCells()) {
                            String text = cell.getText();
                            if (text != null && !text.isBlank()) {
                                buffer.append(text).append('\t');
                            }
                        }
                        buffer.append('\n');
                    }
                }
            }
            if (buffer.isEmpty()) {
                return List.of();
            }
            return List.of(new TextUnit(buffer.toString().stripTrailing(), null, null));
        } catch (Exception e) {
            throw new KnowledgeExtractionException("DOCX 正文抽取失败", e);
        }
    }

    private List<TextUnit> extractXlsx(byte[] content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter();
            List<TextUnit> units = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                var sheet = workbook.getSheetAt(i);
                StringBuilder buffer = new StringBuilder();
                for (var row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (var cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (!value.isBlank()) {
                            line.append(value).append('\t');
                        }
                    }
                    if (!line.isEmpty()) {
                        buffer.append(line.toString().stripTrailing()).append('\n');
                    }
                }
                if (!buffer.isEmpty()) {
                    units.add(new TextUnit(buffer.toString().stripTrailing(), null, sheet.getSheetName()));
                }
            }
            return units;
        } catch (Exception e) {
            throw new KnowledgeExtractionException("XLSX 正文抽取失败", e);
        }
    }

    private List<TextUnit> extractPptx(byte[] content) {
        try (XMLSlideShow presentation = new XMLSlideShow(new ByteArrayInputStream(content))) {
            List<TextUnit> units = new ArrayList<>();
            int slideIndex = 0;
            for (var slide : presentation.getSlides()) {
                slideIndex++;
                StringBuilder buffer = new StringBuilder();
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            buffer.append(text).append('\n');
                        }
                    }
                }
                if (!buffer.isEmpty()) {
                    units.add(new TextUnit(buffer.toString().stripTrailing(), null, "幻灯片 " + slideIndex));
                }
            }
            return units;
        } catch (Exception e) {
            throw new KnowledgeExtractionException("PPTX 正文抽取失败", e);
        }
    }
}
