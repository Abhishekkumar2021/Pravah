## Summary

<!-- Provide a brief description of the changes -->

## Type of Change

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Refactoring (no functional changes)
- [ ] Performance improvement
- [ ] Test coverage improvement

## Related Issues

<!-- Link to related issues: Fixes #123, Relates to #456 -->

## Documentation Alignment

- [ ] Changes align with ADRs in `docs/adr/`
- [ ] Changes follow patterns in `docs/lld/`
- [ ] State transitions match `docs/lld/03-state-machines.md` (if applicable)

## Checklist

### Code Quality
- [ ] Code follows the project's coding standards
- [ ] Self-reviewed my own code
- [ ] Added comments for complex logic
- [ ] No TODO comments without issue reference

### Testing
- [ ] Added unit tests for new code
- [ ] Added integration tests for external dependencies
- [ ] All new and existing tests pass locally
- [ ] Test coverage meets minimum requirements (>80%)

### Security (for multi-tenant changes)
- [ ] Tenant isolation enforced
- [ ] No cross-tenant data leakage possible
- [ ] Input validation in place
- [ ] No secrets or PII in logs

### Patterns (for event-driven changes)
- [ ] Events published via Outbox pattern
- [ ] Kafka consumers are idempotent
- [ ] Dead letter topic configured

## Screenshots/Recordings

<!-- If applicable, add screenshots or recordings to help explain your changes -->

## Additional Notes

<!-- Any additional information that reviewers should know -->
