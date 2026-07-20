package com.zija.system.internal.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

@MappedTypes(Map.class)
public class JsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(
            PreparedStatement ps, int i,
            Map<String, Object> parameter, JdbcType jdbcType
    ) throws SQLException {
        try {
            String json = MAPPER.writeValueAsString(parameter);
            // Types.OTHER lets the PostgreSQL JDBC driver send the value as a
            // string with an unknown OID so the server performs an implicit
            // text -> jsonb cast. This avoids a compile-time dependency on
            // org.postgresql.util.PGobject (the driver is runtime-scoped).
            ps.setObject(i, json, Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new SQLException("failed to serialize jsonb", e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) throws SQLException {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("failed to deserialize jsonb", e);
        }
    }
}
