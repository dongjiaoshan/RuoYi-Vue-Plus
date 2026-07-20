package org.dromara.djs.warehouse.shipment.returnpkg.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 退货管理 admin 外层「门店 + 当日」汇总行 VO（图 153）。
 *
 * <p>外层列表从「逐条明细平铺」改为「门店 + 退货日期」主从视图：一行 = 某门店某天的全部退货行聚合。
 * 点「查看详情」下钻该门店当天逐条明细（复用 {@code /list} 带 storeId + applyDateFrom/To）。</p>
 *
 * <p>分组维度 = 退货日期（{@code apply_time} 截到天）+ {@code store_id}（混合 pending / confirmed）。
 * 表 {@code t_warehouse_return_product} 无 return_qty 列，品种数口径 = distinct product_id 计数。</p>
 *
 * @author djs
 * @since 0614-153
 */
@Data
public class ReturnStoreDailyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退货日期（apply_time 截到天）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    /** 门店 ID（snowflake，Jackson 序列化为 string）。 */
    private Long storeId;

    /** 门店名称（StoreMapper 批量回填，避免 N+1）。 */
    private String storeName;

    /** 退货品种数（该组 distinct product_id 计数）。 */
    private int productKindCount;

    /** 退货重量合计（Σ return_weight）。 */
    private BigDecimal returnWeightTotal;

    /** 确认重量合计（Σ confirm_weight，未确认行不计入）。 */
    private BigDecimal confirmWeightTotal;

    /** 重量差异合计（returnWeightTotal - confirmWeightTotal）。 */
    private BigDecimal weightDiffTotal;

    /** 确认时间（该组最近一条已确认行的 confirm_time，无已确认行则为空）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    /** 确认人 ID（该组最近一条已确认行的 confirm_user，翻译用，无已确认行则为空）。 */
    private Long confirmUser;

    /** 确认人姓名（USER_ID_TO_NICKNAME 翻译，契约 4.5）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "confirmUser")
    private String confirmUserName;
}
