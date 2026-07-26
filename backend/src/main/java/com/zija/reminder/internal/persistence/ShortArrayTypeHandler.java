package com.zija.reminder.internal.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis type handler for PostgreSQL SMALLINT[] ↔ java.util.List&lt;Short&gt;.
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class ShortArrayTypeHandler extends BaseTypeHandler<List<Short>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Short> parameter, JdbcType jdbcType) throws SQLException {
        Object[] arr = parameter.stream().map(s -> (Object) s).toArray();
        Array array = ps.getConnection().createArrayOf("int2", arr);
        ps.setArray(i, array);
    }

    @Override
    public List<Short> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toList(rs.getArray(columnName));
    }

    @Override
    public List<Short> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toList(rs.getArray(columnIndex));
    }

    @Override
    public List<Short> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toList(cs.getArray(columnIndex));
    }

    private List<Short> toList(Array array) throws SQLException {
        if (array == null) return null;
        Object[] values = (Object[]) array.getArray();
        List<Short> result = new ArrayList<>(values.length);
        for (Object v : values) {
            result.add(v == null ? null : ((Number) v).shortValue());
        }
        return result;
    }
}
