package org.dromara.djs.warehouse.cut.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 白条 minimal VO（WMS-PIG-002 mp 端"待领用白条"列表用）。
 *
 * <p>仅暴露 mp 工人选择白条时需要的字段；admin 列表不展示，因 admin 不渲染 bar_info。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
public class BarInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String barId;

    private String earNo;

    /**
     * 出栏重量 kg（领用称重校验上界：领用称重不应大于该值）。
     */
    private BigDecimal marketingWeight;

    private BigDecimal inWeight;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date inTime;

    /**
     * 状态（在 mp 端"待领用"列表中过滤后均为 in_stock）。
     */
    private String status;

    /**
     * 该白条燎毛实际产出的分产品（半只 / 五花肉 / 整只 等，FIX-WMS-OUTSOURCE-001 行51）。
     *
     * <p>取 {@code t_warehouse_product_inhouse WHERE white_bar_id = bar.id}（燎毛入库写入的篮子）。
     * admin 白条领用卡片据此展示「燎毛实际产出的各分产品 + 各自重量」，取代原仅显「白条(整只)+整猪重量」。
     * 燎毛只入了整只（单产品）时即一项；半只 + 五花肉等多产出时各一项。空列表 = 该白条无 inhouse 产出记录
     * （前端回落仅显整只白条信息）。</p>
     */
    private List<BurnProduct> burnProducts;

    /**
     * 燎毛产出分产品 minimal（产品名 + 重量 + 单位），白条领用卡片展示用。
     */
    @Data
    public static class BurnProduct implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 产品名（如「白条·半只」「五花肉」「整只」）。
         */
        private String productName;

        /**
         * 该分产品燎毛入库重量 kg。
         */
        private BigDecimal productWeight;

        /**
         * 计量单位（kg 等）。
         */
        private String productUnit;
    }

}
