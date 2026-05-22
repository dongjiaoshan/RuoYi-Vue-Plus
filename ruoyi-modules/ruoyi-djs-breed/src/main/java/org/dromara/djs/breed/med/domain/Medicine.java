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
 * 药品库主数据实体（BRD-MED-001）。
 *
 * <p>对应表 {@code t_breed_medicine_info}（D02 SYS-INIT-001 已建，本 ticket 补加
 * {@code manufacturer / spec / storage_condition / med_status} 4 列）：</p>
 * <ul>
 *   <li>下游 {@code t_breed_medicine_use} 通过 {@code medicine_id} 关联领用 / 退回 / 损耗（BRD-MED-002）</li>
 *   <li>下游 {@code t_farm_medicine_record} 通过 {@code medicine_id} 关联治疗用药流水（BRD-MED-003）</li>
 *   <li>关联 {@code t_md_supplier.id}（{@link Long} {@code supplierId}），admin 表单走
 *       {@code /djs/common/supplier/list} 远程搜索下拉（联动 SYS-MD-003）</li>
 * </ul>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
 * 在 delFlag='1' 时把 id 写入 delUnique，保证 UNIQUE(tenant_id, medicine_code, del_unique) 不阻塞同编码复用）。
 * 服务层走 {@link DjsBaseServiceImpl#softDelete}（D03 _open-issues #18 教训）。</p>
 *
 * <p>"批次 / 批号"V1 平铺在主表 {@code batchNo / expireDate / currentStock}（doc/06 BRD-MED-001 定方案）。
 * 若 V2 需"一药多批次"再抽 {@code t_farm_med_batch}（详 D04 _open-issues raise）。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_breed_medicine_info")
public class Medicine extends TenantEntity implements DjsBaseServiceImpl.SoftDeletable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 药品编码（业务码，UNIQUE(tenant_id, medicine_code, del_unique)）。
     */
    private String medicineCode;

    /**
     * 药品名称。
     */
    private String medicineName;

    /**
     * 药品类型（字典 {@code djs_med_type}：antibiotic 抗生素 / vaccine 疫苗 /
     * nutrition 营养剂 / disinfectant 消毒剂 / other 其他）。
     */
    private String medicineType;

    /**
     * 供应商 ID（引用 {@code t_md_supplier.id}）。
     */
    private Long supplierId;

    /**
     * 批准文号。
     */
    private String approvalNo;

    /**
     * 批号（V1 单批次平铺，V2 再抽批次表）。
     */
    private String batchNo;

    /**
     * 过期日期。
     */
    private LocalDate expireDate;

    /**
     * 休药期（天数）。
     */
    private Integer withdrawDays;

    /**
     * 计量单位（瓶 / 盒 / 克 等自由文本）。
     */
    private String unit;

    /**
     * 当前库存（V2 BRD-MED-002 领用 / 退回 / 损耗时扣减）。
     */
    private BigDecimal currentStock;

    /**
     * 规格（如 "10ml × 100 支 / 盒"，自由文本）。
     */
    private String spec;

    /**
     * 生产厂家。
     */
    private String manufacturer;

    /**
     * 储存条件（如 "2-8℃ 冷藏 避光"）。
     */
    private String storageCondition;

    /**
     * 药品状态（{@code 1}=启用 / {@code 0}=停用；对齐 {@code sys_normal_disable} 1/0 字典）。
     */
    private Integer medStatus;

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
     * 写入 id，保证 UNIQUE(tenant_id, medicine_code, del_unique) 不阻塞重新启用同编码。
     */
    private Long delUnique;

}
