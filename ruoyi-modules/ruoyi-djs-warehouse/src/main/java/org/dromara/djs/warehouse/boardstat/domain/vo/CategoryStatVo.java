package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 品类统计卡（mp 仓库统计 tab 一张卡 = 一个品类）。
 *
 * <p>卡片顺序：猪肉 / 果蔬 / 蛋类 / 干货 / 其他产品。<b>本月三个指标全无数据的品类不出卡</b>
 * （service 侧已过滤），所以拿到的每一张卡都至少有一个非零量。</p>
 *
 * @author djs
 */
@Data
public class CategoryStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 品类键（pork / vegetable / egg / dry_good / other；猪肉卡合并了 white_bar）。 */
    private String categoryKey;

    /** 品类中文名（猪肉产品 / 果蔬产品 / 蛋类产品 / 干货产品 / 其他产品）。 */
    private String categoryName;

    /** 卡内行（按单位分组，单位名升序）。 */
    private List<CategoryUnitStatVo> rows = new ArrayList<>();
}
