package org.dromara.djs.common.supplier.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 供应商主数据列表查询入参（SYS-MD-003）。
 *
 * <p>分页参数由 Controller 单独接收 {@link org.dromara.common.mybatis.core.page.PageQuery}。</p>
 *
 * @author djs
 * @since SYS-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierQuery extends BaseEntity {

    /**
     * 供应商名称（模糊匹配）。
     */
    private String supplierName;

    /**
     * 供应商编码（精确匹配）。
     */
    private String supplierCode;

    /**
     * 供应商类型（字典 djs_supplier_type，精确匹配）。
     */
    private String supplierType;

    /**
     * 联系电话（模糊匹配）。
     */
    private String contactPhone;

    /**
     * 业务状态（1 启用 / 0 停用）。
     */
    private Integer businessStatus;

}
