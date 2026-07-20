package org.dromara.djs.warehouse.trace.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 追溯码管理查询参数（TRC-ADMIN-001，admin only 无 mp）。
 *
 * <p>{@code codeType} 是 {@code t_warehouse_trace_code.code_type} VARCHAR(16) 列实存值
 * {@code pork / veg / gift}（不是数字 1/2/3），admin 端 el-tabs 切 tab 传字符串。
 * {@code productName} 主表无此列，service 查 product 后内存过滤。
 * 日期范围按主表 {@code create_time} 过滤（DDL 无独立生产日期列）。</p>
 *
 * @author djs
 * @since TRC-ADMIN-001
 */
@Data
public class TraceCodeQuery {

    /**
     * 追溯码类型（字典 {@code djs_trace_code_type}，列实存值 pork / veg / gift）。tab 切换传值。
     */
    private String codeType;

    /**
     * 追溯码模糊查（{@code produce_code}）。
     */
    private String produceCode;

    /**
     * 产品名模糊查（主表无 product_name 列，service JOIN product 后内存过滤）。
     */
    private String productName;

    /**
     * 猪只耳号精确查（{@code pig_ear_no}，猪肉追溯码链）。
     */
    private String pigEarNo;

    /**
     * 生成来源筛选：{@code warehouse}=仓库生码（打包/发货产出）/ {@code store}=门店现场分割打包生码。
     *
     * <p>按 {@code remark}「现场生码」前缀区分：门店 {@code genPorkOnsiteCode} 写 remark="现场生码 部位=… 重量=…kg"，
     * 仓库生码 remark 为空/非此前缀。空 = 全部（仓库 + 门店都列）。猪肉追溯码管理用此维度筛选。</p>
     */
    private String source;

    /**
     * 生成时间起（按主表 create_time 过滤）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date beginDate;

    /**
     * 生成时间止（按主表 create_time 过滤）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endDate;

    /**
     * 到店日期起（按 trace_event ARRIVAL 事件 trace_time 过滤）。
     *
     * <p>果蔬追溯码管理默认显示「当天到店」：到店日期是 trace_event 的 arrival 事件时间，
     * 不是主表 create_time（生成时间）。buildWrapper 先按本区间查 arrival 事件命中的
     * produce_code 集合，再 in 主表过滤，语义对齐列表展示的「到店日期」列。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arrivalBeginDate;

    /**
     * 到店日期止（按 trace_event ARRIVAL 事件 trace_time 过滤）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arrivalEndDate;

}
