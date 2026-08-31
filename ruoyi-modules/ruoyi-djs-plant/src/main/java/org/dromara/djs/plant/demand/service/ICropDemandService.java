package org.dromara.djs.plant.demand.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.demand.domain.bo.CropDemandBo;
import org.dromara.djs.plant.demand.domain.bo.CropDemandReplyBo;
import org.dromara.djs.plant.demand.domain.query.CropDemandQuery;
import org.dromara.djs.plant.demand.domain.vo.CropDemandVo;

import java.util.Collection;

/**
 * 作物需求 Service（V6-R152 运营端提 / V6-R153 种植端回，一套服务两个入口）。
 *
 * @author djs
 * @since V6-R152
 */
public interface ICropDemandService {

    TableDataInfo<CropDemandVo> queryPageList(CropDemandQuery query, PageQuery pageQuery);

    CropDemandVo queryById(Long id);

    /**
     * 新增需求（运营端）。需求日期取当天、状态强制 pending、回复三字段强制留空。
     */
    int insertByBo(CropDemandBo bo);

    /**
     * 回复需求（种植端）。首次回复与修改回复同一入口，回复后状态恒为 replied。
     */
    int reply(CropDemandReplyBo bo);

    /**
     * 删除需求。只有创建人本人能删自己录入的需求，越权抛 {@code ServiceException}。
     */
    int deleteWithValidByIds(Collection<Long> ids);
}
