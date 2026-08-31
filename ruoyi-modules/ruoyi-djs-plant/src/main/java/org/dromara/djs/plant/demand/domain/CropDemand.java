package org.dromara.djs.plant.demand.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Date;

/**
 * 作物需求实体（V6-R152 运营端提需求 / V6-R153 种植端回复，一表两端）。
 *
 * <p>对应表 {@code t_plant_crop_demand}（V202609010400）。</p>
 * <ul>
 *   <li>{@code demandDate} 新增时由服务端取当天，用户不填</li>
 *   <li>{@code demandStatus} 字典 {@code djs_plant_demand_status}：pending=待回复 / replied=已回复</li>
 *   <li>{@code createBy} 既是审计字段，也是「只有创建人能删自己需求」的判定依据</li>
 * </ul>
 *
 * @author djs
 * @since V6-R152
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_crop_demand")
public class CropDemand extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 需求日期（新增时服务端取当天）。 */
    private LocalDate demandDate;

    /** 需求分类（字典 djs_plant_demand_category：plant / new_crop）。 */
    private String demandCategory;

    /** 需求内容（多行文本）。 */
    private String demandContent;

    /** 需求状态（字典 djs_plant_demand_status：pending / replied）。 */
    private String demandStatus;

    /** 需求图片 OSS ossIds 逗号分隔（多张，bizType=plant_demand）。 */
    private String imageOssIds;

    /** 回复内容（种植端填，已回复后可再改）。 */
    private String replyContent;

    /** 最后一次回复时间。 */
    private Date replyTime;

    /** 回复人 sys_user.user_id。 */
    private Long replyBy;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
