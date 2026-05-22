package org.dromara.djs.common.supplier.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 供应商主数据实体（SYS-MD-003）。
 *
 * <p>对应表 {@code t_md_supplier}，跨业务域复用：</p>
 * <ul>
 *   <li>养殖：种猪 / 兽药供应商（关联 {@code t_breed_*.supplier_id}）</li>
 *   <li>种植：蔬菜种子供应商</li>
 *   <li>仓库：饲料 / 包材 / 物资供应商（关联 {@code t_warehouse_supplier_record.supplier_id}）</li>
 * </ul>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
 * 在 delFlag='1' 时把 id 写入 delUnique，保证 UNIQUE(tenant_id, supplier_code, del_unique) 不阻塞重新启用同编码）。</p>
 *
 * @author djs
 * @since SYS-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_md_supplier")
public class Supplier extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 供应商 ID（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 供应商编码（{@link org.dromara.djs.common.encoder.BizCodeType#SUPPLIER_CODE} 生成，pattern {@code G{seq4}}，例 G0001）。
     */
    private String supplierCode;

    /**
     * 供应商名称。
     */
    private String supplierName;

    /**
     * 供应商类型（字典 djs_supplier_type：feed / breed / med / seed / pack / other）。
     */
    private String supplierType;

    /**
     * 联系人姓名。
     */
    private String contactName;

    /**
     * 联系电话。
     */
    private String contactPhone;

    /**
     * 地址（自由文本）。
     */
    private String address;

    /**
     * 业务状态（启用 1 / 停用 0；对齐 sys_normal_disable 0/1 字典正常停用语义）。
     *
     * <p>实表 {@code business_status TINYINT NOT NULL DEFAULT 1}，故用 Integer 而非 String。</p>
     */
    private Integer businessStatus;

    /**
     * 结算方式（自由文本占位，V2 决定是否字典化）。
     */
    private String settleType;

    /**
     * 银行账户（财务核算用，D02 _open-issues #1 保留决策）。
     */
    private String bankAccount;

    /**
     * 开户行名称（财务核算用，D02 _open-issues #1 保留决策）。
     */
    private String bankName;

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
     * 写入 id，保证 UNIQUE 约束不阻塞重新启用同编码。
     */
    private Long delUnique;

}
