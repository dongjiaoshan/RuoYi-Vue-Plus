package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 卡片下钻明细响应（入库明细 / 生产明细共用，V6-R178）。
 *
 * <p>不用 {@code TableDataInfo} 直返，是因为页面顶部要显示「按单位的合计」——
 * 合计必须是全量口径而不是当前这一页的和，得和分页数据一起下发。</p>
 *
 * @param <T> 行类型（{@link InboundDetailRowVo} / {@link ProductionDetailRowVo}）
 * @author djs
 */
@Data
public class BoardStatDetailVo<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份 yyyy-MM。 */
    private String month;

    /** 品类键 pork / vegetable / egg / dry_good。 */
    private String belongType;

    /** 品类中文名（后端给文案，mp 直接显示）。 */
    private String categoryName;

    /** 满足条件的总行数（分页用）。 */
    private Long total;

    /** 当前页明细行（日期倒序）。 */
    private List<T> rows;

    /** 全量合计（按单位分组，与品类卡同口径同数字）。 */
    private List<BoardStatUnitTotalVo> totals;
}
