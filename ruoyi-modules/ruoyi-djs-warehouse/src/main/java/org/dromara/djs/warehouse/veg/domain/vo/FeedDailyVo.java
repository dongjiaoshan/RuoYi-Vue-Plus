package org.dromara.djs.warehouse.veg.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 有机饲喂**按日汇总**行 VO（admin 行199 列表 / mp 行268 卡片共用）。
 *
 * <p>一天一行：当日所有送往有机饲喂的记录（毛菜间 + 仓库两种来源）合并成一条，
 * 明细留给「查看详情」用 {@code FeedRecordVo} 单独拉。</p>
 *
 * @author djs
 */
@Data
@ExcelIgnoreUnannotated
public class FeedDailyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 饲喂日期（仅日期，不含时分秒）。
     */
    @ExcelProperty(value = "日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate feedDate;

    /**
     * 当日总重量(kg)，保留三位小数。当日所有来源求和。
     */
    @ExcelProperty(value = "总重量")
    private BigDecimal totalWeight;

    /**
     * 当日明细条数（前端「查看详情」前可预判空态；不作为列展示）。
     */
    private Integer detailCount;

    /**
     * 仓库确认框数（可小数）。未确认为 {@code null} → 前端显示「-」。
     */
    @ExcelProperty(value = "仓库确认框数")
    private BigDecimal boxCount;

    /**
     * 仓库确认人 user_id。
     */
    private Long confirmUserId;

    /**
     * 仓库确认人昵称（ruoyi USER_ID_TO_NICKNAME 翻译）。
     */
    @ExcelProperty(value = "仓库确认人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "confirmUserId")
    private String confirmUserName;
}
