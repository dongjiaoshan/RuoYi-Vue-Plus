package org.dromara.djs.plant.demand.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.plant.demand.domain.CropDemand;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 作物需求视图对象（V6-R152 运营端列表 / V6-R153 种植端列表共用）。
 *
 * @author djs
 * @since V6-R152
 */
@Data
@AutoMapper(target = CropDemand.class)
public class CropDemandVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 需求日期。 */
    private LocalDate demandDate;

    /** 需求分类（字典 djs_plant_demand_category）。 */
    private String demandCategory;

    /** 需求内容。 */
    private String demandContent;

    /** 需求状态（字典 djs_plant_demand_status）。 */
    private String demandStatus;

    /** 需求图片 OSS ossIds 逗号分隔。 */
    private String imageOssIds;

    /** 回复内容。 */
    private String replyContent;

    /** 回复时间。 */
    private Date replyTime;

    /** 回复人 ID（{@code sys_user.user_id}）。 */
    private Long replyBy;

    /** 回复人姓名（注解翻译）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "replyBy")
    private String replyByName;

    /** 需求创建时间。 */
    private Date createTime;

    /** 创建人 ID（前端据此判断「删除」按钮是否可见；后端仍会独立校验）。 */
    private Long createBy;

    /** 创建人姓名（注解翻译）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

    /** 更新时间。 */
    private Date updateTime;
}
