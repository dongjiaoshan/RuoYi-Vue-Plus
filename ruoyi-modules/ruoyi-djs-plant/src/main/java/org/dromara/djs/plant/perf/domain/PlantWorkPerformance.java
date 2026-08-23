package org.dromara.djs.plant.perf.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 种植班组绩效结算实体（PLT-PERF-001）。
 *
 * <p>对应表 {@code t_plant_work_performance}（baseline 占位 V202605200902，
 * 由本 ticket migration {@code V202606141400} ALTER 对齐 doc/11 §1.13）。</p>
 *
 * <p>结算口径：按 <b>班组(team_id) × 作物(crop_id) × 月份(stat_month)</b> 聚合
 * {@code t_plant_plant_details.actual_yield}（采摘量，公斤）× 作物单价快照
 * {@code unit_price_snapshot}（元/公斤，结算瞬间读 {@code t_plant_crop_info.pick_unit_price}）
 * = 应付绩效金额 {@code performance_amount}。
 * 后续改单价不回写历史月（只认快照值）。</p>
 *
 * <p>绩效行由 {@code generate(statMonth)} 系统生成（先软删该月再聚合 INSERT，幂等），
 * 不支持手工 add/edit/remove。baseline 表的 {@code people_id} 列 doc/11 §1.13 无定义，
 * 本实体不映射（保留 DB 列不删，避免误用）。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_work_performance")
public class PlantWorkPerformance extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（snowflake）。
     */
    @TableId
    private Long id;

    /**
     * 统计月份（VARCHAR(7)，如 "2026-04"）。
     */
    private String statMonth;

    /**
     * 班组 ID（FK → t_plant_work_team.id；聚合源取 details.harvest_by 采摘班组）。
     */
    private Long teamId;

    /**
     * 作物 ID（FK → t_plant_crop_info.id）。
     */
    private Long cropId;

    /**
     * 产品 ID（V6 row20，FK → t_warehouse_product_info.id）。绩效按「作物 × 产品」结算，
     * 单价取该产品在作物产品配置里的绩效金额。作物没配产品时为 null（回落作物级单价）。
     */
    private Long productId;

    /**
     * 绩效百分比（0-100 整数，%）—— V6 row107，来自采摘录入时填的
     * {@code t_warehouse_handle_record.perf_percent}，同产品的不同百分比各占一行。
     */
    private Integer perfPercent;

    /**
     * 采摘总量（公斤，= SUM(actual_yield)）。
     */
    private BigDecimal pickWeight;

    /**
     * 单价快照（元/公斤，结算瞬间读 {@code t_plant_crop_info.pick_unit_price}）。
     */
    private BigDecimal unitPriceSnapshot;

    /**
     * 应付绩效金额（元，= pickWeight(公斤) × perfPercent% × unitPriceSnapshot(元/公斤)）。
     */
    private BigDecimal performanceAmount;

    /**
     * 绩效规则描述（如 "1.00 元/公斤"）。
     */
    private String performanceRule;

    /**
     * 备注（baseline DDL 含 remark 列，BaseEntity 不带 → 实体显式声明）。
     */
    private String remark;

    /**
     * 软删标记（{@code 0}=正常 / {@code 2}=已删；走 MP {@code @TableLogic}，
     * generate 幂等清理时把同月旧行置 '2'）。
     */
    @TableLogic
    private String delFlag;
}
