package org.dromara.djs.breed.farm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 栏位详情视图对象（对齐原型「栏位详情」3-tab：大栏 / 限位栏 / 产床）。
 *
 * <p>按 {@code pen_type} 取不同字段：</p>
 * <ul>
 *   <li>大栏(big)：{@code penName} 大栏序号 + {@code headCount} 猪只头数（一栏多头）</li>
 *   <li>限位栏(stall)：{@code penName} 限位栏号 + {@code earNo} 单头母猪耳号</li>
 *   <li>产床(farrow)：{@code penName} 产床号 + {@code earNo} 母猪耳号 + {@code pigletCount} 仔猪数</li>
 * </ul>
 *
 * @author djs
 * @since BRD-AD-PROTO-ALIGN
 */
@Data
public class PenDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 栏位 ID。
     */
    private Long id;

    /**
     * 栏位编码。
     */
    private String penCode;

    /**
     * 栏位名称（序号，如「1栏」）。
     */
    private String penName;

    /**
     * 栏位类型（big / stall / farrow）。
     */
    private String penType;

    /**
     * 容量。
     */
    private Integer capacity;

    /**
     * 猪只头数（大栏用：栏内存活猪只数）。
     */
    private Integer headCount;

    /**
     * 占栏猪只耳号（限位栏 / 产床用：栏内单头存活猪只耳号；空栏为 null）。
     */
    private String earNo;

    /**
     * 占栏猪只 ID（耳号链接跳猪只详情用；空栏为 null）。
     */
    private Long pigId;

    /**
     * 仔猪数（产床用：占栏母猪最近一次分娩的活产数；无则 null）。
     */
    private Integer pigletCount;

}
