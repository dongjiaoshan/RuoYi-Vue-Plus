package org.dromara.djs.warehouse.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.trace.domain.TracePageConfig;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 追溯码页面配置 VO（V6-R146「追溯码配置管理」列表 + 详情共用）。
 *
 * <p>列表固定两行（猪肉 / 果蔬），无搜索无分页无导出，所以不带 Excel 注解。</p>
 *
 * @author djs
 * @since V6-R146
 */
@Data
@AutoMapper(target = TracePageConfig.class)
public class TracePageConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 追溯码类型：{@code pork} / {@code veg}。 */
    private String codeType;

    /** 追溯码名称（列表第一列）。 */
    private String configName;

    /** 基地介绍页图片 ossId（雪花，前端全链路 string；空 = 未配置）。 */
    private String baseIntroImageOssId;

    /** 基地介绍页图片可访问 URL（service 层批量解析回填；未配置或查不到为 null）。 */
    private String baseIntroImageUrl;

    /** 更新时间。 */
    private Date updateTime;

    /** 更新人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射取数翻译成 updateByName。 */
    private Long updateBy;

    /** 更新人姓名（注解翻译，VO 序列化时填）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "updateBy")
    private String updateByName;
}
