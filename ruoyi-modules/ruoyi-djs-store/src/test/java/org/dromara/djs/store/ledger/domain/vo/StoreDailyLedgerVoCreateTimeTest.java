package org.dromara.djs.store.ledger.domain.vo;

import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.domain.StoreDailyLedgerToStoreDailyLedgerVoMapperImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * createTime 映射不能偏移：MapStruct 把 Date 转 LocalDateTime 时固定按 UTC，东八区会少 8 小时，VO 必须用 Date。
 */
class StoreDailyLedgerVoCreateTimeTest {

    @Test
    @Tag("dev")
    void createTimeKeepsTheSameInstant() {
        Date created = new Date(1793417570000L);
        StoreDailyLedger entity = new StoreDailyLedger();
        entity.setCreateTime(created);

        StoreDailyLedgerVo vo = new StoreDailyLedgerToStoreDailyLedgerVoMapperImpl().convert(entity);

        assertThat(vo.getCreateTime()).isEqualTo(created);
    }
}
