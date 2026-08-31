package org.dromara.djs.plant.organic.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.organic.domain.bo.CropOrganicBo;
import org.dromara.djs.plant.organic.domain.bo.CropOrganicRelateBo;
import org.dromara.djs.plant.organic.domain.query.CropOrganicQuery;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicRelExportVo;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicVo;

import java.util.Collection;
import java.util.List;

/**
 * 果蔬有机证书 Service（PLT-MD-003 / FIX-PLT-AD-INFO-LIST-001）。
 *
 * @author djs
 * @since PLT-MD-003
 */
public interface ICropOrganicService {

    TableDataInfo<CropOrganicVo> queryPageList(CropOrganicQuery query, PageQuery pageQuery);

    List<CropOrganicVo> queryList(CropOrganicQuery query);

    CropOrganicVo queryById(Long id);

    int insertByBo(CropOrganicBo bo);

    int updateByBo(CropOrganicBo bo);

    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 仅更新证书的关联作物（列表「关联作物」el-transfer 弹窗用，一证多作物）。
     *
     * @param bo 证书 id + 作物 id 列表
     * @return 受影响行数（关联表插入行数）
     */
    int relateCrops(CropOrganicRelateBo bo);

    /**
     * 导出某证书已关联的作物明细（row148，一作物一行：证书编号 / 作物名称 / 作物编号）。
     *
     * @param id 证书 id
     * @return 导出行；证书未关联任何作物时返回空列表（导出仅表头）
     */
    List<CropOrganicRelExportVo> queryRelatedCropsForExport(Long id);
}
