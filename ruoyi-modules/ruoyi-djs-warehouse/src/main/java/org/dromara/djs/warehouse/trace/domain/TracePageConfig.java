package org.dromara.djs.warehouse.trace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 追溯码页面配置实体（V6-R146「追溯码配置管理」）。
 *
 * <p>按 {@link #codeType}（pork / veg）各一行，行由 Flyway 预置，页面只换图不增删。
 * 目前只承载「基地介绍页图片」一项；后续追溯页要配的其它内容也往这张表上加列。</p>
 *
 * <p>图片存 ossId 不存 URL：OSS 域名 / 签名会变，URL 由 admin 端与公开端各自解析。
 * 未配置（{@link #baseIntroImageOssId} 为空）时 trace-h5 回落内置版式，不开天窗。</p>
 *
 * @author djs
 * @since V6-R146
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_trace_page_config")
public class TracePageConfig extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 追溯码类型：{@code pork} / {@code veg}，见 {@link TraceCodeTypeConst}。 */
    private String codeType;

    /** 追溯码名称（列表展示用固定名，如「猪肉追溯码」）。 */
    private String configName;

    /** 基地介绍页图片 ossId（{@code sys_oss.oss_id}，单图；空 = 未配置）。 */
    private String baseIntroImageOssId;

    @TableLogic
    private String delFlag;
}
