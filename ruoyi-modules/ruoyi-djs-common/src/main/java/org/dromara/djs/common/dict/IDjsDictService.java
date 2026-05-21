package org.dromara.djs.common.dict;

import org.dromara.system.domain.vo.SysDictDataVo;

import java.util.List;
import java.util.Map;

/**
 * djs 业务字典聚合查询服务（SYS-INFRA-005）。
 *
 * <p>对外只暴露"全量 djs_ 字典"的聚合视图 + 版本 hash，业务模块仍然走 ruoyi 自带
 * {@code ISysDictTypeService.selectDictDataByType(dictType)} 单类拉取（命中
 * {@code CacheNames.SYS_DICT} cache），本服务不重写 ruoyi 字典逻辑。</p>
 *
 * @author djs
 * @since SYS-INFRA-005
 */
public interface IDjsDictService {

    /**
     * 拉取所有 {@code djs_} 前缀字典的全量数据（按 dict_type 聚合）。
     *
     * <p>底层逐个走 ruoyi 自带 {@code ISysDictTypeService.selectDictDataByType}，
     * 故每个 dictType 命中 ruoyi cache（{@code CacheNames.SYS_DICT}），不重复读库。
     * 排序：dict_type 字典序升序 + 每个 dict_type 内部按 dict_sort 升序（ruoyi 自带）。</p>
     *
     * @return 不可变 Map，key 为 dict_type，value 为字典项列表（按 dict_sort 升序）
     */
    Map<String, List<SysDictDataVo>> queryAllDjsTypes();

    /**
     * 当前 djs_ 字典全集的 SHA-256 hash（64 字符小写 hex）。
     *
     * <p>缓存到 Redis key {@code djs:dict:version:{farmId}} TTL 1h；admin 改字典时由
     * {@code resetDictCache()} 钩子或调用方主动 evict（V1 单农场可允许 1h 延迟，
     * V2 多农场 + 字典改动需主动 evict）。</p>
     *
     * @return SHA-256 hex
     */
    String currentVersion();

    /**
     * 直接返完整 {@link DjsDictFullVo}（{@link #queryAllDjsTypes()} + {@link #currentVersion()} 的合体）。
     *
     * <p>命中 {@code djs:dict:full:{farmId}} 整体缓存（TTL 1h）直接返；未命中则重算 + 写缓存 + 返。</p>
     */
    DjsDictFullVo queryFull();

    /**
     * 主动失效字典版本与全量缓存（admin 改字典后调用，V1 暂不强制；
     * 也供集成测试清理 redis 状态用）。
     */
    void evictCache();
}
