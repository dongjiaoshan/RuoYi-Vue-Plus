package org.dromara.djs.breed.core.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.core.domain.Pig;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 猪只列表 / 详情出参（BRD-CORE-001）。
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Pig.class)
public class PigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "耳号简版")
    private String earNo;

    @ExcelProperty(value = "耳号全版")
    private String earTag;

    @ExcelProperty(value = "生命周期")
    private Integer lifecycleId;

    @ExcelProperty(value = "可回收")
    private Integer recyclable;

    @ExcelProperty(value = "性别")
    private String pigSex;

    @ExcelProperty(value = "类型")
    private String pigType;

    @ExcelProperty(value = "品种")
    private String pigBreedCode;

    /** 品种中文名（djs_pig_breed 字典翻译，service 层 enrich；翻不到回落 code）。 */
    @ExcelProperty(value = "品种名称")
    private String pigBreedName;

    @ExcelProperty(value = "品系")
    private String pigStrainCode;

    /** 品系中文名（djs_pig_strain 字典翻译，service 层 enrich；翻不到回落 code）。 */
    @ExcelProperty(value = "品系名称")
    private String pigStrainName;

    @ExcelProperty(value = "当前状态")
    private String currentStatus;

    @ExcelProperty(value = "进入状态时间")
    private LocalDateTime statusStartedAt;

    @ExcelProperty(value = "终止原因")
    private String endReason;

    @ExcelProperty(value = "出生日期")
    private LocalDate birthDate;

    /** 出生重 kg（仔猪耳标登记时按头录入回写；详情卡显示「N kg」）。 */
    @ExcelProperty(value = "出生重")
    private java.math.BigDecimal birthWeight;

    @ExcelProperty(value = "引种日期")
    private LocalDate introduceDate;

    /** 日龄（天）= 今天 - birth_date（缺则 introduce_date）；均缺 → null。列表 enrich 填充，供 mp 卡片显示。 */
    private Integer ageDays;

    @ExcelProperty(value = "胎次")
    private Integer parity;

    @ExcelProperty(value = "栋舍ID")
    private Long barnId;

    /** 栋舍编码（由 PigCoreServiceImpl.queryPage 末尾 batch enrich 填入，DB 不存此列）。 */
    @ExcelProperty(value = "栋舍编码")
    private String barnCode;

    /** 栋舍名称（同 barnCode，service 层 enrich）。 */
    @ExcelProperty(value = "栋舍名称")
    private String barnName;

    @ExcelProperty(value = "栏位ID")
    private Long penId;

    /** 栏位编码（同 barnCode，service 层 enrich）。 */
    @ExcelProperty(value = "栏位编码")
    private String penCode;

    /** 栏位名称（同 barnCode，service 层 enrich）。 */
    @ExcelProperty(value = "栏位名称")
    private String penName;

    @ExcelProperty(value = "最近配种ID")
    private Long matingId;

    @ExcelProperty(value = "母猪耳号")
    private String motherEar;

    /** 母系全版耳号（service 层按 motherEar 反查父母猪 ear_tag enrich；查不到为 null，前端回落短号）。 */
    @ExcelProperty(value = "母猪耳号全版")
    private String motherEarTag;

    @ExcelProperty(value = "公猪耳号")
    private String fatherEar;

    /** 父系全版耳号（service 层按 fatherEar 反查父母猪 ear_tag enrich；查不到为 null，前端回落短号）。 */
    @ExcelProperty(value = "公猪耳号全版")
    private String fatherEarTag;

    @ExcelProperty(value = "配种次数")
    private Integer matingCount;

    /**
     * 活仔数（母猪列表，所有分娩记录 SUM(live_born) — 只算活着出生的，不含死胎/木乃伊；
     * service queryPage 批量聚合 enrich，无分娩记录时 null）。
     */
    @ExcelProperty(value = "活仔数")
    private Integer liveBornCount;

    /**
     * 断奶仔猪数（母猪列表，所有断奶记录 SUM(weaned_count)；service queryPage 批量聚合 enrich，
     * 无断奶记录时 null）。
     */
    @ExcelProperty(value = "断奶仔猪数")
    private Integer weanedCount;

    /**
     * 窝均仔数 = 活仔数 ÷ 胎次（parity），保留 1 位小数；parity=0 / null 时返回 null（与活仔=活口径一致）。
     */
    @ExcelProperty(value = "窝均仔数")
    private java.math.BigDecimal avgLitterSize;

    @ExcelProperty(value = "上次配种日")
    private LocalDate lastMatingDate;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    @ExcelProperty(value = "创建者ID")
    private Long createBy;

    /**
     * 创建人姓名（ADR-0007 + 跨层契约 §4.5；用 USER_ID_TO_NICKNAME 取 sys_user.nick_name 中文名）。
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    @ExcelProperty(value = "创建人")
    private String createName;

    private Integer version;

    /**
     * 近一次用药（药品名 + 日期，如「头孢噻呋 04-13」）。
     * V1.x 补 list 查询聚合（关联 t_breed_medicine_record 取最近 1 条）；当前未填充，前端该格不渲染。
     */
    @ExcelProperty(value = "近一次用药")
    private String lastMedication;
}
