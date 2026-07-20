package org.dromara.djs.common.supplier.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 供应商主数据列表查询入参（SYS-MD-003 + SYS-MD-FIX-002）。
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
     * 联系负责人姓名（模糊匹配）。
     */
    private String liaisonName;

    /**
     * 负责人电话（模糊匹配）。
     */
    private String liaisonPhone;

    /**
     * 合作状态（字典 djs_supplier_status 精确匹配）。
     */
    private String businessStatus;

    /**
     * 结算方式（字典 djs_settle_type 精确匹配）。
     */
    private String settleType;

    /**
     * 更新时间范围 - 开始（含）。
     */
    private Date updateTimeBegin;

    /**
     * 更新时间范围 - 结束（含）。
     */
    private Date updateTimeEnd;

    /**
     * 更新人（sys_user.user_id 精确匹配）。
     */
    private Long updateBy;

}
