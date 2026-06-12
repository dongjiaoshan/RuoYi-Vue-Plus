package org.dromara.djs.warehouse.outsource.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.outsource.domain.bo.OutsourcePigBo;
import org.dromara.djs.warehouse.outsource.domain.query.OutsourcePigQuery;
import org.dromara.djs.warehouse.outsource.domain.vo.OutsourcePigVo;

import java.util.Collection;
import java.util.List;

/**
 * 外购猪只 Service（DJS-FIX-WMS-RALN）。
 *
 * <p>原型仅 新增 + 删除（无编辑），故只提供 list / getInfo / insert / delete / export 能力。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
public interface IOutsourcePigService {

    /**
     * 分页查询外购猪只列表。
     */
    TableDataInfo<OutsourcePigVo> queryPageList(OutsourcePigQuery query, PageQuery pageQuery);

    /**
     * 查询外购猪只列表（不分页，给导出用）。
     */
    List<OutsourcePigVo> queryList(OutsourcePigQuery query);

    /**
     * 根据 ID 查询单条。
     */
    OutsourcePigVo queryById(Long id);

    /**
     * 新增外购猪只。
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(OutsourcePigBo bo);

    /**
     * 软删除外购猪只（支持批量）。
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
