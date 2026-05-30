package org.dromara.djs.breed.med.domain;

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
 * 药品领用台账实体（BRD-MED-002）。
 *
 * <p>对应表 {@code t_breed_medicine_usage}：领用 / 退回 / 损耗共表，由
 * {@code usageType} 区分（use=领用 / return=退回 / loss=损耗）。
 * 库存扣减 / 归还落在 {@link MedBatch#getQuantity()}：</p>
 * <ul>
 *   <li>{@code use} / {@code loss}：批次 {@code quantity -= usageQty}（wrapper-only update，
 *       WHERE quantity >= qty 保证原子防超领）；</li>
 *   <li>{@code return}：批次 {@code quantity += usageQty}；</li>
 *   <li>软删（{@code del_flag='1'}）走基类 {@code DjsBaseServiceImpl#softDelete}，
 *       <b>不回滚库存</b>（已领用历史不可逆，对账不允许凭空消失）。</li>
 * </ul>
 *
 * <p>{@code useDate} 是<b>业务日期</b>（领用 / 退回 / 损耗实际发生日），不是 create_time。
 * BRD-MED-003 用药治疗台账按此字段查"近 3 天已领可用药品"。</p>
 *
 * @author djs
 * @since BRD-MED-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_breed_medicine_usage")
public class MedUsage extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 药品批次 ID（引用 {@link MedBatch#getId()}）。
     */
    private Long batchId;

    /**
     * 药品 ID（冗余字段，引用 {@link Medicine#getId()}；便于按药品维度报表）。
     */
    private Long medicineId;

    /**
     * 类型：use=领用 / return=退回 / loss=损耗（字典 djs_med_pick_action）。
     */
    private String usageType;

    /**
     * 数量（与批次 {@link MedBatch#getQuantity()} 对齐 DECIMAL(18,3)，大于 0）。
     */
    private BigDecimal usageQty;

    /**
     * 业务日期（领用 / 退回 / 损耗发生日，BRD-MED-003 按此过滤）。
     */
    private LocalDate useDate;

    /**
     * 关联栏位（可选）。
     */
    private Long relatedPenId;

    /**
     * 关联猪只（可选，单只用药场景）。
     */
    private Long pigId;

    /**
     * 关联用药计划 ID（可选，BRD-MD-003 {@code t_farm_production_cycle_config} 中的 schedule）。
     */
    private Long scheduleId;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列（未删 0；软删时由 {@code DjsMetaObjectHandler} 写入 id）。
     */
    private Long delUnique;

}
