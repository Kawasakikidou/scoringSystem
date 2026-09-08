package scoring.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 名单行解析器（包内私有实现）。
 *
 * <p>解析规则（详见 docs/接口文档.md「名单解析规则」）：
 * <ul>
 *   <li>学号：恰好 10 位数字的独立数字串（前后非数字）；</li>
 *   <li>姓名：连续汉字段（≥2 字），或「汉字·汉字」多段连接形式（中间点为
 *       ·(U+00B7)/・(U+30FB)/•(U+2022)/．(U+FF0E)，兼容少数民族姓名如「麦麦提·吐尔逊」），
 *       每段 ≥1 字、整名 ≥2 字，内部不含空白；</li>
 *   <li>配对：第一优先「姓名在前、学号在后」——从左到右取第一段其后存在学号的汉字链，
 *       学号取该链之后第一组 10 位数字；整行无此顺序时退化「学号在前、姓名在后」——
 *       取第一组学号及紧随其后的第一段汉字链。间隔 ≤80 字符（容纳其它列）；</li>
 *   <li>文件开头到第一条可解析行之间的非空行视为题头/表头区，不进入「跳过」统计；
 *       首个可解析行之后无法解析的非空行计入跳过并取样例；空行一律忽略。</li>
 * </ul>
 */
final class RosterParser {

    /** 学号：恰好 10 位数字的独立数字串。 */
    static final Pattern STUDENT_NO = Pattern.compile("(?<![0-9])[0-9]{10}(?![0-9])");

    /** 姓名链：连续汉字段，段间可夹一个中间点，最多 3 个点（4 段）。 */
    private static final Pattern NAME_CHAIN = Pattern.compile(
            "[\\u3400-\\u9FFF]{1,}(?:[\\u00B7\\u30FB\\u2022\\uFF0E][\\u3400-\\u9FFF]{1,}){0,3}");

    /** 姓名与学号之间允许的最大间隔字符数（容纳分隔符及其它列）。 */
    private static final int MAX_GAP = 80;

    private RosterParser() {
    }

    /** 解析得到的一行候选人：姓名 + 学号。 */
    record Entry(String name, String studentNo) {
    }

    /** 整个文件/电子表格的解析结果。 */
    record Parsed(List<Entry> entries, int skippedCount, List<String> skippedSamples, String encodingName) {
    }

    /**
     * 读取并解析文本名单文件；编码自动探测（UTF-8 BOM → UTF-8 → GB18030 → ISO-8859-1 兜底）。
     * Excel 文件由 ExcelRosterReader 先行转为行文本后调用 {@link #parseLines}，不经过本方法。
     */
    static Parsed parse(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int offset = 0;
        String baseEncoding = "UTF-8";
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
            baseEncoding = "UTF-8(BOM)";
        }
        String text = decodeStrict(bytes, offset, Charset.forName("UTF-8"));
        String encodingName = baseEncoding;
        if (text == null) {
            text = decodeStrict(bytes, offset, Charset.forName("GB18030"));
            encodingName = text == null ? baseEncoding : "GB18030";
        }
        if (text == null) {
            text = decodeStrict(bytes, offset, Charset.forName("ISO-8859-1"));
            encodingName = "ISO-8859-1(无法识别编码，按单字节读出)";
        }
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\R", -1)) {
            lines.add(raw);
        }
        return parseLines(lines, encodingName);
    }

    /** 严格解码；失败返回 null（不静默产生乱码）。 */
    private static String decodeStrict(byte[] bytes, int offset, Charset charset) {
        try {
            CharBuffer cb = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
            return cb.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * 对“一行一人”的行文本列表执行统一解析管线（文本文件按行拆分后、Excel 按
     * “行内单元格以 Tab 拼接”后，都汇聚到这里；表头跳过/坏行统计/幂等等口径完全一致）。
     */
    static Parsed parseLines(List<String> rawLines, String encodingName) {
        List<Entry> entries = new ArrayList<>();
        List<String> samples = new ArrayList<>();
        int skippedCount = 0;
        boolean dataZoneStarted = false;
        boolean anyParsed = false;
        List<String> headerBlock = new ArrayList<>();
        for (String raw : rawLines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Entry e = parseLine(line);
            if (e != null) {
                dataZoneStarted = true;
                anyParsed = true;
                entries.add(e);
                headerBlock.clear();
            } else if (dataZoneStarted) {
                // 数据区内的坏行：跳过并收集样例
                skippedCount++;
                if (samples.size() < 3) {
                    samples.add(truncate(line, 60));
                }
            } else {
                // 题头/表头区（首个可解析行之前）：暂存；若全部不可解析则计入跳过
                headerBlock.add(line);
            }
        }
        if (!anyParsed) {
            skippedCount = headerBlock.size();
            for (String p : headerBlock) {
                if (samples.size() < 3) {
                    samples.add(truncate(p, 60));
                }
            }
        }
        return new Parsed(entries, skippedCount, samples, encodingName);
    }

    /**
     * 解析单行：返回第一组「姓名 + 10 位学号」；无法解析返回 null。
     *
     * <p>配对策略（与接口文档一致）：
     * <ol>
     *   <li>第一优先「姓名…学号」：按从左到右扫描，取第一段其后（间隔 ≤MAX_GAP）
     *       存在学号的姓名链，学号取该姓名链之后的第一组 10 位数字；</li>
     *   <li>整行没有「姓名…学号」顺序时退化「学号…姓名」：按从左到右取第一组学号，
     *       姓名取该学号之后的第一段汉字链。</li>
     * </ol>
     */
    static Entry parseLine(String line) {
        List<int[]> names = new ArrayList<>();
        Matcher nm = NAME_CHAIN.matcher(line);
        while (nm.find()) {
            String text = nm.group();
            if (text.length() >= 2) {
                names.add(new int[]{nm.start(), nm.end()});
            }
        }
        List<int[]> ids = new ArrayList<>();
        Matcher im = STUDENT_NO.matcher(line);
        while (im.find()) {
            ids.add(new int[]{im.start(), im.end()});
        }
        // 1) 姓名在前：从左到右第一段「其后有学号」的姓名链
        for (int[] n : names) {
            for (int[] id : ids) {
                if (id[0] >= n[1] && id[0] - n[1] <= MAX_GAP) {
                    return new Entry(line.substring(n[0], n[1]), line.substring(id[0], id[1]));
                }
            }
        }
        // 2) 学号在前：从左到右第一组学号 + 其后的第一段姓名链
        for (int[] id : ids) {
            for (int[] n : names) {
                if (n[0] >= id[1] && n[0] - id[1] <= MAX_GAP) {
                    return new Entry(line.substring(n[0], n[1]), line.substring(id[0], id[1]));
                }
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 手动名单维护用：姓名必须为一条完整汉字链（≥2 字、≤64 字，可含 ·・•． 连接段）。 */
    static boolean isValidName(String name) {
        return name != null && name.length() >= 2 && name.length() <= 64
                && NAME_CHAIN.matcher(name).matches();
    }
}
