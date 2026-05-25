package org.dromara.djs.breed.event.weaning.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningBo;
import org.dromara.djs.breed.event.weaning.domain.query.WeaningQuery;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningVo;

/**
 * 断奶事件 Service（BRD-EVENT-002 WEAN）。状态机 FM → DN。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface IWeaningService {

    PigWeaningVo recordWeaning(WeaningBo bo);

    TableDataInfo<PigWeaningVo> queryPage(WeaningQuery query, PageQuery pageQuery);
}
