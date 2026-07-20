package org.dromara.djs.breed.event.weaning.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.weaning.domain.PigWeaning;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 断奶记录视图（BRD-EVENT-002 WEAN）。
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigWeaning.class)
public class PigWeaningVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;
    private Long farrowId;
    private Long breedingId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime weaningDate;

    private Integer weanedCount;
    private BigDecimal weanedWeight;
    private BigDecimal avgWeanedWeight;
    private Long operatorId;

    /**
     * 记录人员姓名（operatorId → USER_ID_TO_NICKNAME 翻译；记录卡第 3 行展示；翻不到回落 null）。
     * 序列化时由 @Translation 自动翻 sys_user.nick_name，service 不显式 enrich。
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 母猪断奶时日龄（天，row24）= weaningDate - 母猪 birth_date。
     * <p>断奶表无日龄快照列，按断奶日期 - 母猪出生日期反算（历史快照口径，非「至今」日龄）；
     * 缺出生日期 → null（前端该格不渲染）。queryPage 后批量 enrich。</p>
     */
    private Integer ageDaysAtWean;

    /**
     * 断奶时胎次（row24）。优先取关联分娩 {@code t_farm_pig_farrow.parity}（当时胎次快照），
     * 缺分娩记录回退母猪当前 {@code parity}。queryPage 后批量 enrich；均缺 → null。
     */
    private Integer parityAtWean;

    /**
     * 母猪断奶时栋舍名（row24）。断奶表无位置快照列，退化取母猪当前 barn_id 对应栋舍名
     * （断奶后若已转移则与断奶时不符，口径见 service 注释）。queryPage 后批量 enrich。
     */
    private String barnName;

    /** 母猪断奶时栏位名（row24）。同 {@link #barnName}，退化取母猪当前 pen_id 对应栏位名。 */
    private String penName;

    /**
     * 仔猪品系中文名（row24，记录卡第 3 行）。同窝仔猪品系一致，取该窝已耳标仔猪真实 pig_strain_code
     * （母×父 → 育种配置派生，与母猪可能不同；未耳标窝回落母猪 code），经 t_farm_breed_info 主表名解析
     * （缺回落 djs_pig_strain 字典 / 原始 code）。queryPage 后批量 enrich。
     */
    private String pigStrainName;

    /** 仔猪品种中文名（row24，记录卡第 3 行）。同 {@link #pigStrainName}，取该窝仔猪真实 pig_breed_code 解析。 */
    private String pigBreedName;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 逐头录重明细（BRD-FIX-MP-EVENT-BREED-IA-001；录入回显 / 记录回看用）。 */
    private List<PigWeaningDetailVo> details;
}
