package com.clienthub.infrastructure.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.*;
import java.util.UUID;

public class PostgreSQLUUIDTypeContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        typeContributions.getTypeConfiguration()
                .getJdbcTypeRegistry()
                .addDescriptor(Types.OTHER, PostgreSQLUUIDJdbcType.INSTANCE);
    }

    /**
     * Custom JDBC type that forces PostgreSQL native UUID handling
     * instead of Hibernate 6's default bytea mapping
     */
    public static class PostgreSQLUUIDJdbcType implements JdbcType {
        
        public static final PostgreSQLUUIDJdbcType INSTANCE = new PostgreSQLUUIDJdbcType();

        @Override
        public int getJdbcTypeCode() {
            return Types.OTHER;
        }

        @Override
        public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
            return new BasicBinder<X>(javaType, this) {
                @Override
                protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
                        throws SQLException {
                    st.setObject(index, javaType.unwrap(value, UUID.class, options), Types.OTHER);
                }

                @Override
                protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                        throws SQLException {
                    st.setObject(name, javaType.unwrap(value, UUID.class, options), Types.OTHER);
                }
            };
        }

        @Override
        public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
            return new BasicExtractor<X>(javaType, this) {
                @Override
                protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                    return javaType.wrap(rs.getObject(paramIndex, UUID.class), options);
                }

                @Override
                protected X doExtract(CallableStatement statement, int index, WrapperOptions options)
                        throws SQLException {
                    return javaType.wrap(statement.getObject(index, UUID.class), options);
                }

                @Override
                protected X doExtract(CallableStatement statement, String name, WrapperOptions options)
                        throws SQLException {
                    return javaType.wrap(statement.getObject(name, UUID.class), options);
                }
            };
        }
    }
}