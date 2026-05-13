package io.pravah.tenant.infrastructure.persistence;

import io.pravah.tenant.domain.model.User;

/** Hibernate UserType for mapping {@link User.Status} to PostgreSQL's user_status ENUM type. */
public class UserStatusType extends PostgresEnumType<User.Status> {

  public UserStatusType() {
    super(User.Status.class);
  }
}
