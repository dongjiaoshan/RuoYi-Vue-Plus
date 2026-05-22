package org.dromara.djs.breed.med.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.bo.MedBatchBo;
import org.dromara.djs.breed.med.domain.query.MedBatchQuery;
import org.dromara.djs.breed.med.domain.vo.MedBatchVo;

import java.util.Collection;
import java.util.List;

/**
 * 药品批次 Service（BRD-MED-001）。
 *
 * @author djs
 * @since BRD-MED-001
 */
public interface IMedBatchService {

    /**
     * 分页查询批次列表。
     */
    TableDataInfo<MedBatchVo> queryPageList(MedBatchQuery query, PageQuery pageQuery);

    /**
     * 查询批次列表（不分页）。
     */
    List<MedBatchVo> queryList(MedBatchQuery query);

    /**
     * 根据 ID 查询单条。
     */
    MedBatchVo queryById(Long id);

    /**
     * 新增批次。
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(MedBatchBo bo);

    /**
     * 修改批次（{@code medicineId} 不允许修改）。
     *
     * @return 受影响行数（成功 1）
     */
    int updateByBo(MedBatchBo bo);

    /**
     * 软删除批次（支持批量）。
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
