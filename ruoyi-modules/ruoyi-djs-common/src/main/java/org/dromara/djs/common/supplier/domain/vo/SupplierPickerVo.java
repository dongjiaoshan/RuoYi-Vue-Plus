package org.dromara.djs.common.supplier.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小程序供应商选择器轻量出参（D12X-MP-INTRO-001）。
 *
 * <p>比 admin 端 {@link SupplierVo} 更轻：mp SupplierPicker 只需 supplierCode（提交值）+
 * supplierName（列表主显示）+ supplierType（按业务类型过滤展示），不回传 bank / license /
 * liaison 等冗余敏感字段。</p>
 *
 * <p>跨层契约（cross-layer-contract §1）：supplierCode 为业务短码 String（如 G0004），
 * mp 选中后作为业务字段 supplierCode 提交，后端 resolve 成 supplierId。</p>
 *
 * @author djs
 * @since D12X-MP-INTRO-001
 */
@Data
public class SupplierPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 供应商编码（业务短码，picker 选中后作为业务字段提交值）。 */
    private String supplierCode;

    /** 供应商名称（picker 列表主显示文案）。 */
    private String supplierName;

    /** 供应商类型（字典 djs_supplier_type；picker 按类型过滤，前端可展示）。 */
    private String supplierType;
}
