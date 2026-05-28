package org.dromara.djs.warehouse.cut.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

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

    private BigDecimal inWeight;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date inTime;

    /**
     * 状态（在 mp 端"待领用"列表中过滤后均为 in_stock）。
     */
    private String status;

}
