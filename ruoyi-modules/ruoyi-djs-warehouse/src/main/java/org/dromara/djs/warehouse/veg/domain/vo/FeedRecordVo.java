package org.dromara.djs.warehouse.veg.domain.vo;

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
 * 有机饲喂记录列表 VO（WMS-FEED-RECORD-001，仓库-admin 行21「有机饲喂记录」只读菜单）。
 *
 * <p>over {@code t_warehouse_feed_log} 全量分页列表，覆盖两类来源：
 * 毛菜处理间（{@code feed_type='veg_handle'}）+ 仓库领用（{@code feed_type='warehouse'}）。
 * 仓库来源行 {@code crop_id/crop_name} 恒空（无作物维度），作物名/作物图列对这些行留空，前端占位兜底。</p>
 *
 * <ul>
 *   <li>{@code cropImageOssId}：作物图 ossId，LEFT JOIN crop 取 {@code crop_image_url 首图 → image_oss_id} 兜底（用户上传优先）。</li>
 *   <li>{@code feedType}：原始值，前端用字典 {@code djs_feed_type} 渲染中文（毛菜间 / 仓库）。</li>
 *   <li>{@code operatorName}：ruoyi {@code USER_ID_TO_NICKNAME} 注解翻译。</li>
 * </ul>
 *
 * @author djs
 * @since WMS-FEED-RECORD-001
 */
@Data
@ExcelIgnoreUnannotated
public class FeedRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 饲喂时间（精确到时分秒）。
     */
    @ExcelProperty(value = "日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date feedDate;

    /**
     * 作物 ID（仓库来源行恒空）。
     */
    private Long cropId;

    /**
     * 作物名称（仓库来源行恒空）。
     */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /**
     * 产品名称（row54：作物名称右边新增一列）。
     *
     * <p>取 {@code feed_log.product_id → product_info.product_name}。两类来源都带 product_id：
     * 毛菜间是「去向=饲料」那笔录入里工人选的处理产品，仓库领用是领的原材料本身。
     * 仓库来源行没有作物维度，「作物名称」列本就回落显示产品名（row92），故那类行两列同名，属预期。</p>
     *
     * <p>⚠️ 这一列有意义的前提是写入端落的是<b>工人选的产品</b>而不是作物默认产品。
     * 写入端曾经用 {@code resolveProductIdByCrop} 按作物反解，红薯杆的饲喂被记成红薯，
     * 这一列会整列退化成作物名；已在 {@code VegetableHandleServiceImpl.insertFeedLog / insertPickFeed} 修正，
     * 历史行由 {@code V202608300900} 回填。改那两处写入前先想清楚这一列会不会又退化。</p>
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /**
     * 作物图 ossId（仓库来源行恒空；前端走 oss listByIds 转 url，空时占位兜底）。
     */
    private String cropImageOssId;

    /**
     * 饲喂饲料量(kg)。
     */
    @ExcelProperty(value = "饲喂饲料量")
    private BigDecimal feedWeight;

    /**
     * 饲喂来源原始值（字典 djs_feed_type：veg_handle 毛菜间 / warehouse 仓库）。
     */
    @ExcelProperty(value = "提供位置", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_feed_type")
    private String feedType;

    /**
     * 饲喂位置（库位 FK）。
     */
    private Long locationId;

    /**
     * 饲喂位置名称（@Select JOIN 回填，仓库来源有值）。
     */
    private String locationName;

    /**
     * 操作人 user_id。
     */
    private Long operatorId;

    /**
     * 操作人姓名（注解翻译 sys_user.nick_name）。
     */
    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;
}
