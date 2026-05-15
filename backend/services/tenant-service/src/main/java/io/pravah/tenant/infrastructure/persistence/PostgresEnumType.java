package io.pravah.tenant.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * Hibernate UserType for mapping Java enums to PostgreSQL ENUM types.
 *
 * <p>PostgreSQL ENUM types require special handling because they are not compatible with standard
 * JDBC VARCHAR binding. This type uses PostgreSQL's native object type with explicit casting.
 *
 * @param <E> the enum type
 */
public abstract class PostgresEnumType<E extends Enum<E>> implements UserType<E> {

  private final Class<E> enumClass;

  protected PostgresEnumType(Class<E> enumClass) {
    this.enumClass = enumClass;
  }

  @Override
  public int getSqlType() {
    return Types.OTHER;
  }

  @Override
  public Class<E> returnedClass() {
    return enumClass;
  }

  @Override
  public boolean equals(E x, E y) {
    return x == y || (x != null && x.equals(y));
  }

  @Override
  public int hashCode(E x) {
    return x == null ? 0 : x.hashCode();
  }

  @Override
  public E nullSafeGet(
      ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
      throws SQLException {
    String value = rs.getString(position);
    if (rs.wasNull() || value == null) {
      return null;
    }
    return Enum.valueOf(enumClass, value);
  }

  @Override
  public void nullSafeSet(
      PreparedStatement st, E value, int index, SharedSessionContractImplementor session)
      throws SQLException {
    if (value == null) {
      st.setNull(index, Types.OTHER);
    } else {
      st.setObject(index, value.name(), Types.OTHER);
    }
  }

  @Override
  public E deepCopy(E value) {
    return value;
  }

  @Override
  public boolean isMutable() {
    return false;
  }

  @Override
  public java.io.Serializable disassemble(E value) {
    return value;
  }

  @Override
  @SuppressWarnings("unchecked")
  public E assemble(java.io.Serializable cached, Object owner) {
    return (E) cached;
  }
}
