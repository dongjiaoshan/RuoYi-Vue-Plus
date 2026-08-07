package org.dromara.djs.plant.crop.service;

import org.dromara.djs.plant.crop.domain.bo.CropProductBo;
import org.dromara.djs.plant.crop.domain.vo.CropProductVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 作物关联产品配置 Service（V6 row16）。
 *
 * @author djs
 */
public interface ICropProductService {

    /** 某作物的产品配置列表（按 id 升序，首行 = 首选产品）。 */
    List<CropProductVo> listByCrop(Long cropId);

    /**
     * 批量取多个作物的产品配置（采摘明细 / 绩效结算 / mp 下拉共用，避免 N+1）。
     *
     * @return cropId → 配置行（按 id 升序）；无配置的作物不出现在 map 里
     */
    Map<Long, List<CropProductVo>> mapByCropIds(Collection<Long> cropIds);

    int insertByBo(CropProductBo bo);

    int updateByBo(CropProductBo bo);

    int deleteByIds(Collection<Long> ids);
}
