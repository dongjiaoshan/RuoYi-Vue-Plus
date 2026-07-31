package org.dromara.djs.store.returns.domain.vo;

import cn.idev.excel.annotation.ExcelIgnore;
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
    @ExcelProperty("退回日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    /** 门店 ID（snowflake，Jackson 序列化为 string）。导出只要门店名，裸 ID 不进表头。 */
    @ExcelIgnore
    private Long storeId;

    /** 门店名称（StoreMapper 批量回填，避免 N+1）。 */
    @ExcelProperty("退回门店")
    private String storeName;

    /** 退回品种数（该组 distinct product_id 计数）。 */
    @ExcelProperty("退回品种数")
    private int productKindCount;

    /** 退回重量合计（row57：仅按重量计（kg/公斤单位）行的 Σ goods_weight，份数产品不计入）。 */
    @ExcelProperty("退回重量")
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

    /**
     * 已确认行数（该组 {@code return_status='received'} 计数）。
     *
     * <p>row178：确认时间 / 确认人取的是该组「最近一条已确认行」，只要有 1 条确认过就会填上，
     * 部分确认（如 3/4）与全部确认在外层看不出差别 —— 仍是 pending 的行既没入库、外层也不留痕迹。
     * 与 {@link #totalCount} 组成「确认进度 n/m」，未全部确认时前端标警告色。</p>
     */
    @ExcelIgnore
    private int confirmedCount;

    /** 总行数（该组全部退回行，含 pending 与 received）。 */
    @ExcelIgnore
    private int totalCount;

    /** 确认进度（{@code confirmedCount/totalCount}，导出列；列表页由前端按两个计数渲染）。 */
    @ExcelProperty("确认进度")
    private String confirmProgress;

    /** 确认时间（该组最近一条已确认行 confirm_time，无已确认行则为空）。 */
    @ExcelProperty("确认时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    /** 确认人 ID（该组最近一条已确认行 confirm_user_id，翻译用）。导出只要姓名，裸 ID 不进表头。 */
    @ExcelIgnore
    private Long confirmUser;

    /**
     * 确认人姓名。
     *
     * <p>{@code @Translation} 只在 Jackson 序列化链上生效，FastExcel 导出直接读字段，不走 Jackson，
     * 光靠注解导出会整列空 —— 所以 service 侧（{@code buildStoreDailyList}）已按 confirmUser 预填好；
     * 注解保留给列表接口（预填值与翻译结果同源，覆盖无副作用）。</p>
     */
    @ExcelProperty("确认人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "confirmUser")
    private String confirmUserName;
}
