package org.dromara.djs.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DjsRedisKey} 模板渲染 happy path 校验。
 *
 * <p>核心约束：所有 key 含 {farmId} 段，前缀统一 {@code djs:}（与 doc/05 §6.4 一致）。
 *
 * @author djs
 * @since SYS-INFRA-007
 */
@Tag("local")
@Tag("dev")
class DjsRedisKeyTest {

    @Test
    @DisplayName("耳号简版序列 key 格式：djs:breed:ear_tag_seq:<farmId>:<yyMMdd>")
    void earTagSeqKeyFormat() {
        String key = DjsRedisKey.EAR_TAG_SEQ.formatted("1001", "260403");
        assertThat(key).isEqualTo("djs:breed:ear_tag_seq:1001:260403");
    }

    @Test
    @DisplayName("出入库流水号序列 key 格式：djs:wms:flow_no_seq:<farmId>:<yyyyMMdd>")
    void flowNoSeqKeyFormat() {
        String key = DjsRedisKey.FLOW_NO_SEQ.formatted("1001", "20260403");
        assertThat(key).isEqualTo("djs:wms:flow_no_seq:1001:20260403");
    }

    @Test
    @DisplayName("加工生产编码序列：djs:wms:produce_no_seq:<farmId>:<yyMMdd>")
    void produceNoSeqKeyFormat() {
        String key = DjsRedisKey.PRODUCE_NO_SEQ.formatted("1001", "260312");
        assertThat(key).isEqualTo("djs:wms:produce_no_seq:1001:260312");
    }

    @Test
    @DisplayName("供应商 / 会员编码 单 farmId 段")
    void supplierAndMemberKeys() {
        assertThat(DjsRedisKey.SUPPLIER_NO_SEQ.formatted("1001")).isEqualTo("djs:md:supplier_no_seq:1001");
        assertThat(DjsRedisKey.MEMBER_NO_SEQ.formatted("1001")).isEqualTo("djs:str:member_no_seq:1001");
    }

    @Test
    @DisplayName("字典版本 key 含 farmId 段")
    void dictVersionKey() {
        assertThat(DjsRedisKey.DICT_VERSION.formatted("1001")).isEqualTo("djs:dict:version:1001");
    }

    @Test
    @DisplayName("所有 key 模板都以 djs: 全局前缀开头")
    void allKeysHaveDjsPrefix() {
        assertThat(DjsRedisKey.EAR_TAG_SEQ).startsWith("djs:");
        assertThat(DjsRedisKey.FLOW_NO_SEQ).startsWith("djs:");
        assertThat(DjsRedisKey.PRODUCE_NO_SEQ).startsWith("djs:");
        assertThat(DjsRedisKey.SUPPLIER_NO_SEQ).startsWith("djs:");
        assertThat(DjsRedisKey.MEMBER_NO_SEQ).startsWith("djs:");
        assertThat(DjsRedisKey.DICT_VERSION).startsWith("djs:");
    }

    @Test
    @DisplayName("所有跨农场 key 模板都含 farmId 占位符（%s）")
    void allKeysContainFarmIdPlaceholder() {
        assertThat(DjsRedisKey.EAR_TAG_SEQ).contains("%s");
        assertThat(DjsRedisKey.FLOW_NO_SEQ).contains("%s");
        assertThat(DjsRedisKey.PRODUCE_NO_SEQ).contains("%s");
        assertThat(DjsRedisKey.SUPPLIER_NO_SEQ).contains("%s");
        assertThat(DjsRedisKey.MEMBER_NO_SEQ).contains("%s");
        assertThat(DjsRedisKey.DICT_VERSION).contains("%s");
    }
}
