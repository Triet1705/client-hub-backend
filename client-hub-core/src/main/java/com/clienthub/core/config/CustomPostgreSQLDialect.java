package com.clienthub.core.config;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.UUIDJdbcType;

import java.sql.Types;

/**
 * Custom PostgreSQL Dialect that forces UUID to use native PostgreSQL uuid type
 * instead of Hibernate 6 default bytea mapping.
 * 
 * This solves the incompatibility between Spring Boot 3.3.6 (Hibernate 6.5.x)
 * and PostgreSQL native uuid columns created by Flyway migrations.
 */
public class CustomPostgreSQLDialect extends PostgreSQLDialect {

    public CustomPostgreSQLDialect() {
        super(DatabaseVersion.make(17, 0));
    }

    @Override
    public JdbcType resolveSqlTypeDescriptor(
            String columnTypeName,
            int jdbcTypeCode,
            int precision,
            int scale,
            org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry jdbcTypeRegistry) {
        
        if ("uuid".equalsIgnoreCase(columnTypeName)) {
            return jdbcTypeRegistry.getDescriptor(Types.OTHER);
        }
        
        return super.resolveSqlTypeDescriptor(columnTypeName, jdbcTypeCode, precision, scale, jdbcTypeRegistry);
    }

    @Override
    public void contribute(org.hibernate.boot.model.TypeContributions typeContributions, 
                          org.hibernate.service.ServiceRegistry serviceRegistry) {
        super.contribute(typeContributions, serviceRegistry);
        
        typeContributions.getTypeConfiguration()
                .getJdbcTypeRegistry()
                .addDescriptor(Types.OTHER, UUIDJdbcType.INSTANCE);
    }
}
