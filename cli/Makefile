.PHONY: all build install clean test lint fmt vet tidy deps help install-tools check-lint-tool

BINARY_NAME = pravah
GOLANGCI_LINT_VERSION ?= v$(shell tr -d '[:space:]' < .golangci-version 2>/dev/null || echo 1.57.2)
GOBIN ?= $(shell go env GOPATH)/bin
VERSION ?= $(shell git describe --tags --always --dirty 2>/dev/null || echo "dev")
COMMIT ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown")
DATE ?= $(shell date -u +"%Y-%m-%dT%H:%M:%SZ")
LDFLAGS = -ldflags "-X main.version=$(VERSION) -X main.commit=$(COMMIT) -X main.date=$(DATE)"

all: build

build: ## Build the CLI binary
	go build $(LDFLAGS) -o bin/$(BINARY_NAME) ./cmd/pravah

install: ## Install the CLI to $GOPATH/bin
	go install $(LDFLAGS) ./cmd/pravah

clean: ## Remove build artifacts
	rm -rf bin/
	rm -rf dist/

test: ## Run tests
	go test -v -race -coverprofile=coverage.out ./...

test-short: ## Run tests (short mode)
	go test -v -short ./...

install-tools: ## Install golangci-lint (pinned; matches CI; one-time)
	@curl -sSfL https://raw.githubusercontent.com/golangci/golangci-lint/master/install.sh | \
		sh -s -- -b "$(GOBIN)" "$(GOLANGCI_LINT_VERSION)"
	@echo "Installed to $(GOBIN). Add to PATH: export PATH=\"$(GOBIN):\$$PATH\""

check-lint-tool: ## Verify golangci-lint version matches .golangci-version
	@command -v golangci-lint >/dev/null || (echo "Run: cd cli && make install-tools" && exit 1)
	@test "$$(golangci-lint version --format short)" = "$$(tr -d '[:space:]' < .golangci-version)" || \
		(echo "golangci-lint version mismatch; run: make install-tools" && exit 1)

lint: check-lint-tool ## Run linter (golangci-lint)
	golangci-lint run ./...

fmt: ## Format code
	go fmt ./...
	gofumpt -l -w .

vet: ## Run go vet
	go vet ./...

tidy: ## Tidy go modules
	go mod tidy

deps: ## Download dependencies
	go mod download

snapshot: ## Build snapshot release (local testing)
	goreleaser release --snapshot --clean

release: ## Build release
	goreleaser release --clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'
