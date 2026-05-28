package org.dromara.djs.breed.event.farrow.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.farrow.domain.bo.FarrowBo;
import org.dromara.djs.breed.event.farrow.domain.query.FarrowQuery;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowPickerVo;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;

import java.util.List;

/**
 * 母猪分娩事件 Service（BRD-EVENT-002 FARROW）。
 *
 * <p>主入口 {@link #recordFarrow}：INSERT t_farm_pig_farrow → fireEvent(FARROW)（PH → FM, parity +1）
 * → publish {@code PigFarrowEvent} 通知耳标域。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface IFarrowService {

    /** mp 端分娩事件录入。 */
    PigFarrowVo recordFarrow(FarrowBo bo);

    /** admin 只读列表分页。 */
    TableDataInfo<PigFarrowVo> queryPage(FarrowQuery query, PageQuery pageQuery);

    /**
     * mp 端"分娩 picker"用：近 7 天 + 本人录入的分娩，按 farrow_date 倒序，含 tagged / remaining。
     * 上限 50 条。
     */
    List<PigFarrowVo> queryRecent(Long operatorId, int limit);

    /**
     * mp 端 D9 FarrowPicker 用：先选母猪 earNo → 反查该母猪最近 N 次未贴满标的分娩记录。
     *
     * <p>过滤逻辑：</p>
     * <ul>
     *   <li>WHERE earNo = ? AND del_flag = '0'</li>
     *   <li>ORDER BY farrow_date DESC LIMIT N（默认 5）</li>
     *   <li>enrich tagged → 仅返 {@code remainEartag > 0}（已贴满 N=liveBorn 的分娩 picker 不显示，工人不会再选）</li>
     * </ul>
     *
     * @param motherEarNo 母猪耳号（业务码）
     * @param limit 最多返回条数（1-20，默认 5）
     * @return picker list；earNo 不存在 / 无符合条件分娩时返空 list（非 throw）
     */
    List<FarrowPickerVo> queryRecentByMotherEarNo(String motherEarNo, int limit);
}
