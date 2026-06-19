package org.dromara.djs.breed.event.eliminate.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.eliminate.domain.PigCulling;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 淘汰记录视图（BRD-EVENT-004 ELIMINATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigCulling.class)
public class PigCullingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cullingDate;

    private String cullingReason;
    private String cullingDest;
    private BigDecimal cullingWeight;
    private String ossIds;
    private Long operatorId;

    /** 淘汰记录人 sys_user.user_id（mp EmployeePicker 所选）。 */
    private Long cullingRecorderId;

    /** 淘汰记录人姓名（cullingRecorderId → USER_ID_TO_NICKNAME 翻译；记录列表显名不显 ID）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "cullingRecorderId")
    private String recorderName;

    /** 淘汰猪只性别原始码 F/M（enrich 批查 t_farm_pig_info 取；前端 F→母 M→公）。 */
    private String pigSex;

    /**
     * 淘汰猪只类型中文名（种母猪 / 种公猪 / 育肥猪 / 仔猪）。
     * <p>enrich 批查 t_farm_pig_info 取 pig_type 映射（sow→种母猪 boar→种公猪 fattening→育肥猪
     * piglet→仔猪，口径同 growth toPigTypeName）；未知 code / 猪只已删 → null（mp 卡片该段不渲染）。</p>
     */
    private String pigTypeName;

    /**
     * 淘汰时日龄（天，事件时口径 = cullingDate - birth_date）。
     * <p>service enrich 批查 t_farm_pig_info 取 birth_date 算；缺 birth_date 回落 introduce_date；
     * 两者均空 → null（mp 卡片该格不渲染）。</p>
     */
    private Integer ageDays;

    /** 猪只品系中文名（t_farm_pig_info.pig_strain_code → 主数据/字典翻译；缺则 null）。 */
    private String pigStrainName;

    /** 猪只品种中文名（t_farm_pig_info.pig_breed_code → 主数据/字典翻译；缺则 null）。 */
    private String pigBreedName;

    /** 淘汰时栋舍名（enrich 批查 t_farm_pig_info.barn_id → BarnMapper；缺则 null）。 */
    private String barnName;

    /** 淘汰时栏位名（enrich 批查 t_farm_pig_info.pen_id → PenMapper；缺则 null）。 */
    private String penName;

    private String remark;

    /**
     * 淘汰照片可访问 URL 列表（service JOIN sys_oss resolve）。
     * <p>mp {@code <image>} 无法携 Bearer token 访问鉴权下载端点，裸 ossId 渲不出图，
     * 故后端预解析成可直接渲染的 URL；无照片返空 list。</p>
     */
    private List<String> imageUrls;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
