package org.dromara.djs.breed.event.weaning.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 断奶逐头录重「待录仔猪」VO（FIX-WEAN-001 #31，原型 91）。
 *
 * <p>mp 端工人选完关联分娩后，按该分娩已贴耳标的仔猪铺固定 N 行（耳号只读、逐头录重）。
 * 数据源 {@code t_farm_pig_pigletno}（按 farrow_id 过滤已贴标仔猪）；若该分娩尚无打标行，
 * mp 端按 farrow.live_born 数量退化铺 N 行（耳号占位空）。</p>
 *
 * @author djs
 * @since FIX-WEAN-001
 */
@Data
public class WeaningPigletVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 仔猪序号（按 piglet_ear_no 升序从 1 起，给 mp 逐头行序号展示）。 */
    private Integer pigletSeq;

    /** 仔猪耳号（来自 t_farm_pig_pigletno.piglet_ear_no；mp 端只读展示）。 */
    private String earNo;

    /** 仔猪性别 F=母 / M=公（可空，mp 端可选展示）。 */
    private String pigletSex;
}
