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
     * <p>每次实时聚合计算，<b>不做 djs 层缓存</b>：底层 ruoyi {@code CacheNames.SYS_DICT}
     * 已随 admin 改字典即时失效，故 hash 始终反映最新字典；小程序据此比对本地版本，
     * 改完字典即可在下次同步时拉到最新。</p>
     *
     * @return SHA-256 hex
     */
    String currentVersion();

    /**
     * 直接返完整 {@link DjsDictFullVo}（{@link #queryAllDjsTypes()} + {@link #currentVersion()} 的合体）。
     *
     * <p>每次实时聚合，不做 djs 层缓存（理由同 {@link #currentVersion()}）。</p>
     */
    DjsDictFullVo queryFull();
}
