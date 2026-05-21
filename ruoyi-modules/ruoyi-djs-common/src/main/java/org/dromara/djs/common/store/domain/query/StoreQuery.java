package org.dromara.djs.common.store.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 门店主数据列表查询入参（SYS-MD-002）。
 *
 * <p>分页参数继承 {@link org.dromara.common.mybatis.core.page.PageQuery}
 * （在 Controller 单独接收，与本 BO 通过 {@code @RequestParam} 同时绑定，参考 SYS-MD-001）。</p>
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
     * 门店类型。
     */
    private String storeType;

    /**
     * 经营状态（1=合作中 / 0=已终止）。
     */
    private Integer businessStatus;

    /**
     * 联系电话（模糊匹配）。
     */
    private String contactPhone;

}
