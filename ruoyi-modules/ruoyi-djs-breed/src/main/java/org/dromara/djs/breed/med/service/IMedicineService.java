package org.dromara.djs.breed.med.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.bo.MedicineBo;
import org.dromara.djs.breed.med.domain.query.MedicineQuery;
import org.dromara.djs.breed.med.domain.vo.MedicineVo;

import java.util.Collection;
import java.util.List;

/**
 * 药品库 Service（BRD-MED-001）。
 *
 * @author djs
 * @since BRD-MED-001
 */
public interface IMedicineService {

    /**
     * 分页查询药品列表。
     */
    TableDataInfo<MedicineVo> queryPageList(MedicineQuery query, PageQuery pageQuery);

    /**
     * 查询药品列表（不分页，给导出 / 下游下拉用）。
     */
    List<MedicineVo> queryList(MedicineQuery query);

    /**
     * 根据 ID 查询单条。
     */
    MedicineVo queryById(Long id);

    /**
     * 新增药品。
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(MedicineBo bo);

    /**
     * 修改药品（{@code medicineCode} 不允许修改）。
     *
     * @return 受影响行数（成功 1）
     */
    int updateByBo(MedicineBo bo);

    /**
     * 软删除药品（支持批量）。
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
