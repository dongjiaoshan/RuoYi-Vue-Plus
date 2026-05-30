package org.dromara.djs.breed.event.farrow.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待打标分娩窝 VO（BRD-FIX-MP-EVENT-MISC-IA-001 — 仔猪耳号"选窝"网格）。
 *
 * <p>专给 mp 端"仔猪耳号"页第 1 步「选窝」用——原型 96/97 的母猪卡网格（公猪 N 头 / 母猪 N 头 /
 * 分娩日期）+ 选中后的「分娩概况」auto-fill 卡（母猪耳号 / 日龄 / 胎次 / 分娩舍栋栏 / 公母数）。</p>
 *
 * <p>口径：仅返 {@code remainEartag > 0}（仍有未贴满标的分娩），与 {@code statByFarrow} / 徽标同口径。</p>
 *
 * <p>跨层契约：{@code id} snowflake Long → 序列化 string（JacksonConfig 全局规则）。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-EVENT-MISC-IA-001
 */
@Data
public class FarrowLitterVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分娩记录主键（snowflake string）。 */
    private Long id;

    /** 母猪 pig_info.id（snowflake string）。 */
    private Long motherPigId;

    /** 母猪耳号（卡片橙色 chip）。 */
    private String motherEarNo;

    /** 分娩日期（卡片右下灰字 + 概况卡）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime farrowDate;

    /** 当时胎次（概况卡"2 胎"）。 */
    private Integer parity;

    /** 活产仔数 = 贴标上限。 */
    private Integer liveBorn;

    /** 已贴耳标数。 */
    private Integer taggedEartag;

    /** 剩余可贴 = liveBorn - taggedEartag。 */
    private Integer remainEartag;

    /** 仔猪公数（卡片"公猪 N 头"+ 概况卡"公猪数"+ 公组预铺行数）。 */
    private Integer maleCount;

    /** 仔猪母数（卡片"母猪 N 头"+ 概况卡"母猪数"+ 母组预铺行数）。 */
    private Integer femaleCount;

    /** 仔猪日龄（天）= NOW - farrowDate；farrowDate 缺时 null（概况卡"234 日龄"）。 */
    private Integer ageDays;

    /** 分娩舍栋名（概况卡"分娩舍1栋11栏"前段；冗余自 farrow.barn_name）。 */
    private String barnName;

    /** 分娩舍栏名（概况卡"…11栏"后段；冗余自 farrow.pen_name）。 */
    private String penName;
}
