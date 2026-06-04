package org.dromara.djs.store.returns.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;

import java.util.Collection;
import java.util.List;

/**
 * 门店退回管理 Service（STR-RETURN-001，门店域薄实现）。
 *
 * <p>V1 仅录入：三方向退回登记（主场景 customer_to_store）+ 会员/追溯码字段，
 * 不做库存联动 / 不做状态机（仓库侧退货由 WMS-SHIP-001 负责，避免双写库存）。</p>
 *
 * @author djs
 * @since STR-RETURN-001
 */
public interface IStoreReturnService {

    /** 分页列表（VO 批量填 storeName/productName）。 */
    TableDataInfo<StoreReturnVo> queryPageList(StoreReturnQuery query, PageQuery pageQuery);

    /** 导出用列表（VO 批量填 storeName/productName）。 */
    List<StoreReturnVo> queryList(StoreReturnQuery query);

    /** 详情。 */
    StoreReturnVo queryById(Long id);

    /**
     * 新增退回记录：returnNo 服务端生成（BizCodeType.RETURN_NO）、operatorId 注入、
     * 校验 product 存在（storeId 非空才校验 store）、returnDate 缺省 now。
     */
    Long insertByBo(StoreReturnBo bo);

    /** 编辑（不允许改 returnNo）。 */
    int updateByBo(StoreReturnBo bo);

    /** 软删除（DjsBaseServiceImpl#softDelete 范式）。 */
    int deleteByIds(Collection<Long> ids);
}
