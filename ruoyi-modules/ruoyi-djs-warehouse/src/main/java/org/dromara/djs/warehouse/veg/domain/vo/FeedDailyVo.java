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
import java.util.Date;

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
     * 仓库确认框数（最多 1 位小数）。未确认为 {@code null} → 前端显示「-」。
     *
     * <p>列类型是 {@code DECIMAL(10,2)}（会序列化成 {@code "1.50"}），小数位上限由录入校验
     * {@code FeedDailyConfirmBo} 的 {@code @Digits(integer = 8, fraction = 1)} 收口，
     * 两端展示统一去尾零（禁取整，否则 1.5 会显示成 2）。</p>
     */
    @ExcelProperty(value = "仓库确认框数")
    private BigDecimal boxCount;

    /**
     * 仓库确认人 user_id。
     */
    private Long confirmUserId;

    /**
     * 仓库确认（处理）日期。未确认为 {@code null}。
     *
     * <p>取自 {@code t_warehouse_feed_daily_confirm.confirm_time}，mp 卡片第三行左侧「处理日期」用。
     * 只对外给日期部分（时分秒对业务无意义，甲方要的是「哪天处理的」）。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date confirmTime;

    /**
     * 仓库确认人昵称（ruoyi USER_ID_TO_NICKNAME 翻译）。
     */
    @ExcelProperty(value = "仓库确认人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "confirmUserId")
    private String confirmUserName;
}
