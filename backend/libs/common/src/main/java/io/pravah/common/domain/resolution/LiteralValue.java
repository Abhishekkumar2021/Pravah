package io.pravah.common.domain.resolution;

/** A literal value that requires no resolution. */
public record LiteralValue(String value) implements ValueReference {

  @Override
  public String raw() {
    return value;
  }

  @Override
  public boolean isDeferred() {
    return false;
  }

  @Override
  public ReferenceType type() {
    return ReferenceType.LITERAL;
  }
}
