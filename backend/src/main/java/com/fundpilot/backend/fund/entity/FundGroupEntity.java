package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(name = "fund_group")
@SQLDelete(sql = "UPDATE fund_group SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundGroupEntity extends AbstractEntity {
    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
