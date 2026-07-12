package com.fundpilot.backend.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.RepairOutput;
import org.flywaydb.core.api.output.RepairResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyV7FlywayRepairServiceTest {

    @Mock
    Flyway flyway;

    @Mock
    MigrationInfoService migrationInfoService;

    @Test
    void disabledByDefaultMigratesWithoutInspectingOrRepairing() {
        LegacyV7FlywayRepairService service = service(false);

        service.migrate(flyway);

        verify(flyway).migrate();
        verify(flyway, never()).repair();
        verifyNoInteractions(migrationInfoService);
    }

    @Test
    void repairsOnlyExpectedLegacyV7ThenValidatesAndMigrates() {
        LegacyV7FlywayRepairService service = service(true);
        MigrationInfo legacyV7 = migration(MigrationState.MISSING_SUCCESS, "7",
                LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                LegacyV7FlywayRepairService.LEGACY_SCRIPT);
        MigrationInfo deletedV7 = migration(MigrationState.DELETED, "7",
                LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                LegacyV7FlywayRepairService.LEGACY_SCRIPT);
        RepairResult repairResult = repairResult(
                List.of(new RepairOutput("7", LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                        LegacyV7FlywayRepairService.LEGACY_SCRIPT)),
                List.of(), List.of());
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(
                new MigrationInfo[]{legacyV7},
                new MigrationInfo[]{deletedV7});
        when(flyway.repair()).thenReturn(repairResult);

        service.migrate(flyway);

        InOrder order = inOrder(flyway);
        order.verify(flyway).info();
        order.verify(flyway).repair();
        order.verify(flyway).info();
        order.verify(flyway).migrate();
        order.verify(flyway).validate();
    }

    @Test
    void rejectsAnyAdditionalMissingMigration() {
        LegacyV7FlywayRepairService service = service(true);
        MigrationInfo legacyV7 = migration(MigrationState.MISSING_SUCCESS, "7",
                LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                LegacyV7FlywayRepairService.LEGACY_SCRIPT);
        MigrationInfo unexpectedV8 = migration(MigrationState.MISSING_SUCCESS, "8", "unexpected",
                "V8__unexpected.sql");
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{legacyV7, unexpectedV8});

        assertThatThrownBy(() -> service.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅允许唯一缺失");

        verify(flyway, never()).repair();
        verify(flyway, never()).migrate();
    }

    @Test
    void enabledRepairIsIdempotentWhenLegacyV7IsAlreadyResolved() {
        LegacyV7FlywayRepairService service = service(true);
        MigrationInfo appliedV8 = migration(MigrationState.SUCCESS, "8", "watched indices",
                "V8__add_watched_indices_to_user_config.sql");
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{appliedV8});

        service.migrate(flyway);

        verify(flyway, never()).repair();
        InOrder order = inOrder(flyway);
        order.verify(flyway).info();
        order.verify(flyway).migrate();
        order.verify(flyway).validate();
    }

    @Test
    void migratesPendingVersionBeforeStrictValidation() {
        LegacyV7FlywayRepairService service = service(true);
        MigrationInfo pendingV18 = migration(MigrationState.PENDING, "18", "add signal ignore date",
                "V18__add_signal_ignore_date.sql");
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{pendingV18});

        service.migrate(flyway);

        verify(flyway, never()).repair();
        InOrder order = inOrder(flyway);
        order.verify(flyway).info();
        order.verify(flyway).migrate();
        order.verify(flyway).validate();
    }

    @Test
    void rejectsFailedLegacyMigration() {
        LegacyV7FlywayRepairService service = service(true);
        MigrationInfo failedV7 = migration(MigrationState.MISSING_FAILED, "7",
                LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                LegacyV7FlywayRepairService.LEGACY_SCRIPT);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{failedV7});

        assertThatThrownBy(() -> service.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("存在失败迁移");

        verify(flyway, never()).repair();
    }

    @Test
    void rejectsMetadataMismatchOnAnyResolvedMigration() {
        LegacyV7FlywayRepairService service = service(true);
        MigrationInfo legacyV7 = migration(MigrationState.MISSING_SUCCESS, "7",
                LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                LegacyV7FlywayRepairService.LEGACY_SCRIPT);
        MigrationInfo checksumMismatch = migration(MigrationState.SUCCESS, "8", "watched indices",
                "V8__add_watched_indices_to_user_config.sql");
        when(checksumMismatch.getAppliedChecksum()).thenReturn(10);
        when(checksumMismatch.getResolvedChecksum()).thenReturn(20);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{legacyV7, checksumMismatch});

        assertThatThrownBy(() -> service.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("元数据不匹配");

        verify(flyway, never()).repair();
    }

    @Test
    void rejectsRepairThatRemovesFailedMigration() {
        LegacyV7FlywayRepairService service = service(true);
        prepareExpectedLegacyV7();
        when(flyway.repair()).thenReturn(repairResult(
                List.of(new RepairOutput("7", LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                        LegacyV7FlywayRepairService.LEGACY_SCRIPT)),
                List.of(new RepairOutput("6", "failed", "V6__failed.sql")),
                List.of()));

        assertThatThrownBy(() -> service.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不得移除失败记录");

        verify(flyway, never()).validate();
        verify(flyway, never()).migrate();
    }

    @Test
    void rejectsRepairThatAlignsMetadata() {
        LegacyV7FlywayRepairService service = service(true);
        prepareExpectedLegacyV7();
        when(flyway.repair()).thenReturn(repairResult(
                List.of(new RepairOutput("7", LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                        LegacyV7FlywayRepairService.LEGACY_SCRIPT)),
                List.of(),
                List.of(new RepairOutput("8", "changed", "V8__changed.sql"))));

        assertThatThrownBy(() -> service.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不得移除失败记录或对齐元数据");

        verify(flyway, never()).migrate();
    }

    @Test
    void rejectsRepairThatDeletesAnyOtherMigration() {
        LegacyV7FlywayRepairService service = service(true);
        prepareExpectedLegacyV7();
        when(flyway.repair()).thenReturn(repairResult(
                List.of(new RepairOutput("8", "unexpected", "V8__unexpected.sql")),
                List.of(), List.of()));

        assertThatThrownBy(() -> service.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须仅删除历史 V7");

        verify(flyway, never()).migrate();
    }

    private void prepareExpectedLegacyV7() {
        MigrationInfo legacyV7 = migration(MigrationState.MISSING_SUCCESS, "7",
                LegacyV7FlywayRepairService.LEGACY_DESCRIPTION,
                LegacyV7FlywayRepairService.LEGACY_SCRIPT);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.all()).thenReturn(new MigrationInfo[]{legacyV7});
    }

    private static LegacyV7FlywayRepairService service(boolean enabled) {
        return new LegacyV7FlywayRepairService(new LegacyV7FlywayRepairProperties(enabled));
    }

    private static MigrationInfo migration(MigrationState state, String version, String description, String script) {
        MigrationInfo info = org.mockito.Mockito.mock(MigrationInfo.class);
        lenient().when(info.getState()).thenReturn(state);
        lenient().when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        lenient().when(info.getDescription()).thenReturn(description);
        lenient().when(info.getScript()).thenReturn(script);
        lenient().when(info.isChecksumMatching()).thenReturn(true);
        lenient().when(info.isDescriptionMatching()).thenReturn(true);
        lenient().when(info.isTypeMatching()).thenReturn(true);
        return info;
    }

    private static RepairResult repairResult(List<RepairOutput> deleted, List<RepairOutput> removed,
                                             List<RepairOutput> aligned) {
        RepairResult result = new RepairResult("11.14.1", "fundpilot");
        result.migrationsDeleted = deleted;
        result.migrationsRemoved = removed;
        result.migrationsAligned = aligned;
        return result;
    }
}
