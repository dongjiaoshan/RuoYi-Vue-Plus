package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 门店管理业态卡「明细」下钻响应（V6-R180）。
 *
 * <p>不用 {@code TableDataInfo} 直返，是因为页面顶部要显示「按单位的三项合计」——
 * 合计必须是全量口径而不是当前这一页的和，得和分页数据一起下发。</p>
 *
 * @author djs
 * @since V6-R180
 */
@Data
public class StoreManageDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份 yyyy-MM。 */
    private String month;

    /** 业态卡 key：pork / vegetable / egg / dry_good / other。 */
    private String belongType;

    /** 业态中文名（后端给文案，mp 直接显示）。 */
    private String categoryName;

    /** 统计门店；null = 全部门店合计。 */
    private Long storeId;

    /** 满足条件的产品行数（分页用）。 */
    private Long total;

    /** 当前页明细行（需求量降序）。 */
    private List<StoreManageDetailRowVo> rows;

    /** 全量合计（按单位分组，与业态卡同口径同数字）。 */
    private List<StoreManageDetailTotalVo> totals;
}
