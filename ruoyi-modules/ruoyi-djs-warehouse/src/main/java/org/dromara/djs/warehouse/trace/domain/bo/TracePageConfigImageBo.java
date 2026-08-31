package org.dromara.djs.warehouse.trace.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 追溯码配置「上传基地介绍图」入参（V6-R146）。
 *
 * @author djs
 * @since V6-R146
 */
@Data
public class TracePageConfigImageBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置行主键（两行由迁移预置，前端从列表带过来）。 */
    @NotNull(message = "{trace.pageConfig.id_required}")
    private Long id;

    /**
     * 基地介绍页图片 ossId（雪花 string，单图）。
     *
     * <p>允许为空 —— 甲方传错图需要一条撤回路径：清空后 trace-h5 回落内置版式，不是死页面。</p>
     */
    @Size(max = 32, message = "{trace.pageConfig.oss_id_too_long}")
    private String baseIntroImageOssId;
}
