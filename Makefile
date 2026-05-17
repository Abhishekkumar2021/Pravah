# Repo-root shortcuts for local development
.PHONY: local-setup local-up local-down local-services local-services-stop local-status local-web local-seed
.PHONY: k8s-local-build k8s-local-install k8s-local-seed

local-setup local-up local-down local-services local-services-stop local-status local-web local-seed:
	$(MAKE) -C backend $@

k8s-local-build:
	./scripts/deploy/k8s-local-build.sh

k8s-local-install:
	./scripts/deploy/k8s-local-install.sh

k8s-local-seed:
	./scripts/deploy/k8s-local-seed.sh
