package org.dromara.djs.warehouse.trace.pub.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 公开追溯聚合 VO（TRC-PUB-API-001，C 端顾客扫码展示）。
 *
 * <h3>定位</h3>
 * <p>顾客扫码追溯页的<b>唯一公开只读数据源</b>。{@code GET /djs/public/trace/{traceCode}}
 * （@SaIgnore 无登录）按 {@code produce_code} 一次性聚合一条追溯码的全链路展示数据。
 * 前端按 {@link #codeType}（pork/veg/gift）路由到对应渲染页。</p>
 *
 * <h3>C 端裁剪原则</h3>
 * <ul>
 *   <li>无 Excel 注解（非导出）；与 admin {@code TraceCodeDetailVo} 独立。</li>
 *   <li>不暴露内部敏感 ID（tenant_id / del_unique / 裸 operatorId）；
 *       operatorId 翻译为 {@link TimelineNode#operatorName} 再返。</li>
 *   <li>{@link #codeType} / {@link TimelineNode#traceContent} 返<b>原码值字符串</b>，
 *       字典中文映射由 mp 前端做（mp V1 硬编码中文，无 i18n 基建）。</li>
 *   <li>图片字段（{@code imageUrl}）返后端已解析的<b>可直接展示 URL</b>（非 ossId，
 *       C 端无登录无法反查 oss）。</li>
 * </ul>
 *
 * @author djs
 * @since TRC-PUB-API-001
 */
@Data
public class PublicTraceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 追溯码类型原值：{@code pork} | {@code veg} | {@code gift}（前端路由依据）。 */
    private String codeType;

    /** 通用产品信息块（三业态共用）。 */
    private ProductBlock product;

    /** 流程时间轴（按 trace_time 倒序，最近的事件在前；节点数 = 实际 event 行数）。 */
    private List<TimelineNode> timeline;

    // ============ pork 专属（veg/gift 为 null）============

    /** 猪只信息（pork 专属）。 */
    private PigBlock pig;

    /** 生长记录（pork 专属）。 */
    private List<GrowthRow> growthRecords;

    /** 用药 / 疫苗保健记录（pork 专属）。 */
    private List<MedicationRow> medications;

    /**
     * 谱系（父系 / 母系信息，pork 专属；无父母耳号 → null，前端整块隐藏）。
     * 复用 breed {@code ISowDetailService#queryPedigree}：本猪 father_ear/mother_ear 反查父母 Pig
     * 的品种 label / 日龄 / 胎次（母系含胎次，父系无）。
     */
    private PedigreeBlock pedigree;

    /** 检疫信息（pork 专属；V1 数据源缺口，返 null）。 */
    private QuarantineBlock quarantine;

    /** 门店信息（pork 专属）。 */
    private StoreBlock store;

    // ============ veg 专属（pork/gift 为 null）============

    /** 作物信息（veg 专属）。 */
    private CropBlock crop;

    /** 地块信息（veg 专属）。 */
    private PlotBlock plot;

    /** 地块农事记录（veg 专属）。 */
    private List<PlotRecordRow> plotRecords;

    /**
     * 该地块历史种植作物（veg 专属；按种植明细 {@code t_plant_plant_details} 倒序，无记录 → 空 list，前端整块隐藏）。
     * 数据源：trace_code.plot_id → 该地块全部种植明细 + 作物名 / 作物图。与 {@link #plotRecords}（农事记录时间轴）并存。
     */
    private List<PlotCropHistoryRow> plotCropHistory;

    /** 有机认证证书（veg 专属）。 */
    private List<OrganicCertRow> organicCerts;

    // ============ gift 专属（pork/veg 为 null）============

    /** 礼盒子产品（gift 专属；V1 gift_components 空 → 空 list）。 */
    private List<ComponentRow> components;

    // ============================ 内嵌块 ============================

    /** 通用产品信息块。 */
    @Data
    public static class ProductBlock implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 产品名称。 */
        private String name;
        /** 规格（也作 weight 兜底来源）。 */
        private String spec;
        /** 净重（V1 无独立列，从 spec 推断兜底，可能为 null）。 */
        private String weight;
        /** 产品描述（{@code t_warehouse_product_info.product_desc}；无则 null）。 */
        private String description;
        /** 产品图可访问 URL（ossId 后端解析；无图为 null）。 */
        private String imageUrl;
        /** 追溯码（= produce_code）。 */
        private String produceCode;
        /** 打包日期（V1 无独立列，用追溯码生成时间兜底）。 */
        private Date packDate;
        /** 生长天数（veg 专属，源 {@code trace_code.plant_days}；无则 null）。 */
        private Integer growthDays;
        /** 来源地块名（veg 专属，源 {@code t_plant_plot_info.plot_name}；无则 null）。 */
        private String plotName;
        /** 采摘日期（veg 专属，源 {@code trace_code.havest_date}；无则 null）。 */
        private LocalDate harvestDate;
    }

    /** 时间轴节点。 */
    @Data
    public static class TimelineNode implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 节点类型原码值（marketing/singe/slaughter/acid/in_stock/ship/arrival，前端映射中文）。 */
        private String traceContent;
        /** 节点时间。 */
        private LocalDateTime traceTime;
        /** 操作人姓名（operatorId 翻译，无裸 id）。 */
        private String operatorName;
        /**
         * 该节点工序重量 kg（源 {@code trace_event.event_data} 的 {@code weight} 字段，如 {@code "5.20"}）；
         * event_data 为空 / 无 weight / 解析失败 → null，前端有才展示「· 5.20kg」。
         */
        private String weight;
    }

    /** 猪只信息块（pork）。 */
    @Data
    public static class PigBlock implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 耳号。 */
        private String earNo;
        /** 性别码（{@code F=母 M=公}，源 {@code t_farm_pig.pig_sex}，前端映射中文）。 */
        private String sex;
        /** 体重（kg，源最新一条生长记录 {@code PigGrowth.weight}；无生长记录为 null）。 */
        private String weight;
        /** 猪只照片可访问 URL（源最新生长记录 {@code photo_oss_ids} 首图后端解析；无图为 null）。 */
        private String photoUrl;
        /** 品种码（前端映射中文）。 */
        private String breed;
        /** 出生日期。 */
        private LocalDate birthDate;
        /** 日龄（出栏日 − 出生日，天；任一缺 → null）。 */
        private Integer ageDays;
        /** 出栏日期（Pig 无列，用 marketing 事件时间兜底，可能为 null）。 */
        private LocalDateTime marketDate;
        /** 所属农场名。 */
        private String farmName;
        /** 栋舍名（用生长记录冗余兜底，可能为 null）。 */
        private String barnName;
    }

    /** 生长记录行（pork）。 */
    @Data
    public static class GrowthRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 测量日期。 */
        private LocalDate date;
        /** 日龄（测量日 − 出生日，天；出生日缺 → null）。 */
        private Integer ageDays;
        /** 体重（kg）。 */
        private String weight;
        /** 背膘厚（mm）。 */
        private String backfat;
        /** 操作人姓名（operatorId 翻译；缺 → null）。 */
        private String operatorName;
        /** 猪只照片可访问 URL（photo_oss_ids 首图后端解析；无图为 null）。 */
        private String photoUrl;
    }

    /** 用药 / 疫苗保健记录行（pork）。 */
    @Data
    public static class MedicationRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 用药日期。 */
        private LocalDateTime date;
        /** 日龄（用药日 − 出生日，天；出生日缺 → null）。 */
        private Integer ageDays;
        /** 药品名称。 */
        private String name;
        /** 用药类型原码值（health/treatment/vaccine，前端映射中文）。 */
        private String type;
        /** 用药原因（可能为 null）。 */
        private String reason;
        /** 操作人姓名（MedRecord.operator_name 冗余列优先，缺则 operatorId 翻译）。 */
        private String operatorName;
    }

    /**
     * 谱系块（pork；父系 / 母系信息）。
     * 各字段关联缺失（耳号空 / 查不到父母猪）→ 对应字段 null；
     * 父母耳号全空 → 整个 pedigree 块返 null（service 层不 set），前端隐藏。
     */
    @Data
    public static class PedigreeBlock implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 父系耳号（本猪 father_ear；缺 → null）。 */
        private String sireEarNo;
        /** 父系品种 label（djs_pig_breed 翻译；缺 → null）。 */
        private String sireBreed;
        /** 父系日龄（天；缺 → null）。 */
        private Integer sireAgeDays;
        /** 母系耳号（本猪 mother_ear；缺 → null）。 */
        private String damEarNo;
        /** 母系品种 label（缺 → null）。 */
        private String damBreed;
        /** 母系日龄（天；缺 → null）。 */
        private Integer damAgeDays;
        /** 母系胎次（缺 → null）。 */
        private Integer damParity;
    }

    /** 检疫信息块（pork；V1 缺口返 null，字段保留供前端空判）。 */
    @Data
    public static class QuarantineBlock implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 检疫证号。 */
        private String certNo;
        /** 检疫机构。 */
        private String agency;
    }

    /** 门店信息块（pork）。 */
    @Data
    public static class StoreBlock implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 门店名。 */
        private String name;
        /** 门店地址。 */
        private String address;
        /** 门店配图 URL（由 t_md_store.image_oss_id 解析；无图为 null，前端用默认图兜底 · row146）。 */
        private String imageUrl;
    }

    /** 作物信息块（veg）。 */
    @Data
    public static class CropBlock implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 作物名称（trace 无 cropId，用产品名兜底）。 */
        private String name;
        /** 品种（trace 无作物关联时为 null）。 */
        private String variety;
    }

    /** 地块信息块（veg）。 */
    @Data
    public static class PlotBlock implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 地块名。 */
        private String plotName;
        /** 片区名。 */
        private String zoneName;
        /** 所属大区（源 {@code t_plant_plot_zone.zone_belong}，例「东部」；无则 null）。 */
        private String zoneBelong;
        /** 面积。 */
        private String area;
    }

    /** 地块农事记录行（veg）。 */
    @Data
    public static class PlotRecordRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 农事日期。 */
        private LocalDate date;
        /** 农事类型原码值（前端映射中文）。 */
        private String workType;
        /** 详情（备注）。 */
        private String detail;
        /** 记录人姓名（{@code operator_user_id} 翻译 nick_name；缺 → null）。 */
        private String operatorName;
    }

    /** 地块历史种植作物行（veg）。 */
    @Data
    public static class PlotCropHistoryRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 作物名称（t_plant_crop_info.crop_name；缺 → null）。 */
        private String cropName;
        /** 生长天数（采摘日 − 种植日；任一缺 → null）。 */
        private Integer growthDays;
        /** 种植日期（明细 begin_actualdate，无实际则计划 plant_date 文本不取，仅 actual 有则填）。 */
        private LocalDate startDate;
        /** 采摘日期（明细 begin_harvestdate 实际，缺则 earliest_harvestdate 计划）。 */
        private LocalDate endDate;
        /** 作物图可访问 URL（crop_image_preview ossId 后端解析；无图为 null）。 */
        private String imageUrl;
    }

    /** 有机认证证书行（veg）。 */
    @Data
    public static class OrganicCertRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 证书类型：{@code crop}（果蔬/作物有机证）| {@code plot}（地块有机证）（前端分组/分屏依据）。 */
        private String certType;
        /** 证书图可访问 URL（ossId 后端解析；无图为 null）。首张，向后兼容旧前端；多图见 {@link #imageUrls}。 */
        private String imageUrl;
        /** 证书全部图可访问 URL（row162：一证多图全解析，前端自适应网格展示；无图为空 list）。 */
        private java.util.List<String> imageUrls;
        /** 颁发机构。 */
        private String issuer;
        /** 证书编号。 */
        private String certNo;
        /** 有效期（截止日；V1 仅存单个有效期，validFrom 留 null）。 */
        private LocalDate validFrom;
        /** 有效期（截止日）。 */
        private LocalDate validTo;
    }

    /** 礼盒子产品行（gift；V1 空）。 */
    @Data
    public static class ComponentRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 子产品业态：{@code pork} | {@code veg}（前端下钻路由依据）。 */
        private String subType;
        /** 子追溯码（点击下钻到对应子追溯页）。 */
        private String subTraceCode;
        /** 子产品名称。 */
        private String productName;
    }
}
