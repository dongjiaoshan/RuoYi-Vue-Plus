package org.dromara.djs.breed.event.eartag.domain.vo;

import lombok.Data;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.event.eartag.domain.PigPigletno;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单头仔猪耳标返回 VO（BRD-EVENT-003）。
 *
 * <p>批量贴标接口逐头返；包含 pig_info + pigletno 关键字段，供小程序回显新生成 pig 列表。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Data
public class PigletEarTagVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** pigletno 日志主键。 */
    private Long pigletnoId;

    /** 新生成 pig_info.id（仔猪 pig 主表行）。 */
    private Long pigId;

    /** 仔猪耳号。 */
    private String pigletEarNo;

    /** 母猪耳号。 */
    private String motherEarNo;

    /** 父猪耳号（可空）。 */
    private String fatherEarNo;

    /** 关联分娩 ID。 */
    private Long farrowId;

    /** 仔猪性别（F/M）。 */
    private String pigletSex;

    /** 出生重 kg。 */
    private BigDecimal birthWeight;

    /** 出生日期（来自 farrow.farrow_date）。 */
    private LocalDate birthDate;

    /** 打标日期。 */
    private LocalDateTime tagDate;

    /** 当前状态（仔猪默认 HB）。 */
    private String currentStatus;

    /** 工厂方法：组合 pig + pigletno 字段为单头 VO。 */
    public static PigletEarTagVo from(Pig pig, PigPigletno log) {
        PigletEarTagVo vo = new PigletEarTagVo();
        vo.setPigletnoId(log.getId());
        vo.setPigId(pig.getId());
        vo.setPigletEarNo(log.getPigletEarNo());
        vo.setMotherEarNo(log.getMotherEarNo());
        vo.setFatherEarNo(log.getFatherEarNo());
        vo.setFarrowId(log.getFarrowId());
        vo.setPigletSex(log.getPigletSex());
        vo.setBirthWeight(log.getBirthWeight());
        vo.setBirthDate(pig.getBirthDate());
        vo.setTagDate(log.getTagDate());
        vo.setCurrentStatus(pig.getCurrentStatus());
        return vo;
    }
}
