package org.dromara.djs.common.store.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.common.store.domain.Store;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 门店主数据视图对象（SYS-MD-002）。
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
     * 门店类型（{@code direct} / {@code franchise}）。
     */
    @ExcelProperty(value = "门店类型")
    private String storeType;

    /**
     * 经营状态（1=合作中 / 0=已终止）。
     */
    @ExcelProperty(value = "经营状态")
    private Integer businessStatus;

    /**
     * 门店地址。
     */
    @ExcelProperty(value = "地址")
    private String address;

    /**
     * 联系人。
     */
    @ExcelProperty(value = "联系人")
    private String contactName;

    /**
     * 联系电话。
     */
    @ExcelProperty(value = "联系电话")
    private String contactPhone;

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

}
