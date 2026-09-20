package org.dromara.djs.breed.event.weaning.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 「分娩未断奶母猪」窝级出参（BRD-WEAN-SELECT-001，V6 行238 断奶仔猪选择页）。
 *
 * <p>一条 = 一窝（一条分娩记录）尚有未断奶仔猪的母猪：列表行展示母猪耳号 / 分娩日期 /
 * 公母头数，展开展示 {@link #piglets} 供逐头勾选。</p>
 *
 * <p>「未断奶仔猪」口径：该窝已贴耳标（{@code t_farm_pig_pigletno}）且
 * ① 耳号未出现在本窝任何一条断奶明细里 ② 对应猪只档案未终止（死亡 / 淘汰 / 出栏）。
 * 整窝未贴标的窝没有耳号可勾选，不在本列表——那种窝仍走录入页按活产仔数铺匿名行的老路径。</p>
 *
 * @author djs
 * @since BRD-WEAN-SELECT-001
 */
@Data
public class UnweanedLitterVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分娩记录 ID（选中后回填断奶录入页 farrowId）。 */
    private Long farrowId;

    /** 母猪 pig_info.id。 */
    private Long sowPigId;

    /** 母猪耳号（列表左列 + 选中后回填断奶录入页 earNo）。 */
    private String sowEarNo;

    /** 分娩日期（列表中列）。 */
    private LocalDateTime farrowDate;

    /** 分娩当时胎次（回填录入页概况卡）。 */
    private Integer parity;

    /** 母猪实时日龄（天，回填录入页概况卡；出生日 / 引种日均缺时为 null）。 */
    private Integer sowAgeDays;

    /** 母猪当前栋舍编码（回填录入页转移目标默认值）。 */
    private String barnCode;

    /** 母猪当前栋舍中文名。 */
    private String barnName;

    /** 母猪当前栏位编码。 */
    private String penCode;

    /** 母猪当前栏位中文名。 */
    private String penName;

    /** 本窝未断奶公仔头数（列表右列）。 */
    private Integer maleCount;

    /** 本窝未断奶母仔头数（列表右列）。 */
    private Integer femaleCount;

    /** 本窝未断奶总头数 = maleCount + femaleCount。 */
    private Integer totalCount;

    /** 本窝未断奶仔猪逐头（展开后的勾选网格；按耳号升序）。 */
    private List<WeaningPigletVo> piglets;
}
