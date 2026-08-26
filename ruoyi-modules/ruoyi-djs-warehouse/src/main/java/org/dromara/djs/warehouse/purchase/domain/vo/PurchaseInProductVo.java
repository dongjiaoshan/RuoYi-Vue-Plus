package org.dromara.djs.warehouse.purchase.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 采购入库「商品维度」列表 VO（WMS-OUTSOURCE-001 需求1）。
 *
 * <p>以外购商品（{@code product_type=2}）为粒度，聚合当前库存 / 最后一次入库时间 / 采购人 /
 * 当月累计采购入库总量。区别于 {@code PurchaseInRecordVo}（流水维度，一行一笔入库流水）。</p>
 *
 * <p>跨层契约：{@code productId} / {@code supplierId} / {@code lastPurchaserId} 是 snowflake，
 * 前端按 string 处理；{@code storeLocationId} 为 CSV 多库位原始串。{@code currentStock} /
 * {@code monthInTotal} 是 BigDecimal，JSON 序列化成字符串，前端累加前必 {@code Number()}。</p>
 *
 * @author djs
 * @since WMS-OUTSOURCE-001
 */
@Data
@ExcelIgnoreUnannotated
public class PurchaseInProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商品主键（snowflake；前端按 string 处理，查看 / 入库时作 productId）。
     */
    private Long productId;

    /**
     * 商品业务码（如 PROD001）。
     */
    @ExcelProperty(value = "商品编码")
    private String productCode;

    /**
     * 商品名称。
     */
    @ExcelProperty(value = "商品名称")
    private String productName;

    /**
     * 单位（个 / 头 / Kg 等）。
     */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /**
     * 规格。
     */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /**
     * 商品缩略图 ossId（SQL 取 {@code product_thumb}）；service 经 resolver 转 url 回填 {@link #imageUrl}。
     */
    private String productThumb;

    /**
     * 商品图 ossId（SQL 取 {@code image_oss_id}，resolver L1 兜底来源）。
     */
    private String imageOssId;

    /**
     * 商品图 public URL（service 经 {@code ImageUrlResolver} 4 层回填，禁 N+1）。
     */
    private String imageUrl;

    /**
     * 商品类别（字典 {@code djs_buy_class} 的 value；前端经字典转中文展示）。
     */
    @ExcelProperty(value = "商品类别", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_buy_class")
    private String buyClass;

    /**
     * 存储库位 ID（CSV 多库位原始串，如 {@code "100,101"}；前端按 string 处理）。
     */
    private String storeLocationId;

    /**
     * 存储库位名称（service 批量 JOIN 回填；多库位「、」拼接）。
     */
    @ExcelProperty(value = "存储仓库")
    private String storeLocationName;

    /**
     * 供应商 ID（snowflake；前端按 string 处理）。
     */
    private Long supplierId;

    /**
     * 供应商名称（row23：列表「供应商」列 + 入库弹框只读展示；SQL JOIN t_md_supplier 回填，无则 null）。
     */
    @ExcelProperty(value = "供应商")
    private String supplierName;

    /**
     * 当前库存（跨库位 SUM(location_stock.product_stock)；无库存行为 0）。
     */
    @ExcelProperty(value = "当前库存")
    private BigDecimal currentStock;

    /**
     * 最后一次采购入库时间（MAX(stock_flow.flow_date) WHERE flow_type='purchase_in'；无则 null）。
     */
    @ExcelProperty(value = "最后入库时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastInTime;

    /**
     * 最后一次采购入库的采购人 user_id（snowflake；前端按 string 处理）。
     */
    private Long lastPurchaserId;

    /**
     * 最后一次采购入库的采购人中文姓名（{@code USER_ID_TO_NICKNAME} 翻译；显 nick_name 非登录账号）。
     */
    @ExcelProperty(value = "采购人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "lastPurchaserId")
    private String lastPurchaserName;

    /**
     * 当月累计采购入库总量（SUM(stock_flow.change_quantity) WHERE flow_type='purchase_in' AND 当月；
     * BigDecimal，JSON 字符串）。
     */
    @ExcelProperty(value = "当月累计采购量")
    private BigDecimal monthInTotal;

}
