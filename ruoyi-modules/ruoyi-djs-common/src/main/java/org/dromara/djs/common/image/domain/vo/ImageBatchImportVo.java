package org.dromara.djs.common.image.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 批量导入结果 VO（IMG-LIB-001 批量上传 + 按文件名自动归档）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
public class ImageBatchImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 新建条数（image_name 此前不存在）。
     */
    private int imported;

    /**
     * 更新条数（image_name 已存在 → 替换 ossId）。
     */
    private int updated;

    /**
     * 各域重新匹配更新条数（domainName → count，如 {@code crop} / {@code product}）。
     * 上游模块未上线时为空 Map。
     */
    private Map<String, Integer> rematched = new LinkedHashMap<>();

}
