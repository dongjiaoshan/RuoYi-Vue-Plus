package org.dromara.djs.breed.event.farrow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分娩记录视图（BRD-EVENT-002）。
 *
 * <p>admin 只读列表 / mp 端"分娩 → 选 farrow 贴耳标"两端复用。
 * 字段顺序与列表展示一致：日期 / 母猪 / 仔猪明细 / 备注。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigFarrow.class)
public class PigFarrowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;
    private Long breedingId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime farrowDate;

    private Integer totalBorn;
    private Integer liveBorn;
    private Integer deadBorn;
    private Integer mummyBorn;
    private Integer weakBorn;
    private Integer maleCount;
    private Integer femaleCount;
    private Integer healthyMale;
    private Integer healthyFemale;
    private Integer weakRaisedMale;
    private Integer weakRaisedFemale;
    private Integer weakCulled;
    private Integer deformedBorn;
    private Integer crushedBorn;
    private BigDecimal totalWeight;
    private BigDecimal avgWeight;
    private Integer parity;
    private Long operatorId;

    /** 分娩记录人员姓名（operatorId → USER_ID_TO_NICKNAME 翻译；mp 记录卡展示；翻不到回落 null）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    private String barnName;
    private String penName;

    /**
     * 母猪日龄（天，事件时口径 = farrowDate - birth_date）。
     * <p>service enrich 批查 t_farm_pig_info 取 birth_date 算；缺 birth_date 回落 introduce_date；
     * 两者均空 → null（mp 卡片该格不渲染）。</p>
     */
    private Integer ageDays;

    /** 仔猪品系中文名（该窝已耳标仔猪真实 pig_strain_code → 主数据/字典翻译；未耳标回落母猪；缺则 null）。 */
    private String pigStrainName;

    /** 仔猪品种中文名（该窝已耳标仔猪真实 pig_breed_code → 主数据/字典翻译；未耳标回落母猪；缺则 null）。 */
    private String pigBreedName;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 已贴耳标头数（用于 mp 端"分娩 picker"显示"已贴/活产"，列表场景可空）。 */
    private Integer tagged;

    /** 剩余可贴 = liveBorn - tagged。 */
    private Integer remaining;
}
