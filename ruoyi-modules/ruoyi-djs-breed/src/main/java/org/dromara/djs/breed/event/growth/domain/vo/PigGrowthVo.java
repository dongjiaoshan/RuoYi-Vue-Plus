package org.dromara.djs.breed.event.growth.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生长记录视图（BRD-EVENT-005 GROWTH）。
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigGrowth.class)
public class PigGrowthVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate measureDate;

    private BigDecimal weight;
    private BigDecimal backfatThickness;
    private BigDecimal backHeight;
    private String photoOssIds;
    private Long operatorId;

    /**
     * 操作人姓名（来自 {@code sys_user.nick_name}，ADR-0007 ＋ 跨层契约 §4.5）。
     * <p>USER_ID_TO_NICKNAME 走 NicknameTranslationImpl 取 sys_user.nick_name 中文名。</p>
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    private String barnName;
    private String penName;
    private String remark;

    /**
     * 测量时日龄（天）= measureDate - pig.birthDate（缺时 fallback introduceDate）；均空 → null。
     * <p>BRD-FIX-MP-EVENT-MISC-IA-001：mp 端"记录列表"timeline「42 日龄」（原型 15）；null → 该格不渲染。
     * 仅 queryPage enrich（admin 列表 / mp 历史用），新增 / 详情接口不填。</p>
     */
    private Integer ageDays;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
