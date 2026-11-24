package org.dromara.djs.warehouse.cross.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/** 同一次出栏对应的猪只生日与首次接收日龄，只读展示投影。 */
@Data
public class PigReceptionAgeVo {
    private Long barInfoId;
    private Date receiveTime;
    private LocalDate birthDate;
    private Integer ageDays;
}
