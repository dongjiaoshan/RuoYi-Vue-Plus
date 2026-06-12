package org.dromara.djs.plant.crop.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.crop.domain.bo.CropInfoBo;
import org.dromara.djs.plant.crop.domain.query.CropInfoQuery;
import org.dromara.djs.plant.crop.domain.vo.CropFarmworkVo;
import org.dromara.djs.plant.crop.domain.vo.CropInfoVo;
import org.dromara.djs.plant.crop.domain.vo.CropPlantingRecordVo;

import java.util.Collection;
import java.util.List;

/**
 * 作物 Service（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
public interface ICropInfoService {

    TableDataInfo<CropInfoVo> queryPageList(CropInfoQuery query, PageQuery pageQuery);

    List<CropInfoVo> queryList(CropInfoQuery query);

    CropInfoVo queryById(Long id);

    int insertByBo(CropInfoBo bo);

    int updateByBo(CropInfoBo bo);

    /**
     * 软删（D8 阶段 stub，D9+ PLT-PLAN-001 等上线后启用真校验）。
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 作物详情·种植信息子表（按 cropId 聚合 t_plant_plant_details，9 列）。
     */
    List<CropPlantingRecordVo> listPlantingByCrop(Long cropId);

    /**
     * 作物详情·农事信息子表（按 cropId 关联 t_plant_farm_records）。
     */
    List<CropFarmworkVo> listFarmworkByCrop(Long cropId);
}
