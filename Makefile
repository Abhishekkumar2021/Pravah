# Repo-root shortcuts for local development
.PHONY: local-setup local-up local-down local-services local-services-stop local-status local-web local-seed

local-setup local-up local-down local-services local-services-stop local-status local-web local-seed:
	$(MAKE) -C backend $@
