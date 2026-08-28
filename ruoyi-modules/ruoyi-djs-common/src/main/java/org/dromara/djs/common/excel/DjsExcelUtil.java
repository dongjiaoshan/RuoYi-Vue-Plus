package org.dromara.djs.common.excel;

import cn.idev.excel.FastExcel;
import cn.idev.excel.write.builder.ExcelWriterSheetBuilder;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.excel.convert.ExcelBigNumberConvert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * djs 业务侧 Excel 导出补充工具。
 *
 * <p>只补一件 ruoyi 自带 {@code ExcelUtil} 没给出口的能力：<b>同一个 VO 被多个导出端点共用时，
 * 按端点排除掉个别列</b>。ruoyi 的 {@code exportExcel} 全部按 {@code Class} 出全列，
 * 要按端点裁列只能改 ruoyi 源码，而 ruoyi 自带模块不许动（CLAUDE.md §6 强约束 1），故落在 djs 侧。</p>
 *
 * <p>落地场景（V6-R139）：{@code StockFlowVo} 被 入库记录 / 出库记录 / 出入库流水 三个导出端点共用，
 * 甲方只要求入库记录加「供应商」列；而 {@code supplier_id} 只在入库方向写入，
 * 出库导出带上这列恒空、是纯垃圾列。</p>
 *
 * @author djs
 * @since V6-R139
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DjsExcelUtil {

    /**
     * 导出 Excel，并排除指定字段列。
     *
     * <p>写出参数与 {@code ExcelUtil.exportExcel} 保持一致（自动列宽 + 大数值防失真），
     * 只多一个 {@code excludeColumnFieldNames}。</p>
     *
     * @param list       数据
     * @param sheetName  sheet 名（同时用作下载文件名）
     * @param clazz      带 Excel 注解的类型
     * @param excludes   要排除的<b>字段名</b>（不是列标题），如 {@code List.of("supplierName")}
     * @param response   响应
     */
    public static <T> void exportExcelExcluding(List<T> list, String sheetName, Class<T> clazz,
                                                Collection<String> excludes, HttpServletResponse response) {
        try {
            resetResponse(sheetName, response);
            exportExcelExcluding(list, sheetName, clazz, excludes, response.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("导出Excel异常", e);
        }
    }

    /**
     * 导出 Excel 到流，并排除指定字段列。
     */
    public static <T> void exportExcelExcluding(List<T> list, String sheetName, Class<T> clazz,
                                                Collection<String> excludes, OutputStream os) {
        ExcelWriterSheetBuilder builder = FastExcel.write(os, clazz)
            .autoCloseStream(false)
            .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
            .registerConverter(new ExcelBigNumberConvert())
            .sheet(sheetName);
        if (excludes != null && !excludes.isEmpty()) {
            builder.excludeColumnFieldNames(excludes);
        }
        builder.doWrite(list);
    }

    /**
     * 设置下载响应头（与 ruoyi {@code ExcelUtil.resetResponse} 同口径）。
     */
    private static void resetResponse(String sheetName, HttpServletResponse response) {
        String filename = encodingFilename(sheetName);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-disposition", "attachment; filename=" + filename
            + "; filename*=utf-8''" + filename);
    }

    private static String encodingFilename(String filename) {
        return URLEncoder.encode(StringUtils.blankToDefault(filename, "export"), StandardCharsets.UTF_8)
            + "_" + System.currentTimeMillis() + ".xlsx";
    }
}
