# Repo-root shortcuts for local development
.PHONY: local-setup local-up local-down local-services local-services-stop local-status local-web local-seed
.PHONY: k8s-local-build k8s-local-install k8s-local-seed k8s-local-smoke k8s-local-runner-grpc-tls k8s-local-argocd k8s-ci-smoke k8s-ci-smoke-argocd validate-argocd validate-terraform
.PHONY: pre-commit ci-backend ci-web ci-cli check

local-setup local-up local-down local-services local-services-stop local-status local-web local-seed:
	$(MAKE) -C backend $@

k8s-local-build:
	./scripts/deploy/k8s-local-build.sh

k8s-local-install:
	./scripts/deploy/k8s-local-install.sh

k8s-local-seed:
	./scripts/deploy/k8s-local-seed.sh

k8s-local-smoke:
	./scripts/deploy/k8s-local-smoke.sh

k8s-ci-smoke:
	./scripts/deploy/k8s-ci-smoke.sh

k8s-ci-smoke-argocd:
	./scripts/deploy/k8s-ci-smoke-argocd.sh

k8s-local-runner-grpc-tls:
	./scripts/deploy/generate-runner-grpc-tls.sh
	./scripts/deploy/k8s-local-runner-grpc-tls.sh

k8s-local-argocd:
	./scripts/deploy/k8s-local-argocd.sh

validate-argocd:
	./scripts/deploy/validate-argocd-manifests.sh

validate-terraform:
	./scripts/deploy/validate-terraform.sh

# CI-parity checks (see scripts/pre-commit.sh)
pre-commit:
	./scripts/pre-commit.sh

ci-backend:
	./scripts/ci-backend.sh

ci-web:
	./scripts/ci-web.sh

ci-cli:
	./scripts/ci-cli.sh

check:
	FORCE_ALL=1 ./scripts/pre-commit.sh
