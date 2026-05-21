package org.dromara.djs.common.person.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.person.domain.bo.PersonBo;
import org.dromara.djs.common.person.domain.query.PersonQuery;
import org.dromara.djs.common.person.domain.vo.PersonVo;

import java.util.Collection;
import java.util.List;

/**
 * 人员主数据 Service（SYS-MD-001）。
 *
 * @author djs
 * @since SYS-MD-001
 */
public interface IPersonService {

    /**
     * 分页查询人员列表。
     */
    TableDataInfo<PersonVo> queryPageList(PersonQuery query, PageQuery pageQuery);

    /**
     * 查询人员列表（不分页，给导出用）。
     */
    List<PersonVo> queryList(PersonQuery query);

    /**
     * 根据 ID 查询单条。
     */
    PersonVo queryById(Long id);

    /**
     * 新增。
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(PersonBo bo);

    /**
     * 修改。
     *
     * @return 受影响行数（成功 1）
     */
    int updateByBo(PersonBo bo);

    /**
     * 软删除（支持批量）。
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
