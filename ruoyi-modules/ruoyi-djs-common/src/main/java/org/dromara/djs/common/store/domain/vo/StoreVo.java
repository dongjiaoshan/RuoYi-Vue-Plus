package org.dromara.djs.common.store.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.common.store.domain.Store;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 门店主数据视图对象（SYS-MD-002 + SYS-MD-FIX-002）。
 *
 * <p>列表查询额外联表查 {@code sys_user.nick_name AS managerNickName}（store_mapper.xml 中 selectVoPage 走 wrapper SQL）；
 * 同时聚合 dept 员工数到 {@code staffCount}（V1 stub=null，下游 wire 后回填）。</p>
 *
 * @author djs
 * @since SYS-MD-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Store.class)
public class StoreVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 门店编码。
     */
    @ExcelProperty(value = "门店编码")
    private String storeCode;

    /**
     * 门店名称。
     */
    @ExcelProperty(value = "门店名称")
    private String storeName;

    /**
     * 门店简称。
     */
    @ExcelProperty(value = "门店简称")
    private String shortName;

    /**
     * 开业日期。
     */
    @ExcelProperty(value = "开业日期")
    private LocalDate openDate;

    /**
     * 门店类型（字典 djs_store_type）。
     */
    @ExcelProperty(value = "门店类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_store_type")
    private String storeType;

    /**
     * 合作状态（字典 djs_store_status）。
     */
    @ExcelProperty(value = "合作状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_store_status")
    private String businessStatus;

    /**
     * 门店地址。
     */
    @ExcelProperty(value = "地址")
    private String address;

    /**
     * 店长姓名（文本）。
     */
    @ExcelProperty(value = "店长姓名")
    private String managerName;

    /**
     * 店长电话。
     */
    @ExcelProperty(value = "店长电话")
    private String managerPhone;

    /**
     * 店长 sys_user.user_id（V1 stub：list 不展开成 user 全信息；详情页另查）。
     */
    private Long managerUserId;

    /**
     * 收银系统 ID。
     */
    @ExcelProperty(value = "收银系统 ID")
    private String posSystemId;

    /**
     * 门店图片（OSS oss_id；前端 image-preview 用）。
     */
    private Long imageOssId;

    /**
     * 备注。
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新时间。
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 更新人（sys_user.user_id）。
     */
    @ExcelProperty(value = "更新人")
    private Long updateBy;

    /**
     * 员工数量（V1 stub：当前 t_md_store 与 sys_user 仅通过 manager_user_id 关联单店长，
     * 无法直接统计员工数；service 层 fillEmployeeCount 默认填 0，待 Kevin 定义关联规则
     * （是否引入 store_user_relation / 或复用 sys_dept）后回填真实值）。
     */
    @ExcelProperty(value = "员工数量")
    private Integer employeeCount;

}
