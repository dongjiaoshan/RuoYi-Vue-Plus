package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * mp 仓库管理「仓库统计」tab 月度品类统计 VO（V6-R178）。
 *
 * <p>按月统计 4 个品类 × 若干单位 × 3 指标（入库量 / 生产量 / 原材料消耗量）+ 各自环比。</p>
 *
 * @author djs
 */
@Data
public class WarehouseBoardStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份 yyyy-MM。 */
    private String month;

    /** 环比基准月份 yyyy-MM（统计月的上一个自然月）。 */
    private String prevMonth;

    /** 品类卡（固定 4 张，顺序：猪肉 / 果蔬 / 蛋类 / 干货）。 */
    private List<CategoryStatVo> categories = new ArrayList<>();
}
