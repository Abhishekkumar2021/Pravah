.PHONY: all build install clean test lint fmt vet tidy help

BINARY_NAME = pravah
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

lint: ## Run linter
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
