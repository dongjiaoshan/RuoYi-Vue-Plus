package org.dromara.djs.plant.farmmap.service;

import org.dromara.djs.plant.farmmap.domain.bo.FarmMapBindBo;
import org.dromara.djs.plant.farmmap.domain.vo.FarmMapOverviewVo;

/**
 * 农场地图 Service（PLT-FARMMAP-001）。
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
public interface IFarmMapService {

    /**
     * 拉全图：已挂格子 + 图外地块 + 覆盖率。
     */
    FarmMapOverviewVo overview();

    /**
     * 把一个格子挂到一块地上。
     *
     * <p>1:1：格子已挂别的地 → 换成新的；地块已挂在别的格子上 → 拒绝，让用户先解绑那边。</p>
     *
     * @return 影响行数
     */
    int bind(FarmMapBindBo bo);

    /**
     * 解绑一个格子（软删）。格子本来就没挂 → 返回 0，不报错。
     *
     * @return 影响行数
     */
    int unbind(String regionKey);

}
