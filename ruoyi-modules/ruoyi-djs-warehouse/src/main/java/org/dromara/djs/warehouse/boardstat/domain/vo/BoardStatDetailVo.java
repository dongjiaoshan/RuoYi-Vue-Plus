package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 卡片下钻明细响应（入库明细 / 生产明细共用，V6-R193）。
 *
 * <p>不分页：一个品类一个自然月的<b>产品数</b>是十几到几十的量级（不是流水条数），
 * 一次全给，mp 端在弹窗里一屏滚完，省掉上拉加载与「合计对不上当前页」的歧义。</p>
 *
 * @author djs
 */
@Data
public class BoardStatDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份 yyyy-MM。 */
    private String month;

    /** 环比基准月份 yyyy-MM（统计月的上一个自然月）。 */
    private String prevMonth;

    /** 品类键 pork / vegetable / egg / dry_good / other。 */
    private String belongType;

    /** 品类中文名（后端给文案，mp 直接拼弹窗标题）。 */
    private String categoryName;

    /** 明细行（按产品聚合，量降序）。 */
    private List<BoardStatProductRowVo> rows = new ArrayList<>();

    /** 全量合计（按单位分组，与品类卡同口径同数字）。 */
    private List<BoardStatUnitTotalVo> totals = new ArrayList<>();
}
