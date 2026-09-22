package org.dromara.djs.breed.event.farrow.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.farrow.domain.bo.FarrowBo;
import org.dromara.djs.breed.event.farrow.domain.query.FarrowQuery;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowBarnCountVo;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowLitterVo;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowPickerVo;
import org.dromara.djs.breed.event.farrow.domain.vo.PigFarrowVo;

import java.util.List;

/**
 * 母猪分娩事件 Service（BRD-EVENT-002 FARROW）。
 *
 * <p>主入口 {@link #recordFarrow}：INSERT t_farm_pig_farrow → fireEvent(FARROW)（PZ → FM, parity +1）
 * → 整窝仔猪自动建档（V6 行242，同事务；头数取窝级公母数，出生重取字典默认值 D-0107）。</p>
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
     * mp 端 D9 FarrowPicker 用：先选母猪 earNo → 反查该母猪最近 N 次分娩记录。
     *
     * <p>过滤逻辑：</p>
     * <ul>
     *   <li>WHERE earNo = ? AND del_flag = '0'</li>
     *   <li>ORDER BY farrow_date DESC LIMIT N（默认 5）</li>
     *   <li>enrich tagged / remain 供展示；<b>不</b>按建档进度过滤（断奶发生在建档之后）</li>
     * </ul>
     *
     * @param motherEarNo 母猪耳号（业务码）
     * @param limit 最多返回条数（1-20，默认 5）
     * @return picker list；earNo 不存在 / 无符合条件分娩时返空 list（非 throw）
     */
    List<FarrowPickerVo> queryRecentByMotherEarNo(String motherEarNo, int limit);

    /**
     * mp 端"仔猪耳号"页选窝网格用：<b>未断奶</b>分娩窝列表（原型 96 母猪卡网格）。
     *
     * <p>过滤逻辑（V6 行243 把口径从「仔猪耳号未打标」换成「母猪未断奶」——
     * 行242 起整窝在分娩提交时即自动建档，「未打标」永远为空，这一页的用途改成订正出生重）：</p>
     * <ul>
     *   <li>SQL 侧 {@code NOT EXISTS t_farm_pig_weaning}（母猪一断奶就从列表消失）</li>
     *   <li>可选 {@code motherEarNo}（母猪耳号下拉筛选）/ {@code barnName}（分娩栋舍 chip 筛选）</li>
     *   <li>按 farrow_date 倒序，上限 60 条</li>
     *   <li>enrich 公母数 / 日龄（NOW - farrowDate）/ 分娩舍栋栏 / 已建档头数</li>
     * </ul>
     *
     * @param motherEarNo 母猪耳号过滤（可空）
     * @param barnName    分娩栋舍名过滤（chip 点击后传，可空）
     * @return 待订正出生重的窝 list（窝里还有没断奶的仔猪 / 零档案老窝）；无符合条件时返空 list（非 throw）
     */
    List<FarrowLitterVo> queryPendingLitters(String motherEarNo, String barnName);

    /**
     * mp 端"仔猪耳号"页分娩栋舍 chip：按分娩舍聚合未断奶窝数（原型 96 顶部 chip"分娩1栋(12)"）。
     *
     * <p>口径与 {@link #queryPendingLitters} 一致（窝里还有没断奶的仔猪才算）；
     * barn_name 为空的窝归"未分配"不计入；count 0 不返；按 barnName 升序。</p>
     */
    List<FarrowBarnCountVo> countPendingLittersByBarn();
}
