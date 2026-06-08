package org.dromara.djs.breed.event.weaning.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningBo;
import org.dromara.djs.breed.event.weaning.domain.query.WeaningQuery;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningVo;
import org.dromara.djs.breed.event.weaning.domain.vo.WeaningPigletVo;

import java.util.List;

/**
 * 断奶事件 Service（BRD-EVENT-002 WEAN）。状态机 FM → DN。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface IWeaningService {

    PigWeaningVo recordWeaning(WeaningBo bo);

    TableDataInfo<PigWeaningVo> queryPage(WeaningQuery query, PageQuery pageQuery);

    /**
     * 按关联分娩查已贴耳标的仔猪列表（FIX-WEAN-001 #31，原型 91 逐头录重铺行）。
     *
     * <p>数据源 {@code t_farm_pig_pigletno}（farrow_id 过滤），按 piglet_ear_no 升序。
     * 该分娩无打标行时返空列表（mp 端按 live_born 数量退化铺行）。</p>
     *
     * @param farrowId 关联分娩记录 ID
     * @return 仔猪耳号 + 序号 + 性别列表
     */
    List<WeaningPigletVo> listPigletsByFarrow(Long farrowId);
}
