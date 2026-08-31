package org.dromara.djs.plant.demand.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.djs.plant.demand.domain.CropDemand;
import org.dromara.djs.plant.demand.domain.bo.CropDemandBo;
import org.dromara.djs.plant.demand.domain.bo.CropDemandReplyBo;
import org.dromara.djs.plant.demand.domain.query.CropDemandQuery;
import org.dromara.djs.plant.demand.domain.vo.CropDemandVo;
import org.dromara.djs.plant.demand.mapper.CropDemandMapper;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CropDemandServiceImpl} 单测（V6-R152 / V6-R153）。
 *
 * <p>5 case 覆盖：</p>
 * <ol>
 *   <li>insertByBo happy path：需求日期取当天 + 状态强制 pending + 回复三字段留空</li>
 *   <li>reply happy path：写回复内容 / 时间 / 人 + 状态置 replied</li>
 *   <li>reply 已回复再调（改回复）：状态仍 replied，内容被覆盖</li>
 *   <li>deleteWithValidByIds 非创建人 → 抛 ServiceException 且不软删</li>
 *   <li>queryPageList：分页透传 + 查询条件不炸</li>
 * </ol>
 *
 * <p>无 Spring 上下文，{@code toEntity} 由 {@link TestableCropDemandServiceImpl} override
 * 手工映射，避开 MapstructUtils 的 Spring 依赖。{@code currentUserIdSafe()} 在无登录上下文下返 0。</p>
 *
 * @author djs
 * @since V6-R152
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CropDemandServiceImpl 单元测试")
class CropDemandServiceImplTest {

    @Mock
    private CropDemandMapper cropDemandMapper;

    private TestableCropDemandServiceImpl service;

    static class TestableCropDemandServiceImpl extends CropDemandServiceImpl {
        TestableCropDemandServiceImpl(CropDemandMapper baseMapper) {
            super(baseMapper);
        }

        @Override
        protected CropDemand toEntity(CropDemandBo bo) {
            if (bo == null) {
                return null;
            }
            CropDemand e = new CropDemand();
            e.setDemandCategory(bo.getDemandCategory());
            e.setDemandContent(bo.getDemandContent());
            e.setImageOssIds(bo.getImageOssIds());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableCropDemandServiceImpl(cropDemandMapper);
    }

    private CropDemandBo sampleBo() {
        CropDemandBo bo = new CropDemandBo();
        bo.setDemandCategory("new_crop");
        bo.setDemandContent("希望增加羽衣甘蓝的种植");
        bo.setImageOssIds("oss-1,oss-2");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → demandDate=当天 + status=pending + 回复字段留空")
    void testInsertByBo_HappyPath() {
        when(cropDemandMapper.insert(any(CropDemand.class))).thenReturn(1);

        int rows = service.insertByBo(sampleBo());

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<CropDemand> captor = ArgumentCaptor.forClass(CropDemand.class);
        verify(cropDemandMapper).insert(captor.capture());
        CropDemand saved = captor.getValue();
        assertThat(saved.getDemandDate()).as("需求日期由服务端取当天").isEqualTo(LocalDate.now());
        assertThat(saved.getDemandStatus()).as("新建默认待回复").isEqualTo(CropDemandServiceImpl.STATUS_PENDING);
        assertThat(saved.getDemandCategory()).isEqualTo("new_crop");
        assertThat(saved.getImageOssIds()).isEqualTo("oss-1,oss-2");
        assertThat(saved.getReplyContent()).isNull();
        assertThat(saved.getReplyTime()).isNull();
        assertThat(saved.getReplyBy()).isNull();
    }

    @Test
    @DisplayName("reply: 待回复 → 写回复内容 / 时间 / 人 + 状态置 replied")
    void testReply_HappyPath() {
        CropDemand exists = new CropDemand();
        exists.setId(90001L);
        exists.setDemandStatus(CropDemandServiceImpl.STATUS_PENDING);
        when(cropDemandMapper.selectById(90001L)).thenReturn(exists);
        when(cropDemandMapper.updateById(any(CropDemand.class))).thenReturn(1);

        CropDemandReplyBo bo = new CropDemandReplyBo();
        bo.setId(90001L);
        bo.setReplyContent("下季安排试种 2 亩");

        int rows = service.reply(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<CropDemand> captor = ArgumentCaptor.forClass(CropDemand.class);
        verify(cropDemandMapper).updateById(captor.capture());
        CropDemand updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(90001L);
        assertThat(updated.getReplyContent()).isEqualTo("下季安排试种 2 亩");
        assertThat(updated.getReplyTime()).isNotNull();
        assertThat(updated.getReplyBy()).isNotNull();
        assertThat(updated.getDemandStatus()).isEqualTo(CropDemandServiceImpl.STATUS_REPLIED);
    }

    @Test
    @DisplayName("reply: 已回复再调（修改回复）→ 内容覆盖，状态仍 replied")
    void testReply_UpdateExistingReply() {
        CropDemand exists = new CropDemand();
        exists.setId(90002L);
        exists.setDemandStatus(CropDemandServiceImpl.STATUS_REPLIED);
        exists.setReplyContent("旧回复");
        when(cropDemandMapper.selectById(90002L)).thenReturn(exists);
        when(cropDemandMapper.updateById(any(CropDemand.class))).thenReturn(1);

        CropDemandReplyBo bo = new CropDemandReplyBo();
        bo.setId(90002L);
        bo.setReplyContent("新回复");

        assertThat(service.reply(bo)).isEqualTo(1);

        ArgumentCaptor<CropDemand> captor = ArgumentCaptor.forClass(CropDemand.class);
        verify(cropDemandMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReplyContent()).isEqualTo("新回复");
        assertThat(captor.getValue().getDemandStatus()).isEqualTo(CropDemandServiceImpl.STATUS_REPLIED);
    }

    @Test
    @DisplayName("deleteWithValidByIds: 非创建人 → 抛 ServiceException 且不执行软删")
    void testDelete_NotOwner() {
        CropDemand exists = new CropDemand();
        exists.setId(90003L);
        // 无登录上下文时 currentUserIdSafe() 返 0，创建人写成 777 即「不是本人」
        exists.setCreateBy(777L);
        when(cropDemandMapper.selectById(90003L)).thenReturn(exists);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(90003L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("plant.demand.delete.not_owner");

        verify(cropDemandMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 记录不存在 → 抛 ServiceException")
    void testDelete_NotExist() {
        when(cropDemandMapper.selectById(90004L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(90004L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("plant.demand.not_exist");

        verify(cropDemandMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("queryPageList: 带全部搜索条件 → 分页结果透传")
    void testQueryPageList() {
        CropDemandVo vo = new CropDemandVo();
        vo.setId(90005L);
        vo.setDemandStatus(CropDemandServiceImpl.STATUS_PENDING);
        Page<CropDemandVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(cropDemandMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        CropDemandQuery query = new CropDemandQuery();
        query.setDemandContent("羽衣");
        query.setDemandCategory("new_crop");
        query.setDemandStatus(CropDemandServiceImpl.STATUS_PENDING);
        query.setBeginDate(LocalDate.now().minusDays(7));
        query.setEndDate(LocalDate.now());

        var result = service.queryPageList(query, new PageQuery(1, 10));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getId()).isEqualTo(90005L);
    }
}
