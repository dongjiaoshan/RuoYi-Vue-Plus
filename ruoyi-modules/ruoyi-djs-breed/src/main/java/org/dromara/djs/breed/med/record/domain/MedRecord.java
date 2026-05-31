package org.dromara.djs.breed.med.record.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用药治疗流水实体（BRD-MED-003）。
 *
 * <p>对应表 {@code t_breed_medicine_record}。{@code drugType} 区分三态：</p>
 * <ul>
 *   <li>{@code 1}：单只用药 —— {@code pigId / earNo} 必填，{@code masterId} 为 NULL；</li>
 *   <li>{@code 2}：批量用药 master —— {@code pigId / earNo} 为 NULL，作为批次汇总行；</li>
 *   <li>{@code 3}：批量用药 detail —— {@code pigId / earNo} 必填，{@code masterId}
 *       回指本批次的 master 记录 id。</li>
 * </ul>
 *
 * <p>库存扣减：service 端单事务内对 {@code t_breed_medicine_batch.quantity} 做
 * 原子 {@code quantity -= dosage}（单只）或 {@code quantity -= dosage × N}（批量），
 * 同时校验 {@code 0 ≤ quantity ≤ stock_qty}（防退回累计污染）。</p>
 *
 * <p>软删走基类 {@code DjsBaseServiceImpl#softDelete}（同 {@code MedUsage}）。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_breed_medicine_record")
public class MedRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 用药日期（业务日期，非 create_time）。
     */
    private LocalDateTime useDate;

    /**
     * 猪只 ID（drug_type=1 / 3 必填；drug_type=2 master 行为 NULL）。
     */
    private Long pigId;

    /**
     * 猪只耳号冗余（同上必填规则；冗余便于报表 / 列表展示）。
     */
    private String earNo;

    /**
     * 批量用药 master id（drug_type=3 detail 行回指）。
     */
    private Long masterId;

    /**
     * 1=单只 / 2=批量 master / 3=批量 detail（字典 djs_drug_type）。
     */
    private Integer drugType;

    /**
     * 用药类型（djs_medicine_use_type: health/treatment/vaccine）。
     */
    private String medicineType;

    /**
     * 疫苗类型（djs_vaccine_type，仅免疫类用药时填写，可空）。
     */
    private String vaccineType;

    /**
     * 用药原因（djs_medicine_reason，可选）。
     */
    private String medicineReason;

    /**
     * 用药方式（djs_medicine_way）。
     */
    private String medicineWay;

    /**
     * 药品 ID（FK → {@code t_breed_medicine_info.id}）。
     */
    private Long medicineId;

    /**
     * 药品名冗余（防药品改名影响历史）。
     */
    private String medicineName;

    /**
     * 批次 ID（FK → {@code t_breed_medicine_batch.id}，必填，
     * 从 3 天内已领批次中选）。
     */
    private Long batchId;

    /**
     * 领用记录 ID（FK → {@code t_breed_medicine_usage.id}，
     * 追溯 3 天领用源头；V1 可选）。
     */
    private Long usageId;

    /**
     * 用药 schedule ID（FK → {@code t_farm_production_cycle_config.id}，
     * V1 仅记录不强校验归属，预留 V2 闭环统计执行率）。
     */
    private Long scheduleId;

    /**
     * 用药剂量。
     */
    private BigDecimal medicineDosage;

    /**
     * 操作人 user_id（FK → {@code sys_user.user_id}，ADR-0007）。
     */
    private Long operatorId;

    /**
     * 操作人冗余姓名（{@code sys_user.nick_name} 快照）。
     */
    private String operatorName;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
