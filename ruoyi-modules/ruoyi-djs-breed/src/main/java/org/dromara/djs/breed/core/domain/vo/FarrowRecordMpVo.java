package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 母猪详情「分娩记录」逐条明细卡出参（DJS-FIX-6-14 row212，原型 母猪详情·分娩记录 tab）。
 *
 * <p>每条 = 一窝分娩记录，倒序 by 分娩日期。展示字段对齐原型明细卡：
 * 配种日期 / 分娩日期 / 总产仔数 / 公猪(配种公猪耳号) / 仔猪均重 / 母猪数 +
 * 健仔数 / 弱仔(处死) / 弱仔(饲养) / 压死数 / 畸形数 / 死胎数 / 木乃伊数。</p>
 *
 * <p>日期字段为 'YYYY-MM-DD' 字符串（mp 直显，避免 LocalDateTime 序列化口径分歧）；
 * 缺数据的字段返 null，mp 端显 —。</p>
 *
 * @author djs
 * @since DJS-FIX-6-14
 */
@Data
public class FarrowRecordMpVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配种日期（关联配种记录，'YYYY-MM-DD'）；无关联 → null。 */
    private String breedingDate;

    /** 分娩日期（'YYYY-MM-DD'）。 */
    private String farrowDate;

    /** 胎次。 */
    private Integer parity;

    /** 总产仔数。 */
    private Integer totalBorn;

    /** 配种公猪耳号（原型「公猪」字段）；无关联配种 → null。 */
    private String boarEarNo;

    /** 仔猪均重 kg。 */
    private BigDecimal avgWeight;

    /** 母猪数（健仔母 + 弱仔留养母，原型「母猪数」）。 */
    private Integer femaleCount;

    /** 健仔数 = healthy_male + healthy_female。 */
    private Integer healthy;

    /** 弱仔(处死)数。 */
    private Integer weakCulled;

    /** 弱仔(饲养)数 = weak_raised_male + weak_raised_female。 */
    private Integer weakRaised;

    /** 压死数。 */
    private Integer crushed;

    /** 畸形数。 */
    private Integer deformed;

    /** 死胎数。 */
    private Integer dead;

    /** 木乃伊数。 */
    private Integer mummy;
}
