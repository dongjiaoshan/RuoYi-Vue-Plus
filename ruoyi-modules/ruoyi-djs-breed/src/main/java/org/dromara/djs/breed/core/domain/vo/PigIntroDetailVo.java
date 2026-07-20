package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 内部引种 auto-fill 单头查猪明细出参（BRD-FIX-MP-INTRO-001）。
 *
 * <p>专供 {@code GET /applet/pig/intro-detail?pigId=}——mp 端「内部引种」segment 查耳号后
 * 带出只读「猪只信息」卡（性别/品种/品系/日龄/当前位置），所有 *Label 字段都是 service 层
 * 翻好的中文 label，mp 端直接展示，不再二次翻字典（可测试性约束：不显原始 code）。</p>
 *
 * <p>JSON 序列化：{@code pigId} 走 ruoyi {@code JacksonConfig} Long → string 全局规则，
 * mp 端不准 {@code Number(pigId)} 截断。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-INTRO-001
 */
@Data
public class PigIntroDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（snowflake，序列化为 string）。 */
    private Long pigId;

    /** 耳号简版。 */
    private String earNo;

    /** 性别原始码 F/M。 */
    private String pigSex;

    /** 性别 label（母猪/公猪），service 硬映射，不依赖字典。 */
    private String pigSexLabel;

    /** 品种编码。 */
    private String pigBreedCode;

    /** 品种 label（djs_pig_breed 翻译；翻不到回落 code）。 */
    private String pigBreedLabel;

    /** 品系编码。 */
    private String pigStrainCode;

    /** 品系 label（djs_pig_strain 翻译；字典缺时回落 code）。 */
    private String pigStrainLabel;

    /** 日龄（天）= NOW - birthDate（缺时 fallback introduceDate）；均空为 null。 */
    private Integer ageDays;

    /** 栋舍名称。 */
    private String barnName;

    /** 栏位名称。 */
    private String penName;

    /** 拼好的当前位置（如「育成舍1栋2栏」），service 层组装；缺栋舍/栏位时为 null。 */
    private String currentLocation;
}
