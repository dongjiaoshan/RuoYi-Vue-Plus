package org.dromara.djs.warehouse.product.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.product.domain.ProductInfo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 产品视图对象（WMS-MD-002）。
 *
 * <p>字典翻译走 admin 端 {@code <dict-tag>} 自渲染（ADR-0004 §2.3 范式），后端 VO 不主动 JOIN
 * {@code sys_dict_data}，保持瘦 VO。</p>
 *
 * <p>{@code giftComponents} 仅 {@code productType=3} 详情接口注入；列表查询不带（避免 N+1）。</p>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProductInfo.class)
public class ProductInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "产品编码")
    private String productId;

    @ExcelProperty(value = "产品名称")
    private String productName;

    @ExcelProperty(value = "产品类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_product_type")
    private Integer productType;

    @ExcelProperty(value = "单位")
    private String productUnit;

    @ExcelProperty(value = "规格")
    private String productSpec;

    @ExcelProperty(value = "归属类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_belong_type")
    private String belongType;

    @ExcelProperty(value = "外购类", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_buy_class")
    private String buyClass;

    @ExcelProperty(value = "缩略图")
    private String productThumb;

    @ExcelProperty(value = "原图")
    private String productImg;

    /** 主图 ossId（IMG-LIB-001 L1）。 */
    private String imageOssId;

    /** 图来源（IMG-LIB-001：0 自动 / 1 手动）。 */
    private Integer imageSource;

    /** 主图 public URL（IMG-LIB-001 resolver 4 层兜底回填）。 */
    private String imageUrl;

    @ExcelProperty(value = "产品属性", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_product_attr")
    private Integer productAttr;

    @ExcelProperty(value = "生产车间", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_product_workshop")
    private Integer productWorkshop;

    @ExcelProperty(value = "存储库位")
    private String storeLocationId;

    /**
     * 存储仓库名称（service 层按 store_location_id 关联 location_info 回填；多库位逗号拼接）。
     */
    @ExcelProperty(value = "存储仓库")
    private String storeLocationName;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private Integer productStatus;

    @ExcelProperty(value = "原材料 ID")
    private Long productMaterial;

    /**
     * 关联原材料产品名称（row31：详情「原材料产品」列；service 按 {@link #productMaterial} 回查 product_name 回填）。
     */
    private String productMaterialName;

    @ExcelProperty(value = "描述")
    private String productDesc;

    @ExcelProperty(value = "原料计算量")
    private BigDecimal materialNum;

    @ExcelProperty(value = "是否发货", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_yes_no")
    private Integer isDelivery;

    @ExcelProperty(value = "供应商 ID")
    private Long supplierId;

    @ExcelProperty(value = "是否可外购", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_yes_no")
    private Integer isBuyOut;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新时间。
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 更新人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射取数翻译成 updateByName。
     */
    private Long updateBy;

    /**
     * 更新人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "更新人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "updateBy")
    private String updateByName;

    /**
     * 礼盒组件清单（详情接口注入；{@code productType=3} 时非空）。
     */
    private List<GiftBoxVo> giftComponents;

}
