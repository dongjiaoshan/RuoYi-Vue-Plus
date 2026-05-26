package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 猪只 picker 轻量出参（BRD-LIST-001 sub-step 1）。
 *
 * <p>专供 {@code GET /applet/pig/search} 返回——mp 端事件表单的 PigPicker 组件只需展示
 * 耳号 + 性别 + 类型 + 当前状态 + 栋舍/栏位编码，不需要完整 PigVo 全字段。</p>
 *
 * <p>JSON 序列化：{@code id} 走 ruoyi {@code JacksonConfig} Long → string 全局规则，
 * mp 端不准 {@code Number(id)} 截断（参 {@code coder-djs-cross-layer-contract §契约 1}）。</p>
 *
 * @author djs
 * @since BRD-LIST-001
 */
@Data
public class PigSearchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（snowflake string，mp 端 picker 选中后传 earNo，pigId 仅作 key/tracking）。 */
    private Long id;

    /** 耳号简版（picker 主显示 + emit 值）。 */
    private String earNo;

    /** 性别 F/M（castrate 表单按 'M' 过滤用）。 */
    private String pigSex;

    /** 类型 sow/boar/piglet/fattening。 */
    private String pigType;

    /** 当前 lifecycle（HB/PZ/PH/FM/DN/LC/KH/FQ/BOAR_ACTIVE/END，下拉副标识用）。 */
    private String currentStatus;

    /** 栋舍编码（service enrich，避免 N+1）。 */
    private String barnCode;

    /** 栏位编码（service enrich）。 */
    private String penCode;
}
