package org.dromara.djs.breed.breeding.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.breeding.domain.bo.BreedInfoBo;
import org.dromara.djs.breed.breeding.domain.query.BreedInfoQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedInfoOptionVo;
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
     * 品种/品系下拉选项（FIX-INTRO-001 #2）——mp 端外部引种品种/品系下拉数据源。
     *
     * <p>按 {@code breedStrain}（1=品种 / 2=品系）过滤 {@code t_farm_breed_info}，
     * 返回 {@code {code, name}} 轻量列表（提交存 code）。{@code breedStrain} 为 null 时返全部。</p>
     *
     * @param breedStrain 1=品种 / 2=品系 / null=全部
     * @return 下拉选项列表（code + name）
     */
    List<BreedInfoOptionVo> listOptionsByStrain(Integer breedStrain);

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
