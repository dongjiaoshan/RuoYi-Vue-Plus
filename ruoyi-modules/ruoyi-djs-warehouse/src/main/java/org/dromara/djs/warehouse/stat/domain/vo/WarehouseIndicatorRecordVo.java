package org.dromara.djs.warehouse.stat.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 仓库日数据记录视图对象（WMS-STAT-001，邓博 admin row16）。
 *
 * <p>BaseMapperPlus selectVoList 自动 column→field 映射本 VO（与实体同名字段）。
 * BigDecimal 序列化为 String，前端 Number 强转防拼接坑。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Data
@AutoMapper(target = WarehouseIndicatorRecord.class)
public class WarehouseIndicatorRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期。 */
    private LocalDate statDate;

    /** 屠宰头数（当日出栏的猪只头数）。 */
    private Integer slaughterCount;
    /** 送宰总重。 */
    private BigDecimal slaughterWeight;
    /** 送宰均重。 */
    private BigDecimal avgSlaughterWeight;
    /** 接收重量（当日燎毛间完成称重的猪只总重）。 */
    private BigDecimal arriveWeight;
    /** 屠宰率分子（称重 cohort 里有出栏重量那部分的 Σ 到场重）。 */
    private BigDecimal slaughterRateArriveWeight;
    /** 屠宰率分母（同一部分猪的 Σ 出栏重量）。 */
    private BigDecimal slaughterRateBaseWeight;
    /** 屠宰率%。 */
    private BigDecimal slaughterRate;

    /** 白条总重（当日处理完成的全部猪只）。 */
    private BigDecimal barTotalWeight;
    /** 处理完成头数。 */
    private Integer finishedCount;
    /** 处理完成猪只的接收重量之和（白条出品率分母）。 */
    private BigDecimal finishedArriveWeight;
    /** 白条出品率分子（处理完成 ∩ 有接收重量子集的 Σ in_weight）。 */
    private BigDecimal barYieldNumerWeight;
    /** 白条均重。 */
    private BigDecimal avgBarWeight;
    /** 白条出品率%。 */
    private BigDecimal barYieldRate;

    /** 分割白条数（半只计 0.5、整只计 1）。 */
    private BigDecimal cutBarCount;
    /** 预冷损耗。 */
    private BigDecimal precoolLoss;
    /** 分割产品总重。 */
    private BigDecimal cutProductWeight;
    /** 分割白条总重。 */
    private BigDecimal cutBarWeight;
    /** 分割率%。 */
    private BigDecimal cutRate;
    /** 分割间损耗重。 */
    private BigDecimal cutLoss;

    /** 毛菜称量总重。 */
    private BigDecimal vegWeighWeight;
    /** 毛菜损耗重。 */
    private BigDecimal vegLoss;
    /** 毛菜损耗率%。 */
    private BigDecimal vegLossRate;

    /** 发往月台果蔬总重。 */
    private BigDecimal sendPlatformWeight;
    /** 月台接收果蔬总重。 */
    private BigDecimal receivePlatformWeight;
    /** 路损率%。 */
    private BigDecimal transportLossRate;

    /** 果蔬生产领用总重。 */
    private BigDecimal prodPickWeight;
    /** 果蔬生产损耗总重（果蔬领用−退回−录入损耗−饲喂−打包生产使用量，≥0）。 */
    private BigDecimal prodLossWeight;
    /** 净菜损耗率%。 */
    private BigDecimal netVegLossRate;
}
