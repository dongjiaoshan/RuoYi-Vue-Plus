package org.dromara.djs.common.store.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 门店主数据列表查询入参（SYS-MD-002 + SYS-MD-FIX-002）。
 *
 * <p>分页参数继承 {@link org.dromara.common.mybatis.core.page.PageQuery}
 * （在 Controller 单独接收，与本 BO 通过 {@code @RequestParam} 同时绑定）。</p>
 *
 * @author djs
 * @since SYS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreQuery extends BaseEntity {

    /**
     * 门店名称（模糊匹配）。
     */
    private String storeName;

    /**
     * 门店编码（精确匹配）。
     */
    private String storeCode;

    /**
     * 门店类型（字典 djs_store_type 精确匹配）。
     */
    private String storeType;

    /**
     * 店长姓名（模糊匹配，针对 manager_name 文本字段）。
     */
    private String managerName;

    /**
     * 合作状态（字典 djs_store_status 精确匹配）。
     */
    private String businessStatus;

}
