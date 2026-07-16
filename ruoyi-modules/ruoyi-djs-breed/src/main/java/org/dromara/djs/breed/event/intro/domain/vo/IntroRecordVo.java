package org.dromara.djs.breed.event.intro.domain.vo;

import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * mp 端「引种记录」列表出参（BRD-FIX-MP-INTRO-001，原型 86 第 3 段）。
 *
 * <p>专供 {@code GET /applet/breed/intro/records}——比 admin 端 {@code PigIntroduceVo}
 * 更轻量，含 service 翻好的引种方式 label（内部/外部）+ 耳号（内部=关联猪耳号，
 * 外部=起始耳号），mp 端直接渲染列表卡，不再二次翻字典。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-INTRO-001
 */
@Data
public class IntroRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 引种单号。 */
    private String introduceNo;

    /** 引种方式原始码 external/internal。 */
    private String introduceType;

    /** 引种方式 label（内部/外部），service 硬映射。 */
    private String introduceTypeLabel;

    /** 耳号（内部=关联猪耳号；外部=起始耳号 startEarNo）。 */
    private String earNo;

    /** 引入头数。 */
    private Integer pigCount;

    /** 性别中文（F→母 / M→公；引种台账 t_farm_pig_introduce.pig_sex 直翻；无值 null）。 */
    private String pigSexLabel;

    /** 品种 label（djs_pig_breed 翻译；翻不到回落 code）。 */
    private String pigBreedLabel;

    /** 品系 label（djs_pig_strain 翻译；翻不到回落 code）（601-6）。 */
    private String pigStrainLabel;

    /**
     * 日龄（天，601-6 / row224）。
     * <p>内部引种：关联猪只 {@code pig_id → t_farm_pig_info.birth_date} 算 NOW - birthDate。
     * 外部引种：外部引种表单必填「出生日期」落到新建猪 {@code t_farm_pig_info.birth_date}，
     * 记录 {@code start_ear_no} 即当次首头猪耳号，沿 {@code start_ear_no = ear_no} 取 birth_date 同口径算。
     * 仅当 birthDate 缺失（历史脏数据）才 null（mp 端 {@code '-'} 兜底）。</p>
     */
    private Integer ageDays;

    /** 引种日期。 */
    private LocalDate introduceDate;

    /**
     * 供应商名称（row225，外部引种记录卡左下显示）。
     * <p>外部引种关联 {@code t_md_supplier.supplier_name}，service 批查装配；
     * 内部引种无供应商 → null（mp 端不渲染该段）。</p>
     */
    private String supplierName;

    /** 引种人员 userId（EmployeePicker 所选；legacy 可能自由文本）。 */
    private String operator;

    /** 引种人员姓名（FIX-CC-PERSON-001 #16：operator userId → USER_ID_TO_NICKNAME 翻译；mp 记录列表显名不显 ID，非数字 operator 翻不到回落 null）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operator")
    private String operatorName;

    /** 凭证图 OSS IDs 逗号分隔（外部引种有；mp 端渲染缩略图）。 */
    private String proofOssIds;

    /**
     * 凭证图可访问 URL 列表（service JOIN sys_oss resolve）。
     * <p>mp {@code <image>} 无法携 Bearer token 访问鉴权下载端点，裸 ossId 渲不出图，
     * 故后端预解析成可直接渲染的 URL；无凭证图返空 list。</p>
     */
    private List<String> proofImageUrls;
}
