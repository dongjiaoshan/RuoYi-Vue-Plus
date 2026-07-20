package org.dromara.djs.breed.farm.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.farm.domain.FarmInfo;
import org.dromara.djs.breed.farm.domain.bo.FarmContactBo;
import org.dromara.djs.breed.farm.domain.vo.FarmInfoVo;
import org.dromara.djs.breed.farm.mapper.FarmInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FarmInfoServiceImpl} 单元测试（BRD-MD-002）。
 *
 * <p>覆盖：queryCurrent 查 id=1001、updateContact happy / id=null / id != 1001 / DB 缺行
 * 四条分支；并断言 updateById 不传 farm_code / farm_name / farm_status
 * （MP {@code FieldStrategy.NOT_NULL} 自动从 UPDATE 剥离）。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FarmInfoServiceImpl 单元测试")
class FarmInfoServiceImplTest {

    @Mock
    private FarmInfoMapper farmInfoMapper;

    private FarmInfoServiceImpl service;

    @BeforeEach
    void setup() {
        service = new FarmInfoServiceImpl(farmInfoMapper);
    }

    @Test
    @DisplayName("queryCurrent: 走 mapper.selectVoById(1001L) 返回 VO")
    void testQueryCurrent() {
        FarmInfoVo vo = new FarmInfoVo();
        vo.setId(1001L);
        vo.setFarmCode("DJS-MAIN");
        vo.setFarmName("东角山主场");
        when(farmInfoMapper.selectVoById(1001L)).thenReturn(vo);

        FarmInfoVo got = service.queryCurrent();
        assertThat(got).isNotNull();
        assertThat(got.getId()).isEqualTo(1001L);
        assertThat(got.getFarmCode()).isEqualTo("DJS-MAIN");
    }

    @Test
    @DisplayName("updateContact: happy → 只更新 contact 三字段，不带 farm_code/farm_name/farm_status")
    void testUpdateContact_HappyPath() {
        FarmInfo existing = new FarmInfo();
        existing.setId(1001L);
        existing.setFarmCode("DJS-MAIN");
        existing.setFarmName("东角山主场");
        existing.setFarmStatus(1);
        when(farmInfoMapper.selectById(1001L)).thenReturn(existing);
        when(farmInfoMapper.updateById(any(FarmInfo.class))).thenReturn(1);

        FarmContactBo bo = new FarmContactBo();
        bo.setId(1001L);
        bo.setContactName("王经理");
        bo.setContactPhone("13800138001");
        bo.setAddress("上海市浦东新区张江高科园区A栋101");

        int rows = service.updateContact(bo);
        assertThat(rows).isEqualTo(1);

        ArgumentCaptor<FarmInfo> captor = ArgumentCaptor.forClass(FarmInfo.class);
        verify(farmInfoMapper).updateById(captor.capture());
        FarmInfo saved = captor.getValue();
        // contact 三字段填进去
        assertThat(saved.getId()).isEqualTo(1001L);
        assertThat(saved.getContactName()).isEqualTo("王经理");
        assertThat(saved.getContactPhone()).isEqualTo("13800138001");
        assertThat(saved.getAddress()).isEqualTo("上海市浦东新区张江高科园区A栋101");
        // 其他字段保持 null → MP FieldStrategy.NOT_NULL 自动从 UPDATE SET 剥离
        assertThat(saved.getFarmCode()).isNull();
        assertThat(saved.getFarmName()).isNull();
        assertThat(saved.getFarmStatus()).isNull();
    }

    @Test
    @DisplayName("updateContact: id=null → 抛 ServiceException")
    void testUpdateContact_NullId() {
        FarmContactBo bo = new FarmContactBo();
        bo.setId(null);
        bo.setContactName("X");
        assertThatThrownBy(() -> service.updateContact(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("农场 ID 不能为空");
    }

    @Test
    @DisplayName("updateContact: id != 1001 → 抛 ServiceException（V1 ADR-0001）")
    void testUpdateContact_NonDefaultFarmId() {
        FarmContactBo bo = new FarmContactBo();
        bo.setId(2001L);
        assertThatThrownBy(() -> service.updateContact(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("V1 仅允许编辑默认农场");
    }

    @Test
    @DisplayName("updateContact: sys_farm 缺 1001 行 → 抛 ServiceException")
    void testUpdateContact_RowMissing() {
        when(farmInfoMapper.selectById(1001L)).thenReturn(null);

        FarmContactBo bo = new FarmContactBo();
        bo.setId(1001L);
        assertThatThrownBy(() -> service.updateContact(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("农场数据未初始化");
    }
}
