# Pravah CLI

Command-line interface for managing data pipelines, workflows, and executions in the Pravah platform.

## Installation

### Homebrew (macOS/Linux)

```bash
brew install Abhishekkumar2021/tap/pravah
```

### Curl (Linux/macOS)

```bash
curl -sSL https://raw.githubusercontent.com/Abhishekkumar2021/Pravah/main/cli/scripts/install.sh | bash
```

### Go Install

```bash
go install github.com/Abhishekkumar2021/Pravah/cli/cmd/pravah@latest
```

### Manual Download

Download the latest release from [GitHub Releases](https://github.com/Abhishekkumar2021/Pravah/releases).

## Quick Start

```bash
# Login to Pravah
pravah login

# List workflows
pravah workflow list

# Run a workflow
pravah workflow run <workflow-id>

# Deploy workflows
pravah deploy -f workflows/
```

## Authentication

### Interactive Login

```bash
# Browser-based OAuth flow (default)
pravah login
```

### API Token

For CI/CD environments:

```bash
# Set token directly
pravah login --token <api-token>

# Or set environment variable
export PRAVAH_API_TOKEN=<api-token>
```

### Check Status

```bash
pravah auth status
```

## Commands

### Workflow Management

```bash
# List all workflows
pravah workflow list

# List with filters
pravah workflow list --status ACTIVE --search "etl"

# Get workflow details
pravah workflow get <id>
pravah workflow get <id> -o yaml

# Create from YAML
pravah workflow create -f workflow.yaml

# Validate without creating
pravah workflow validate -f workflow.yaml

# Run a workflow
pravah workflow run <id>
pravah workflow run <id> --param env=prod --param date=2024-01-01

# Run and wait for completion
pravah workflow run <id> --wait
```

### Execution Management

```bash
# List executions
pravah run list
pravah run list --pipeline-id <id> --status RUNNING

# Get execution status
pravah run status <id>
pravah run status <id> --watch

# View logs
pravah run logs <id>
pravah run logs <id> --stage extract
pravah run logs <id> --follow

# Cancel execution
pravah run cancel <id>
pravah run cancel <id> --force

# Retry failed execution
pravah run retry <id>
pravah run retry <id> --from-stage transform
```

### Deployment

```bash
# Deploy single file
pravah deploy -f workflow.yaml

# Deploy directory (recursive)
pravah deploy -f workflows/ --recursive

# Preview changes (dry run)
pravah deploy -f workflows/ --dry-run

# Show diff
pravah diff -f workflow.yaml
```

### Configuration

```bash
# Show current config
pravah config get

# Set values
pravah config set api-url https://api.pravah.io
pravah config set project-id <project-id>

# Profile management
pravah config profile list
pravah config profile create production --api-url https://api.pravah.io
pravah config profile use production
pravah config profile delete staging
```

## Configuration File

Configuration is stored in `~/.pravah/config.yaml`:

```yaml
current-profile: default
profiles:
  default:
    api-url: http://localhost:8080
    project-id: ""
  production:
    api-url: https://api.pravah.io
    project-id: 550e8400-e29b-41d4-a716-446655440000
```

## Output Formats

All commands support multiple output formats:

```bash
# Table (default)
pravah workflow list

# JSON
pravah workflow list -o json

# YAML
pravah workflow list -o yaml
```

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Error |
| 2 | No changes (for deploy) |
| 3 | Authentication required |
| 4 | Validation error |

## CI/CD Integration

### GitHub Actions

```yaml
- uses: Abhishekkumar2021/Pravah/.github/actions/deploy@main
  with:
    api-url: https://api.pravah.io
    api-token: ${{ secrets.PRAVAH_API_TOKEN }}
    workflow-files: ./workflows/
```

See [.github/actions/deploy/README.md](../.github/actions/deploy/README.md) for full documentation.

### GitLab CI

```yaml
deploy:
  image: ghcr.io/abhishekkumar2021/pravah-cli:latest
  script:
    - pravah login --token $PRAVAH_API_TOKEN
    - pravah deploy -f workflows/
```

### Generic CI

```bash
# Install CLI
curl -sSL https://raw.githubusercontent.com/Abhishekkumar2021/Pravah/main/cli/scripts/install.sh | bash

# Authenticate
pravah login --token $PRAVAH_API_TOKEN

# Deploy
pravah deploy -f workflows/ --dry-run  # Preview
pravah deploy -f workflows/             # Apply
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `PRAVAH_API_URL` | API URL override |
| `PRAVAH_API_TOKEN` | Authentication token |
| `PRAVAH_PROJECT_ID` | Default project ID |
| `PRAVAH_PROFILE` | Active profile name |

## Development

### Build from Source

```bash
cd cli
make build
./bin/pravah version
```

### Run Tests

```bash
make test
```

### Install golangci-lint (one-time; required for lint / CI parity)

Version is pinned in `cli/.golangci-version` (currently **1.57.2**, same as `.github/workflows/cli-ci.yml`).

```bash
cd cli && make install-tools
export PATH="$(go env GOPATH)/bin:$PATH"
golangci-lint version --format short   # must print 1.57.2
```

### Local CI parity

From repo root (matches `.github/workflows/cli-ci.yml`):

```bash
export PATH="$(go env GOPATH)/bin:$PATH"
./scripts/ci-cli.sh
```

Or from `cli/`:

```bash
make lint
make test
make build
```

### Release

```bash
# Snapshot (local testing)
make snapshot

# Full release (requires GoReleaser)
make release
```

## Troubleshooting

### Authentication Issues

```bash
# Check current auth status
pravah auth status

# Re-login
pravah logout
pravah login
```

### Connection Errors

```bash
# Verify API URL
pravah config get api-url

# Test connectivity
curl -I https://api.pravah.io/health
```

### Debug Mode

```bash
# Enable verbose output
PRAVAH_DEBUG=1 pravah workflow list
```

## Related Documentation

- [API Documentation](../backend/README.md#api-documentation)
- [Workflow YAML Reference](../docs/lld/05-class-diagrams.md)
- [GitHub Action](../.github/actions/deploy/README.md)

## License

Apache License 2.0
