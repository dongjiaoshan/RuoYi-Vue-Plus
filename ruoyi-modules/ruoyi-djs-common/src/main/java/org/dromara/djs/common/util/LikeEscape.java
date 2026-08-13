package org.dromara.djs.common.util;

/**
 * LIKE 关键字转义 —— 用户手输的搜索词进 {@code LIKE} 之前必须过一遍这里。
 *
 * <p>不转义时，输入一个 {@code %} 或 {@code _} 就变成通配符：页面表现为「搜什么都返回全部」，
 * 用户以为筛选坏了。{@code _} 更隐蔽——搜「五_肉」会命中「五花肉」，看起来像模糊匹配其实是注入了通配符。</p>
 *
 * <p>MySQL 的 {@code LIKE} <b>默认转义符就是反斜杠</b>，所以转义后不需要再写 {@code ESCAPE} 子句。
 * 前提是连接的 {@code sql_mode} 不含 {@code NO_BACKSLASH_ESCAPES}（本项目 staging / prod 均不含）。</p>
 *
 * <p>放在 djs-common 而不是某个域里，是因为三个域的 applet 列表接口（产品 / 库位 / 门店需求目录）
 * 都要用同一份口径 —— 复制第二份实现必然漂。</p>
 *
 * @author djs
 */
public final class LikeEscape {

    private LikeEscape() {
    }

    /**
     * 转义 LIKE 元字符（{@code \} {@code %} {@code _}）。
     *
     * <p>顺序必须是先 {@code \} 后 {@code %}/{@code _}——反过来会把自己刚插入的反斜杠再转义一次。</p>
     *
     * @param raw 用户手输的关键字；{@code null} 原样返回（调用方一般用 {@code isNotBlank} 守着）
     * @return 可直接拼进 {@code LIKE '%' || x || '%'} 的字面量
     */
    public static String escape(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
