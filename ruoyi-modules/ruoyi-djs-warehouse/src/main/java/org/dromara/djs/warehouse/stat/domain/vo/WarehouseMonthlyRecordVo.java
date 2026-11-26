package org.dromara.djs.warehouse.stat.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.RoundingMode;

/**
 * 仓库月数据记录视图对象（WMS-STAT-001，邓博 admin row18）。
 *
 * <p>{@code @AutoMapper} 生成 entity→VO 的 MapStruct converter，供 {@code selectVoList} /
 * {@code MapstructUtils.convert} 使用。缺它 {@code listMonthly} 转换会抛 ConvertException（接口 500）。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Data
@AutoMapper(target = WarehouseMonthlyRecord.class)
public class WarehouseMonthlyRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份 yyyy-MM。 */
    private String statMonth;

    /** 屠宰头数。 */
    private Integer slaughterCount;
    /** 屠宰出品率%。 */
    private BigDecimal slaughterRate;
    /** 白条出品率%。 */
    private BigDecimal barYieldRate;
    /** 分割出品率%。 */
    private BigDecimal cutYieldRate;

    /** 按源出栏月统计的出栏头数。 */
    private Integer sourceMarketingCount;
    /** 同批源出栏总重（kg）。 */
    private BigDecimal sourceMarketingWeight;
    /** 同批源出栏均重（kg）。 */
    private BigDecimal sourceMarketingAvgWeight;
    /** 同月接收批次白条总重（kg）。 */
    private BigDecimal receivedBarWeight;
    /** 同月接收整猪头数。 */
    private Integer receivedBarPigCount;
    /** 同月接收批次白条均重（kg）。 */
    private BigDecimal receivedBarAvgWeight;
    /** 小程序矩阵口径日白条出品率之和。 */
    private BigDecimal sumDailyBarYieldRate;
    /** 小程序矩阵口径正值日出品率天数。 */
    private Integer positiveBarYieldDays;
    /** 小程序日矩阵同口径月白条出品率（%）。 */
    private BigDecimal matrixBarYieldRate;
    /** 按领用月统计的分割扇数。 */
    private BigDecimal cutBarCount;
    /** 按领用月统计的分割白条总重（kg）。 */
    private BigDecimal cutBarWeight;
    /** 按领用月统计的分割品总重（kg）。 */
    private BigDecimal cutProductWeight;
    /** 月内全部可比日的白条出品率合计（含 0%）。 */
    private BigDecimal barYieldRateSum;
    /** 月内全部可比日的天数（含 0%）。 */
    private Integer barYieldRateDays;
    /** 按领用月统计的分割损耗重（kg）。 */
    private BigDecimal cutLoss;
    /** 同批分割领用总重（kg）。 */
    private BigDecimal cutLossPickupWeight;
    /** 分割损耗率（%，分母 0 时为空）。 */
    private BigDecimal cutLossRate;
    /** 果蔬生产毛领用总重（kg）。 */
    private BigDecimal vegProductionPickWeight;
    /** 果蔬毛菜实际入库重量（kg）。 */
    private BigDecimal vegRawInboundWeight;
    /** 果蔬实际配送离库重量（kg）。 */
    private BigDecimal vegDeliveryOutWeight;
    /** 有效果蔬生产份数。 */
    private Integer vegProducedPortionCount;

    /** 有可比需求的各日满足率的平均（%）。 */
    private BigDecimal vegDemandFulfillmentRate;
    /** 无原始份数而排除的公斤需求条数。 */
    private Integer vegDemandKgExcludedCount;
    /** 月果蔬打包损耗率（%）。 */
    private BigDecimal vegPackLossRate;
    /** 按原生产月归集的果蔬损坏份数。 */
    private Integer vegDamagedPortionCount;
    /** 月果蔬损坏率（%）。 */
    private BigDecimal vegDamageRate;
    /** 月内逐日领用损耗之和（kg）。 */
    private BigDecimal vegProductionLossWeight;
    /** 月果蔬生产损耗率（%）。 */
    private BigDecimal vegProductionLossRate;
    /** 月果蔬生产规格合格率（%）。 */
    private BigDecimal vegSpecQualifiedRate;
    /** 当月超规件平均超出百分比（%）。 */
    private BigDecimal vegAvgOverspecPercent;
    /** 月末果蔬原料库存（kg），无快照为空。 */
    private BigDecimal vegEndStockWeight;
    /** 期末库存所用快照日期。 */
    private LocalDate vegEndStockDate;
    /** 月内重量合格且未标损坏的份数。 */
    private Integer vegUndamagedQualifiedCount;

    public BigDecimal getBarYieldRate() {
        return barYieldRate == null ? null : barYieldRate.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getCutYieldRate() {
        return cutYieldRate == null ? null : cutYieldRate.setScale(2, RoundingMode.HALF_UP);
    }
}
