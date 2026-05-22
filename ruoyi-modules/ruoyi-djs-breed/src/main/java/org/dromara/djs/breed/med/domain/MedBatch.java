package org.dromara.djs.breed.med.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 药品批次实体（BRD-MED-001）。
 *
 * <p>对应表 {@code t_breed_medicine_batch}：药品入库批次明细（一药多批次场景）。
 * 实际 V1 客户优先按"单批次平铺在 {@link Medicine}"使用（doc/06 BRD-MED-001 落地形态），
 * 本批次表 admin 端 CRUD 作为"扩展能力"暴露给愿意精细化的客户，
 * 字段语义独立 {@link Medicine}：{@code batch_no} 为批次唯一码，{@code quantity}
 * 为该批次剩余库存（V2 BRD-MED-002 领用扣减时择 FIFO 批次扣减）。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（同 {@link Medicine}）。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_breed_medicine_batch")
public class MedBatch extends TenantEntity implements DjsBaseServiceImpl.SoftDeletable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 药品 ID（引用 {@link Medicine#getId()}）。
     */
    private Long medicineId;

    /**
     * 批次编码（UNIQUE(tenant_id, medicine_id, batch_no, del_unique)）。
     */
    private String batchNo;

    /**
     * 生产日期。
     */
    private LocalDate productionDate;

    /**
     * 过期日期。
     */
    private LocalDate expiryDate;

    /**
     * 该批次当前库存（领用 / 退回 / 损耗时扣减）。
     */
    private BigDecimal quantity;

    /**
     * 进货单价。
     */
    private BigDecimal unitPrice;

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
     * 软删唯一性辅助列：未删时 0；软删时由 {@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
     * 写入 id，保证 UNIQUE(tenant_id, medicine_id, batch_no, del_unique) 不阻塞重新启用同批次号。
     */
    private Long delUnique;

}
