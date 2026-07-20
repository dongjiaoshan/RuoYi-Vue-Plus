package org.dromara.djs.common.dict;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dromara.system.domain.vo.SysDictDataVo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * djs 业务字典全量 VO（小程序首次同步 / version 不命中重拉时返）。
 *
 * <p>{@code version} 为 {@code data} 内容的 SHA-256 hash，小程序端把它存到 localStorage，
 * 后续启动用 {@code GET /djs/dict/version} 探测，相等就直接读本地缓存不再拉全量。</p>
 *
 * <p>{@code data} 的 key 为 {@code sys_dict_type.dict_type}（如 {@code djs_pig_breed}），
 * value 为 ruoyi 自带 {@link SysDictDataVo} 列表，按 {@code dict_sort} 升序。</p>
 *
 * @author djs
 * @since SYS-INFRA-005
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DjsDictFullVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 全量字典数据 SHA-256 hash（64 字符小写 hex）。
     */
    private String version;

    /**
     * dict_type → 字典项列表。
     */
    private Map<String, List<SysDictDataVo>> data;
}
