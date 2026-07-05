package org.dromara.djs.warehouse.stat.domain.vo;

import lombok.Data;

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
public class WarehouseIndicatorRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期。 */
    private LocalDate statDate;

    /** 屠宰头数。 */
    private Integer slaughterCount;
    /** 送宰总重。 */
    private BigDecimal slaughterWeight;
    /** 送宰均重。 */
    private BigDecimal avgSlaughterWeight;
    /** 接收重量。 */
    private BigDecimal arriveWeight;
    /** 屠宰率%。 */
    private BigDecimal slaughterRate;

    /** 白条总重。 */
    private BigDecimal barTotalWeight;
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
    /** 果蔬产品生产损耗总重。 */
    private BigDecimal prodLossWeight;
    /** 净菜损耗率%。 */
    private BigDecimal netVegLossRate;
}
