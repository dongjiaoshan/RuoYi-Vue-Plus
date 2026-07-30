package org.dromara.djs.store.returns.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
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
 * 门店退回（门店→仓库）admin 外层「门店 + 当日」汇总行 VO（STORE-RETURN-UNIFY-001）。
 *
 * <p>仓库「退货记录」主从视图外层：一行 = 某门店某天的全部退回行聚合（混合 pending / received）；
 * 点行下钻该门店当天逐条明细（复用 {@code /djs/store/return/list} 带 storeId + returnDateFrom/To）。</p>
 *
 * <p>分组维度 = 退回日期（{@code return_date} 截到天）+ {@code store_id}，仅 {@code store_to_warehouse} 方向。
 * 重量合计：退回 = Σ{@code goods_weight}；确认 = Σ{@code received_weight}。品种数 = distinct {@code product_id}。</p>
 *
 * @author djs
 * @since STORE-RETURN-UNIFY-001
 */
@Data
public class StoreReturnStoreDailyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退回日期（return_date 截到天）。 */
    @ExcelProperty("退货日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    /** 门店 ID（snowflake，Jackson 序列化为 string）。 */
    private Long storeId;

    /** 门店名称（StoreMapper 批量回填，避免 N+1）。 */
    @ExcelProperty("退货门店")
    private String storeName;

    /** 退回品种数（该组 distinct product_id 计数）。 */
    @ExcelProperty("退货品种数")
    private int productKindCount;

    /** 退回重量合计（row57：仅按重量计（kg/公斤单位）行的 Σ goods_weight，份数产品不计入）。 */
    @ExcelProperty("退货重量")
    private BigDecimal returnWeightTotal;

    /** 确认重量合计（row57：仅按重量计（kg/公斤单位）行的 Σ received_weight，份数产品与未确认行不计入）。 */
    @ExcelProperty("确认重量")
    private BigDecimal confirmWeightTotal;

    /** 重量差异合计（row57：returnWeightTotal - confirmWeightTotal，两侧同为 kg 行口径）。 */
    @ExcelProperty("重量差异")
    private BigDecimal weightDiffTotal;

    /** 非重量产品退回重量合计（row57：份数产品（非 kg 单位）行的 Σ received_weight，即仓库称重）。 */
    @ExcelProperty("非重量产品退回重量")
    private BigDecimal nonWeightReturnWeightTotal;

    /** 确认时间（该组最近一条已确认行 confirm_time，无已确认行则为空）。 */
    @ExcelProperty("确认时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    /** 确认人 ID（该组最近一条已确认行 confirm_user_id，翻译用）。 */
    private Long confirmUser;

    /** 确认人姓名（USER_ID_TO_NICKNAME 翻译，契约 4.5）。 */
    @ExcelProperty("确认人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "confirmUser")
    private String confirmUserName;
}
