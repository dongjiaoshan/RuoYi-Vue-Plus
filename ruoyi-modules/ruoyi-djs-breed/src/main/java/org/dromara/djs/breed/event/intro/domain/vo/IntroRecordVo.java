package org.dromara.djs.breed.event.intro.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

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

    /** 品种 label（djs_pig_breed 翻译；翻不到回落 code）。 */
    private String pigBreedLabel;

    /** 引种日期。 */
    private LocalDate introduceDate;

    /** 引种人员（V1 自由文本）。 */
    private String operator;

    /** 凭证图 OSS IDs 逗号分隔（外部引种有；mp 端渲染缩略图）。 */
    private String proofOssIds;
}
