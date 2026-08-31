package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 出入库月汇总列表行（V6-R154）。
 *
 * <p>甲方 row154 第 3 点：列表只有「汇总月份」+「操作」两列，故本 VO 只有一个字段。</p>
 *
 * <p>月份集合 = <b>有出入库流水的月份</b>（本页统计的是流量，没有出入库的月份出一行全零没有信息），
 * 与「库存月汇总」的连续自然月补零口径<b>刻意分道</b> —— 那页统计的是结存。</p>
 *
 * @author djs
 * @since V6-R154
 */
@Data
public class InoutMonthVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 汇总月份 yyyy-MM。 */
    private String statMonth;
}
