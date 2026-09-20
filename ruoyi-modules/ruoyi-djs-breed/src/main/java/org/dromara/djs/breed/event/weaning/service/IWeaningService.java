package org.dromara.djs.breed.event.weaning.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningBo;
import org.dromara.djs.breed.event.weaning.domain.query.WeaningQuery;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningVo;
import org.dromara.djs.breed.event.weaning.domain.vo.UnweanedLitterVo;
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

    /**
     * 分娩未断奶母猪窝列表（BRD-WEAN-SELECT-001，V6 行238 断奶仔猪选择页）。
     *
     * <p>一条 = 一窝仍有未断奶仔猪的分娩记录，带母猪概况（耳号 / 分娩日期 / 胎次 / 日龄 / 当前栋舍栏位）
     * 与该窝未断奶仔猪逐头（耳号 + 性别），供 mp 端跨窝勾选。按分娩日期倒序。</p>
     *
     * @return 未断奶窝列表；无则空列表
     */
    List<UnweanedLitterVo> listUnweanedLitters();
}
