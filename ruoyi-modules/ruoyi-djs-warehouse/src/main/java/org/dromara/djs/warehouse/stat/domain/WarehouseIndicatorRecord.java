package org.dromara.djs.warehouse.stat.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 仓库日数据记录实体（WMS-STAT-001，表 {@code t_warehouse_indicator_record}，邓博 admin row16）。
 *
 * <p>每个 tenant_id + stat_date 一行；由 {@link org.dromara.djs.warehouse.stat.job.WarehouseStatJob}
 * 每晚跑批落盘 T-1 全量仓库日指标（UPSERT 幂等）。admin「仓库日报表」只读列表读本表。</p>
 *
 * <p><b>率/均值类列可空 + ALWAYS</b>：分母≤0 时落 NULL（无意义不造假）。MP updateById 默认跳 null
 * 会把重算成 NULL 的列留旧脏值，故率/均值类列标 {@code @TableField(updateStrategy = ALWAYS)}
 * 让重算的 NULL 真覆盖旧值。重量/头数类有 DEFAULT 0、恒非 null，不需要 ALWAYS。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_indicator_record")
public class WarehouseIndicatorRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计日期（T-1）。 */
    private LocalDate statDate;

    // ---- 屠宰 / 送宰段 ----
    /** 屠宰头数（当日燎毛间接收的猪只头数 = 当日 burn 记录数）。 */
    private Integer slaughterCount;
    /** 送宰总重（当日出栏送宰猪总重 + 当日外购猪总重）。 */
    private BigDecimal slaughterWeight;
    /** 送宰均重（送宰总重/屠宰头数；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgSlaughterWeight;
    /** 接收重量（当日燎毛间称重总重 = Σ burn.arrive_weight）。 */
    private BigDecimal arriveWeight;
    /** 屠宰率%（接收重量/送宰总重×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal slaughterRate;

    // ---- 白条段 ----
    /** 白条总重（当日白条库入库白条产品总重 = Σ burn.burn_weight）。 */
    private BigDecimal barTotalWeight;
    /** 白条均重（白条总重/屠宰头数；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgBarWeight;
    /** 白条出品率%（白条总重/送宰总重×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal barYieldRate;

    // ---- 分割段 ----
    /** 分割白条数（当日转入分割车间的白条数量；半只计 0.5、整只计 1，故为小数）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal cutBarCount;
    /** 预冷损耗（当日 loss_flow precool_loss 之和）。 */
    private BigDecimal precoolLoss;
    /** 分割产品总重（当日分割车间产出产品重之和）。 */
    private BigDecimal cutProductWeight;
    /** 分割白条总重（当日白条出库总重 = Σ cut_record.pickup_weight）。 */
    private BigDecimal cutBarWeight;
    /** 分割率%（分割产品总重/分割白条总重×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal cutRate;
    /** 分割间损耗重（当日 loss_flow cut_loss 之和）。 */
    private BigDecimal cutLoss;

    // ---- 毛菜段 ----
    /** 毛菜称量总重（当日采摘称重之和 = Σ vegetable_handle.picked_weight）。 */
    private BigDecimal vegWeighWeight;
    /** 毛菜损耗重（当日 loss_flow veg_handle_loss 之和）。 */
    private BigDecimal vegLoss;
    /** 毛菜损耗率%（毛菜损耗重/毛菜称量总重×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal vegLossRate;

    // ---- 果蔬月台段 ----
    /** 发往月台果蔬总重（当日 Σ vegetable_handle.send_platform_weight）。 */
    private BigDecimal sendPlatformWeight;
    /** 月台接收果蔬总重（当日 Σ veg_receive.weight 自产 receive_type=1）。 */
    private BigDecimal receivePlatformWeight;
    /** 路损率%（(发往月台量−月台接收量)/发往月台量×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal transportLossRate;

    // ---- 净菜生产段 ----
    /** 果蔬生产领用总重（当日 stock_flow prod_pick_out 之和）。 */
    private BigDecimal prodPickWeight;
    /** 果蔬产品生产损耗总重（当日 loss_flow production_loss 之和）。 */
    private BigDecimal prodLossWeight;
    /** 净菜损耗率%（(生产损耗+录入损耗)/(生产领用−生产退回)×100；分母≤0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netVegLossRate;

    /** 软删标志（业务表必含）。 */
    @TableLogic
    private String delFlag;
    /** 软删唯一标识。 */
    private Long delUnique;
}
