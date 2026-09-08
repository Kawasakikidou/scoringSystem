package scoring.core;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Excel(.xlsx/.xls) 名单读取器（包内私有实现）。
 *
 * <p>读取原则：逐 sheet → 逐行 → 单元格文本 → 以制表符拼接为一行 → 交给
 * {@link RosterParser#parseLines} 统一解析管线。**不在 Excel 路径另写一套姓名/学号解析**，
 * 表头跳过、坏行统计、幂等更新等口径与文本文件完全一致。
 *
 * <p>单元格取值规则：
 * <ul>
 *   <li>数字单元格：{@code BigDecimal.valueOf(double)} 后 toPlainString()，禁止科学计数法；
 *       整数不带小数尾（如 2023000001）；</li>
 *   <li>数字单元格按 Excel 日期格式时：按单元格显示格式取文本（DataFormatter）；</li>
 *   <li>文本/布尔：原样（布尔转 TRUE/FALSE）；公式：取其缓存结果按上述规则处理；</li>
 *   <li>空单元格（含合并区域非左上角、报错格、空行）：一律按空串/跳过。</li>
 * </ul>
 */
final class ExcelRosterReader {

    private ExcelRosterReader() {
    }

    static RosterParser.Parsed read(Path file) throws IOException {
        // 按魔数标注类型（与扩展名无关，展示口径准确）
        String kind;
        try (InputStream sniff = Files.newInputStream(file)) {
            byte[] h = sniff.readNBytes(4);
            boolean zip = h.length >= 2 && h[0] == 'P' && h[1] == 'K';
            kind = zip ? "Excel(.xlsx)" : "Excel(.xls)";
        }
        List<String> lines = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file);
             Workbook wb = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                int first = sheet.getFirstRowNum();
                int last = sheet.getLastRowNum();
                for (int r = first; r >= 0 && r <= last; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    List<String> cells = new ArrayList<>();
                    boolean anyText = false;
                    int lastCell = row.getLastCellNum(); // -1 表示无单元格
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c);
                        String text = cellText(cell, formatter);
                        cells.add(text);
                        if (!text.isEmpty()) {
                            anyText = true;
                        }
                    }
                    if (anyText) {
                        lines.add(String.join("\t", cells));
                    }
                }
            }
        }
        return RosterParser.parseLines(lines, kind);
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // 数字单元格内嵌日期等格式 → 按显示格式取文本（兜底）
                    return formatter.formatCellValue(cell);
                }
                double dv = cell.getNumericCellValue();
                if (!Double.isFinite(dv)) {
                    return "";
                }
                // 禁止科学计数法：2.02312E9 → 2023120000；纯整数不带小数尾
                return BigDecimal.valueOf(dv).stripTrailingZeros().toPlainString();
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            default:
                // BLANK / ERROR / 其它 → 空串（合并区域非左上角为 null，也在 cell==null 分支处理）
                return "";
        }
    }
}
