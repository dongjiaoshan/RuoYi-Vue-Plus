package org.dromara.djs.warehouse.product.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 产品 picker 轻量 VO（MP-UX-002）。
 *
 * <p>给 mp {@code ProductPicker} 用——除 id + name + code 三件基本套件外，额外携带
 * {@code productUnit / productSpec / belongType} 三字段，便于 mp 业务页 select 时回填
 * 表单的"单位 / 规格 / 归属"列（避免再次远程查 detail）。</p>
 *
 * <p>不复用 {@link ProductInfoVo}，避免缩略图 / 原图 / 礼盒组件 / 审计字段进 mp 流量。</p>
 *
 * @author djs
 * @since MP-UX-002
 */
@Data
public class ProductPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品 ID（snowflake，Jackson 序列化为 string）。
     */
    private Long id;

    /**
     * 产品业务码（{@code productId} 字段，原 doc/11 §2.5 R6 业务码，如 PROD001）。
     */
    private String productCode;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 字典 {@code djs_product_type}：1=自产 / 2=外购 / 3=礼盒。
     */
    private Integer productType;

    /**
     * 单位（kg / 个 / 盒）。
     */
    private String productUnit;

    /**
     * 规格（500g/包）。
     */
    private String productSpec;

    /**
     * 字典 {@code djs_belong_type}：业务页面常按归属类型锁选项。
     */
    private String belongType;

}
