package com.dilaw.mohe.approval;

import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.repository.ApprovalLevelRepository;
import com.dilaw.mohe.approval.service.ApprovalConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Live QA H5 (25 Sep 2026): a level update that left {@code requiredRoleId} out
 * cleared it, turning the level into one that names nobody. The screen requires a
 * role on every level, so the stored one is now kept; a new role still replaces it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalLevelUpdateKeepsRoleTest {

    @Mock private ApprovalLevelRepository levelRepository;
    @Mock private com.dilaw.mohe.approval.repository.ApprovalConfigRepository configRepository;
    @Mock private com.dilaw.mohe.ums.repository.UmsUserRepository userRepository;
    @Mock private com.dilaw.mohe.ums.repository.UmsUserCountryRepository userCountryRepository;
    @Mock private com.dilaw.mohe.ums.service.AdminTierService adminTierService;
    @Mock private com.dilaw.mohe.service.codelist.CodeListService codeLists;
    @InjectMocks private ApprovalConfigService service;

    private ApprovalLevel stored() {
        ApprovalLevel l = new ApprovalLevel();
        l.setId(38L);
        l.setLevelName("Level 1");
        l.setRequiredRoleId(7L);
        when(levelRepository.findById(38L)).thenReturn(Optional.of(l));
        when(levelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return l;
    }

    private static ApprovalLevel body(Long roleId) {
        ApprovalLevel b = new ApprovalLevel();
        b.setLevelName("Level 1 (renamed)");
        b.setRequiredRoleId(roleId);
        b.setCanSkip(false);
        b.setIsActive(true);
        return b;
    }

    @Test
    @DisplayName("a body without a role keeps the stored role")
    void missingRoleIsKept() {
        stored();
        ApprovalLevel saved = service.updateLevel(38L, body(null));
        assertThat(saved.getRequiredRoleId()).isEqualTo(7L);
        assertThat(saved.getLevelName()).isEqualTo("Level 1 (renamed)");
    }

    @Test
    @DisplayName("P5-5: a body with a position and no role makes a position-only level")
    void positionWithoutRoleClearsTheRole() {
        stored();
        when(codeLists.rows("position")).thenReturn(java.util.List.of(new com.dilaw.mohe.service.codelist.CodeRow(
                4, "ATTACHE", "الملحق الثقافي", "Cultural attaché", 40, null, null, false, true, null)));
        ApprovalLevel body = body(null);
        body.setRequiredPositionCode("ATTACHE");

        ApprovalLevel saved = service.updateLevel(38L, body);

        assertThat(saved.getRequiredRoleId()).isNull();
        assertThat(saved.getRequiredPositionCode()).isEqualTo("ATTACHE");
    }

    @Test
    @DisplayName("a body with a role replaces it")
    void newRoleReplaces() {
        stored();
        assertThat(service.updateLevel(38L, body(4L)).getRequiredRoleId()).isEqualTo(4L);
    }
}
