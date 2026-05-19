# Pravah Deploy GitHub Action

Deploy workflow definitions to Pravah directly from your CI/CD pipeline.

## Usage

### Basic Usage

```yaml
name: Deploy Workflows

on:
  push:
    branches: [main]
    paths:
      - 'workflows/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Deploy to Pravah
        uses: Abhishekkumar2021/Pravah/.github/actions/deploy@main
        with:
          api-url: https://api.pravah.io
          api-token: ${{ secrets.PRAVAH_API_TOKEN }}
          workflow-files: ./workflows/
```

### Dry Run on Pull Requests

```yaml
name: Validate Workflows

on:
  pull_request:
    paths:
      - 'workflows/**'

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Validate workflows (dry run)
        uses: Abhishekkumar2021/Pravah/.github/actions/deploy@main
        with:
          api-url: https://api.pravah.io
          api-token: ${{ secrets.PRAVAH_API_TOKEN }}
          workflow-files: ./workflows/
          dry-run: 'true'
```

### Multi-Environment Deployment

```yaml
name: Deploy to Staging

on:
  push:
    branches: [develop]

jobs:
  deploy-staging:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Deploy to Staging
        uses: Abhishekkumar2021/Pravah/.github/actions/deploy@main
        with:
          api-url: https://staging-api.pravah.io
          api-token: ${{ secrets.PRAVAH_STAGING_TOKEN }}
          workflow-files: ./workflows/
          project-id: ${{ vars.PRAVAH_STAGING_PROJECT_ID }}
```

## Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| `api-url` | Pravah API URL | Yes | `https://api.pravah.io` |
| `api-token` | Pravah API token for authentication | Yes | - |
| `workflow-files` | Path to workflow files or directory | Yes | `./workflows/**/*.yaml` |
| `dry-run` | Preview changes without applying | No | `false` |
| `project-id` | Pravah project ID | No | - |
| `recursive` | Recursively process directories | No | `true` |

## Outputs

| Output | Description |
|--------|-------------|
| `deployment-status` | Deployment status: `success`, `no-changes`, or `failed` |
| `workflow-ids` | JSON array of deployed workflow IDs |
| `changes` | JSON object with change counts: `{created, updated, unchanged, errors}` |

## Exit Codes

The action uses specific exit codes for CI integration:

| Code | Meaning |
|------|---------|
| 0 | Success - workflows deployed |
| 2 | No changes - all workflows up to date |
| 1 | Error - deployment failed |

## API Token

Create an API token in Pravah:

1. Go to **Settings** → **API Tokens**
2. Click **Create Token**
3. Give it a descriptive name (e.g., "GitHub Actions - Production")
4. Copy the token and store it as a GitHub secret

```bash
# Using GitHub CLI
gh secret set PRAVAH_API_TOKEN
```

## Workflow File Format

Your workflow files should be valid Pravah YAML definitions:

```yaml
# workflows/etl-pipeline.yaml
name: etl-pipeline
description: Daily ETL pipeline

stages:
  - name: extract
    type: sql
    config:
      connection: ${connection.warehouse}
      query: SELECT * FROM source_table

  - name: transform
    type: python
    depends_on: [extract]
    config:
      script: |
        data = ${stages.extract.output.rows}
        # Transform logic here

  - name: load
    type: sql
    depends_on: [transform]
    config:
      connection: ${connection.warehouse}
      query: INSERT INTO target_table ...
```

## Troubleshooting

### Authentication Failed

- Verify your API token is valid and not expired
- Check that the token has sufficient permissions
- Ensure the API URL is correct for your environment

### No Workflows Found

- Check the `workflow-files` path pattern
- Verify files have `.yaml` or `.yml` extension
- Enable `recursive: true` for nested directories

### Validation Errors

Use `dry-run: true` to see what would change:

```yaml
- name: Validate
  uses: Abhishekkumar2021/Pravah/.github/actions/deploy@main
  with:
    api-token: ${{ secrets.PRAVAH_API_TOKEN }}
    workflow-files: ./workflows/
    dry-run: 'true'
```
