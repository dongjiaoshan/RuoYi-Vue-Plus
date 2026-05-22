package org.dromara.djs.breed.breeding.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.breeding.domain.bo.BreedInfoBo;
import org.dromara.djs.breed.breeding.domain.query.BreedInfoQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedInfoVo;

import java.util.Collection;
import java.util.List;

/**
 * 育种信息 Service（BRD-MD-001）。
 *
 * <p>下游消费：{@code BRD-CORE-001} 猪只引用品种品系按 {@code (breed_strain, code)} 两字段定位；
 * 删除前置校验"是否被 {@code t_farm_pig_info} 引用"在 D05 BRD-CORE-001 wire 后补。</p>
 *
 * @author djs
 * @since BRD-MD-001
 */
public interface IBreedInfoService {

    /**
     * 分页查询。
     */
    TableDataInfo<BreedInfoVo> queryPageList(BreedInfoQuery query, PageQuery pageQuery);

    /**
     * 列表（不分页，给导出 / 配种关系下拉用）。
     */
    List<BreedInfoVo> queryList(BreedInfoQuery query);

    /**
     * 根据 ID 查询单条。
     */
    BreedInfoVo queryById(Long id);

    /**
     * 新增。
     */
    int insertByBo(BreedInfoBo bo);

    /**
     * 修改。
     */
    int updateByBo(BreedInfoBo bo);

    /**
     * 软删除（支持批量）。
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
