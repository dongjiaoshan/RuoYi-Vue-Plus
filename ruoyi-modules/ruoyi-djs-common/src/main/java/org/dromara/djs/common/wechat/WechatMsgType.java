package org.dromara.djs.common.wechat;

/**
 * 微信小程序订阅消息类型枚举（SYS-INFRA-006）。
 *
 * <p>4 类 V1 启用的订阅消息，对应字典 {@code djs_subscribe_message_type}（dict_id=100502）：
 * <ul>
 *   <li>{@link #SLAUGHTER_NOTICE} — 出栏通知（BRD-EVENT-004 触发）</li>
 *   <li>{@link #STOCK_ALERT}      — 库存告警（WMS 库存低于阈值触发）</li>
 *   <li>{@link #SALES_SUMMARY}    — 销售汇总（每日 18:00 定时任务触发）</li>
 *   <li>{@link #INTERNAL_FEEDBACK}— 内测反馈（bug 状态更新触发）</li>
 * </ul>
 *
 * <p>{@link #getCode()} 返回的 code 字符串与字典 dict_value 完全一致，也即
 * {@code application.yml} {@code djs.wechat.miniapp.templates.<code>} 配置项的 key。</p>
 *
 * @author djs
 */
public enum WechatMsgType {

    /** 出栏通知 — 通知录入人出栏单据已审核 / 已售出 */
    SLAUGHTER_NOTICE("slaughter_notice", "出栏通知"),

    /** 库存告警 — 仓库库存低于安全阈值时通知仓管员 */
    STOCK_ALERT("stock_alert", "库存告警"),

    /** 销售汇总 — 每日 18:00 给店长发当日销售汇总 */
    SALES_SUMMARY("sales_summary", "销售汇总"),

    /** 内测反馈 — bug 提交方收到状态更新（已确认 / 已修复） */
    INTERNAL_FEEDBACK("internal_feedback", "内测反馈");

    private final String code;
    private final String label;

    WechatMsgType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** dict_value / yml 配置 key（如 {@code slaughter_notice}）。 */
    public String getCode() {
        return code;
    }

    /** 中文标签（用于日志与告警语句）。 */
    public String getLabel() {
        return label;
    }

    /**
     * 按 code 反查枚举（用于业务调用方传字符串时的容错入口）。
     *
     * @param code 字典 dict_value
     * @return 命中的枚举；未命中返 null
     */
    public static WechatMsgType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (WechatMsgType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
