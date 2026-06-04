package org.dromara.djs.store.split.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 门店白条分割明细 VO（STR-SPLIT-001 admin 列表 + 导出行）。
 *
 * <p>映射 {@code t_warehouse_product_inhouse} 中 {@code source='store'} 的门店再分行。
 * {@code cutPart} 部位字典 fe 翻译；{@code createByName} 走 ruoyi {@code USER_ID_TO_NAME}
 * 翻译（5.5.x 实现的是 {@code UserNameTranslationImpl}，不写 {@code USER_ID_TO_NICKNAME}）。</p>
 *
 * @author djs
 * @since STR-SPLIT-001
 */
@Data
@ExcelIgnoreUnannotated
public class StoreSplitVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * inhouse 行主键（snowflake，全链路 string）。
     */
    @ExcelProperty(value = "记录ID")
    private Long id;

    /**
     * 分割部位字典 {@code djs_pig_cut_part}：lean / part / bone / skin / scrap。
     */
    @ExcelProperty(value = "部位")
    private String cutPart;

    /**
     * 产品名称（冗余 SKU 名）。
     */
    @ExcelProperty(value = "产品")
    private String productName;

    /**
     * 再分重量 kg。
     */
    @ExcelProperty(value = "重量(kg)")
    private BigDecimal productWeight;

    /**
     * 入库库位 FK。
     */
    @ExcelProperty(value = "库位")
    private Long locationId;

    /**
     * 源白条溯源关联（可空）。
     */
    private Long whiteBarId;

    /**
     * 来源（固定 store=门店再分）。
     */
    @ExcelProperty(value = "来源")
    private String source;

    /**
     * 生产日期。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "生产日期")
    private Date produceDate;

    /**
     * 备注。
     */
    @ExcelProperty(value = "备注")
    private String remark;

    private Long createBy;

    /**
     * 录入人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "录入人")
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
