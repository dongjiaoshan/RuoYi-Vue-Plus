package org.dromara.djs.warehouse.vegout.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 毛菜间出库提交（admin row185 单条内部处理 / row187 批量出库共用）。
 *
 * <p>row185 从库存查询行发起，items 只有 1 条、去向限「果蔬月台 / 饲料饲喂」；
 * row187 从毛菜间出库管理抽屉发起，items 多条、去向为出库去向字典全项。
 * 两者写入口径完全一致，只是入口与粒度不同。</p>
 *
 * @author djs
 */
@Data
public class VegOutSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 出库日期（可选当天或历史日期）。 */
    @NotNull(message = "出库日期不能为空")
    private Date outDate;

    /** 出库去向（字典 djs_stock_out_dest；veg_dock 果蔬月台 / feed 猪只饲料 有额外下游写入）。 */
    @NotBlank(message = "出库去向不能为空")
    private String outDest;

    /** 出库明细（至少 1 条）。 */
    @Valid
    @NotEmpty(message = "请至少选择一个产品")
    private List<VegOutItemBo> items;

    /** 备注。 */
    private String remark;
}
