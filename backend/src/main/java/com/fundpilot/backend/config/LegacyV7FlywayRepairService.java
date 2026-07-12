package com.fundpilot.backend.config;

import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.RepairOutput;
import org.flywaydb.core.api.output.RepairResult;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LegacyV7FlywayRepairService {

    static final String LEGACY_VERSION = "7";
    static final String LEGACY_DESCRIPTION = "dca take profit replaces timing";
    static final String LEGACY_SCRIPT = "V7__dca_take_profit_replaces_timing.sql";

    private static final Set<MigrationState> REPAIR_DELETABLE_STATES = Set.of(
            MigrationState.MISSING_SUCCESS,
            MigrationState.MISSING_FAILED,
            MigrationState.FUTURE_SUCCESS,
            MigrationState.FUTURE_FAILED);

    private final LegacyV7FlywayRepairProperties properties;

    public void migrate(Flyway flyway) {
        if (!properties.enabled()) {
            flyway.migrate();
            return;
        }

        MigrationInfo[] migrations = flyway.info().all();
        verifyNoFailedOrMismatchedMigrations(migrations);
        List<MigrationInfo> deletable = findDeletableMigrations(migrations);
        if (deletable.isEmpty()) {
            flyway.migrate();
            flyway.validate();
            return;
        }
        verifyRepairPreconditions(deletable);
        RepairResult repairResult = flyway.repair();
        verifyRepairResult(repairResult);
        verifyRepairedHistory(flyway.info().all());
        flyway.migrate();
        flyway.validate();
    }

    private List<MigrationInfo> findDeletableMigrations(MigrationInfo[] migrations) {
        return Arrays.stream(migrations)
                .filter(info -> REPAIR_DELETABLE_STATES.contains(info.getState()))
                .toList();
    }

    private void verifyRepairPreconditions(List<MigrationInfo> deletable) {
        if (deletable.size() != 1 || !isExpectedLegacyMigration(deletable.getFirst())) {
            throw new IllegalStateException(
                    "Flyway repair 已拒绝：仅允许唯一缺失的历史 V7 迁移 " + LEGACY_SCRIPT);
        }
    }

    private void verifyNoFailedOrMismatchedMigrations(MigrationInfo[] migrations) {
        Arrays.stream(migrations)
                .filter(info -> info.getState().isFailed())
                .findAny()
                .ifPresent(info -> {
                    throw new IllegalStateException("Flyway repair 已拒绝：存在失败迁移 " + migrationLabel(info));
                });

        Arrays.stream(migrations)
                .filter(this::hasMetadataMismatch)
                .findAny()
                .ifPresent(info -> {
                    throw new IllegalStateException("Flyway repair 已拒绝：迁移元数据不匹配 " + migrationLabel(info));
                });
    }

    private boolean isExpectedLegacyMigration(MigrationInfo info) {
        return info.getState() == MigrationState.MISSING_SUCCESS
                && info.getVersion() != null
                && LEGACY_VERSION.equals(info.getVersion().toString())
                && LEGACY_DESCRIPTION.equals(info.getDescription())
                && LEGACY_SCRIPT.equals(info.getScript());
    }

    private boolean hasMetadataMismatch(MigrationInfo info) {
        if (REPAIR_DELETABLE_STATES.contains(info.getState())) {
            return false;
        }
        boolean checksumMismatch = info.getAppliedChecksum() != null
                && info.getResolvedChecksum() != null
                && !Objects.equals(info.getAppliedChecksum(), info.getResolvedChecksum());
        boolean descriptionMismatch = info.getAppliedDescription() != null
                && info.getResolvedDescription() != null
                && !Objects.equals(info.getAppliedDescription(), info.getResolvedDescription());
        boolean typeMismatch = info.getAppliedType() != null
                && info.getResolvedType() != null
                && !Objects.equals(info.getAppliedType(), info.getResolvedType());
        return checksumMismatch || descriptionMismatch || typeMismatch;
    }

    private void verifyRepairResult(RepairResult result) {
        List<RepairOutput> removed = safeList(result.migrationsRemoved);
        List<RepairOutput> aligned = safeList(result.migrationsAligned);
        List<RepairOutput> deleted = safeList(result.migrationsDeleted);

        if (!removed.isEmpty() || !aligned.isEmpty() || deleted.size() != 1
                || !isExpectedDeletedMigration(deleted.getFirst())) {
            throw new IllegalStateException(
                    "Flyway repair 结果超出允许范围：必须仅删除历史 V7，且不得移除失败记录或对齐元数据");
        }
    }

    private boolean isExpectedDeletedMigration(RepairOutput output) {
        return LEGACY_VERSION.equals(output.version)
                && LEGACY_DESCRIPTION.equals(output.description);
    }

    private void verifyRepairedHistory(MigrationInfo[] migrations) {
        List<MigrationInfo> legacyV7 = Arrays.stream(migrations)
                .filter(info -> info.getVersion() != null
                        && LEGACY_VERSION.equals(info.getVersion().toString()))
                .toList();
        if (legacyV7.size() != 1
                || legacyV7.getFirst().getState() != MigrationState.DELETED
                || !LEGACY_DESCRIPTION.equals(legacyV7.getFirst().getDescription())
                || !LEGACY_SCRIPT.equals(legacyV7.getFirst().getScript())) {
            throw new IllegalStateException("Flyway repair 后历史状态异常：V7 必须精确标记为 DELETED");
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String migrationLabel(MigrationInfo info) {
        return Objects.toString(info.getVersion(), "repeatable") + " (" + info.getScript() + ")";
    }
}
