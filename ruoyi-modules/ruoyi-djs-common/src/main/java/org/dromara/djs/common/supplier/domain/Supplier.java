package org.dromara.djs.common.supplier.domain;

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
 * 供应商主数据实体（SYS-MD-003 + SYS-MD-FIX-002）。
 *
 * <p>对应表 {@code t_md_supplier}，跨业务域复用：</p>
 * <ul>
 *   <li>养殖：种猪 / 药品供应商（关联 {@code t_breed_*.supplier_id}）</li>
 *   <li>种植：种子供应商</li>
 *   <li>仓库：饲料原材料 / 物资供应商（关联 {@code t_warehouse_supplier_record.supplier_id}）</li>
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
     * 供应商编码（{@link org.dromara.djs.common.encoder.BizCodeType#SUPPLIER_CODE} 生成，pattern {@code G{seq4}}）。
     */
    private String supplierCode;

    /**
     * 供应商名称。
     */
    private String supplierName;

    /**
     * 营业执照编号。
     */
    private String licenseNo;

    /**
     * 营业执照图片（OSS oss_id）。
     */
    private Long licenseImageOssId;

    /**
     * 经营许可证编号。
     */
    private String businessLicenseNo;

    /**
     * 合作开始日期。
     */
    private LocalDate cooperationStartDate;

    /**
     * 供应商类型（字典 djs_supplier_type：feed=饲料原材料 / breed=种猪 / med=药品 / seed=种子 / other=其他）。
     */
    private String supplierType;

    /**
     * 联系负责人姓名。
     */
    private String liaisonName;

    /**
     * 负责人电话。
     */
    private String liaisonPhone;

    /**
     * 地址（自由文本）。
     */
    private String address;

    /**
     * 合作状态（字典 djs_supplier_status：0=合作中 / 1=已终止）。
     */
    private String businessStatus;

    /**
     * 结算方式（字典 djs_settle_type：cash=现款现货 / monthly=月结 / quarterly=季结）。
     */
    private String settleType;

    /**
     * 银行账户（财务核算用）。
     */
    private String bankAccount;

    /**
     * 开户行名称（财务核算用）。
     */
    private String bankName;

    /**
     * 交易次数（聚合冗余字段，V1 stub=0；下游 BRD-MED / WMS-PURCHASE 落地后回填）。
     */
    private Integer dealCount;

    /**
     * 累计购入商品数（V1 stub=0）。
     */
    private BigDecimal purchaseQty;

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
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
