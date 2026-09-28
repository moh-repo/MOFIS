package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Query_Builder", schema = "MOHECORE")
@IdClass(QueryBuilder.QueryBuilderId.class)
public class QueryBuilder {
    
    @Id
    @Column(name = "table_name")
    private String tableName;
    
    @Id
    @Column(name = "column_name")
    private String columnName;
    
    @Column(name = "data_type")
    private String dataType;
    
    @Column(name = "column_description")
    private String columnDescription;
    
    @Column(name = "is_filterable")
    private Integer isFilterable;
    
    @Column(name = "is_sortable")
    private Integer isSortable;
    
    @Getter
    @Setter
    @lombok.EqualsAndHashCode
    public static class QueryBuilderId implements java.io.Serializable {
        private String tableName;
        private String columnName;
    }
}

