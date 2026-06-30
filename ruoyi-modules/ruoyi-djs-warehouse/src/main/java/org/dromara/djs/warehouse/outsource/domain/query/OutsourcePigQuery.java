package org.dromara.djs.warehouse.outsource.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;
import java.util.List;

/**
 * 外购猪只列表查询入参（DJS-FIX-WMS-RALN）。
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OutsourcePigQuery extends BaseEntity {

    /**
     * 采购日期范围下界（含，yyyy-MM-dd）。
     */
    private String purchaseDateFrom;

    /**
     * 采购日期范围上界（含，yyyy-MM-dd）。
     */
    private String purchaseDateTo;

    /**
     * 到场时间（按天精确匹配）。
     */
    private Date arriveTime;

    /**
     * 供应商 ID（精确匹配；多选时走 {@link #supplierIds}）。
     */
    private Long supplierId;

    /**
     * 供应商 ID 多选（IN 匹配）。
     */
    private List<Long> supplierIds;

    /**
     * 购买人（sys_user.user_id，精确匹配；多选时走 {@link #buyers}）。
     */
    private String buyer;

    /**
     * 购买人多选（sys_user.user_id，IN 匹配）。
     */
    private List<String> buyers;

}
