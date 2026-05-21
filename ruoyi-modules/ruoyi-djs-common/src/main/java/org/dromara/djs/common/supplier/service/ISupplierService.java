package org.dromara.djs.common.supplier.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.supplier.domain.bo.SupplierBo;
import org.dromara.djs.common.supplier.domain.query.SupplierQuery;
import org.dromara.djs.common.supplier.domain.vo.SupplierVo;

import java.util.Collection;
import java.util.List;

/**
 * 供应商主数据 Service（SYS-MD-003）。
 *
 * @author djs
 * @since SYS-MD-003
 */
public interface ISupplierService {

    /**
     * 分页查询供应商列表。
     */
    TableDataInfo<SupplierVo> queryPageList(SupplierQuery query, PageQuery pageQuery);

    /**
     * 查询供应商列表（不分页，给导出用）。
     */
    List<SupplierVo> queryList(SupplierQuery query);

    /**
     * 根据 ID 查询单条。
     */
    SupplierVo queryById(Long id);

    /**
     * 新增。
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(SupplierBo bo);

    /**
     * 修改。
     *
     * @return 受影响行数（成功 1）
     */
    int updateByBo(SupplierBo bo);

    /**
     * 软删除（支持批量）。
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
