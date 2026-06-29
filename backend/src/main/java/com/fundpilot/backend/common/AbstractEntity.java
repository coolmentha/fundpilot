package com.fundpilot.backend.common;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * 所有实体的公共基类。
 * <p>
 * <b>软删除机制</b>：用 {@link SQLRestriction}({@code "deleted_date IS NULL"}) 让 Hibernate
 * 自动给所有查询追加 {@code WHERE deleted_date IS NULL} 过滤,软删记录天然不出现在默认查询里;
 * 软删的 UPDATE 语句由各具体实体的 {@code @SQLDelete} 提供(见子类)。
 * <p>
 * <b>为什么 @SQLDelete 不放本父类</b>:本类是 {@code @MappedSuperclass},Hibernate 不把
 * {@code @SQLDelete} 从 {@code @MappedSuperclass} 继承到具体 {@code @Entity}(只继承 {@code @SQLRestriction}
 * 等查询过滤)。放父类会静默失效——{@code em.remove()} 照发硬 DELETE,软删从不生效。故每个具体实体
 * 各自声明 {@code @SQLDelete(sql="UPDATE <表名> SET deleted_date=now() WHERE id=? AND version=?")},
 * 字面表名(非 {@code {h-entity}} 占位符,Hibernate 7 不认),id+version 两参数(版本化实体必需)。
 * 这样既保留了 PRD §0「归档=软删除,与持仓状态正交」的语义,
 * 又不触发 Hibernate 7 那条「@SoftDelete 实体不能被 LAZY 关联」的限制——关联可照常 LAZY。
 * <p>
 * <b>为什么不用 @SoftDelete</b>:Hibernate 7 在 {@code ToOneAttributeMapping} 元模型构建阶段
 * 硬编码拒绝 {@code @SoftDelete} 实体的 LAZY 关联(抛 UnsupportedMappingException),官方测试
 * {@code SoftDeleteFetchModeTests} 把这个异常当预期行为断言。FundPilot 几乎所有实体都被 LAZY 关联
 * (FundTransaction→Fund、SignalLog→Fund、FundStrategy→Fund…),用 @SoftDelete 等于强制全 EAGER,
 * 仓位聚合等查询会被 N+1 拖垮,生产不可用。{@code @SQLRestriction} 是普通 SQL 过滤,不依赖
 * Hibernate 软删特技,长期稳定。
 * <p>
 * {@code deletedDate} 字段映射到 {@code deleted_date} 列,由各子类的 {@code @SQLDelete} 在删除时写入,
 * Java 侧标 {@code insertable=false, updatable=false} 防止普通 INSERT/UPDATE 误动它。
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
public abstract class AbstractEntity implements Serializable {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;
    @Version
    private Long version;
    @CreatedDate
    @Column(updatable = false)
    private Instant createdDate;
    @LastModifiedDate
    private Instant updatedDate;
    @Column(name = "deleted_date", insertable = false, updatable = false)
    private Instant deletedDate;
}
