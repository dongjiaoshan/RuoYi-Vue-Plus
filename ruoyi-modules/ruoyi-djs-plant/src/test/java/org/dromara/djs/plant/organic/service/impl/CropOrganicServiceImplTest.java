package org.dromara.djs.plant.organic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.organic.domain.CropOrganic;
import org.dromara.djs.plant.organic.domain.OrganicCropno;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicRelExportVo;
import org.dromara.djs.plant.organic.mapper.CropOrganicMapper;
import org.dromara.djs.plant.organic.mapper.OrganicCropnoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CropOrganicServiceImpl} 关联作物导出单测（row148）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy：3 条关联 → 3 行，每行带证书编号，按作物编号升序</li>
 *   <li>证书 id 为空 / 证书不存在 → ServiceException</li>
 *   <li>无关联作物 → 空列表，且不再查作物表</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CropOrganicServiceImpl 关联作物导出单元测试")
class CropOrganicServiceImplTest {

    private static final String CERT_NO = "134OP1200306";

    @Mock
    private CropOrganicMapper cropOrganicMapper;

    @Mock
    private CropInfoMapper cropInfoMapper;

    @Mock
    private OrganicCropnoMapper cropnoMapper;

    private CropOrganicServiceImpl service;

    @BeforeEach
    void setup() {
        service = new CropOrganicServiceImpl(cropOrganicMapper, cropInfoMapper, cropnoMapper);
    }

    private OrganicCropno rel(Long cropId) {
        OrganicCropno r = new OrganicCropno();
        r.setOrganicId(90001L);
        r.setCropId(cropId);
        return r;
    }

    private CropInfo crop(Long id, String name, String code) {
        CropInfo c = new CropInfo();
        c.setId(id);
        c.setCropName(name);
        c.setCropCode(code);
        return c;
    }

    @Test
    @DisplayName("queryRelatedCropsForExport happy：3 行 + 证书编号回填 + 按作物编号升序")
    void testExportHappyPath() {
        CropOrganic cert = new CropOrganic();
        cert.setId(90001L);
        cert.setCropCertNo(CERT_NO);
        when(cropOrganicMapper.selectById(90001L)).thenReturn(cert);

        when(cropnoMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(rel(80002L), rel(80001L), rel(80044L)));
        when(cropInfoMapper.selectByIds(any(Collection.class)))
            .thenReturn(List.of(crop(80002L, "玉米", "C002"),
                crop(80001L, "上海青", "C001"),
                crop(80044L, "丝瓜", "C044")));

        List<CropOrganicRelExportVo> rows = service.queryRelatedCropsForExport(90001L);

        assertThat(rows).hasSize(3);
        assertThat(rows).allMatch(r -> CERT_NO.equals(r.getCropCertNo()));
        assertThat(rows).extracting(CropOrganicRelExportVo::getCropCode).containsExactly("C001", "C002", "C044");
        assertThat(rows).extracting(CropOrganicRelExportVo::getCropName).containsExactly("上海青", "玉米", "丝瓜");
    }

    @Test
    @DisplayName("queryRelatedCropsForExport：id 为空 / 证书不存在 → ServiceException")
    void testExportCertMissing() {
        assertThatThrownBy(() -> service.queryRelatedCropsForExport(null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");

        when(cropOrganicMapper.selectById(90002L)).thenReturn(null);
        assertThatThrownBy(() -> service.queryRelatedCropsForExport(90002L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在或已删除");
    }

    @Test
    @DisplayName("queryRelatedCropsForExport：无关联作物 → 空列表且不查作物表")
    void testExportNoRelations() {
        CropOrganic cert = new CropOrganic();
        cert.setId(90001L);
        cert.setCropCertNo(CERT_NO);
        when(cropOrganicMapper.selectById(90001L)).thenReturn(cert);
        when(cropnoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(service.queryRelatedCropsForExport(90001L)).isEmpty();
        verify(cropInfoMapper, never()).selectByIds(any(Collection.class));
    }
}
