package scoring.core.dto;

import java.util.List;

/**
 * 名单导入结果报告（CLI 与 GUI 导入后提示用户均直接使用本对象）。
 *
 * @param parsedLines    成功解析出「姓名+10位学号」的数据行数
 * @param skippedLines   无法解析而被跳过的数据行数（题头区与空行不计入，见接口文档）
 * @param added          本次导入新插入的候选人（此前库中无此学号）
 * @param updated        本次导入按学号幂等更新的已有候选人（以本次姓名为准，状态/评分不受影响）
 * @param encoding       实际使用的文本编码（UTF-8 / GB18030，自动探测）
 * @param skippedSamples 被跳过行的样例（最多 3 条、每条约 60 字），用于排查格式问题
 */
public record ImportReport(
        int parsedLines,
        int skippedLines,
        int added,
        int updated,
        String encoding,
        List<String> skippedSamples) {
}
