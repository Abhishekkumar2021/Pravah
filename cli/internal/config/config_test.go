package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoadConfig_Default(t *testing.T) {
	cfg, err := Load("")
	if err != nil {
		t.Fatalf("unexpected error loading default config: %v", err)
	}

	if cfg.CurrentProfile != "default" {
		t.Errorf("expected current profile 'default', got %q", cfg.CurrentProfile)
	}

	if len(cfg.Profiles) == 0 {
		t.Error("expected at least one profile")
	}

	if cfg.APIURL() == "" {
		t.Error("expected non-empty API URL")
	}
}

func TestLoadConfig_FromFile(t *testing.T) {
	tmpDir := t.TempDir()
	configPath := filepath.Join(tmpDir, "config.yaml")

	configContent := `current-profile: production
profiles:
  production:
    api-url: https://api.pravah.io
    project-id: proj-123
  staging:
    api-url: https://staging.pravah.io
`
	if err := os.WriteFile(configPath, []byte(configContent), 0600); err != nil {
		t.Fatalf("failed to write test config: %v", err)
	}

	cfg, err := Load(configPath)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if cfg.CurrentProfile != "production" {
		t.Errorf("expected profile 'production', got %q", cfg.CurrentProfile)
	}

	if cfg.APIURL() != "https://api.pravah.io" {
		t.Errorf("expected API URL 'https://api.pravah.io', got %q", cfg.APIURL())
	}

	if cfg.ProjectID() != "proj-123" {
		t.Errorf("expected project ID 'proj-123', got %q", cfg.ProjectID())
	}
}

func TestConfig_SetProfile(t *testing.T) {
	cfg := &Config{
		CurrentProfile: "default",
		Profiles:       make(map[string]Profile),
	}

	cfg.SetProfile("test", Profile{
		APIURL:    "https://test.example.com",
		ProjectID: "test-project",
	})

	if _, ok := cfg.Profiles["test"]; !ok {
		t.Error("expected profile 'test' to exist")
	}

	profile := cfg.Profiles["test"]
	if profile.APIURL != "https://test.example.com" {
		t.Errorf("expected API URL 'https://test.example.com', got %q", profile.APIURL)
	}
}

func TestConfig_DeleteProfile(t *testing.T) {
	cfg := &Config{
		CurrentProfile: "default",
		Profiles: map[string]Profile{
			"default": {APIURL: "http://localhost:8080"},
			"staging": {APIURL: "https://staging.example.com"},
		},
	}

	if err := cfg.DeleteProfile("staging"); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if _, ok := cfg.Profiles["staging"]; ok {
		t.Error("expected profile 'staging' to be deleted")
	}

	if err := cfg.DeleteProfile("default"); err == nil {
		t.Error("expected error when deleting default profile")
	}
}

func TestConfig_Save(t *testing.T) {
	tmpDir := t.TempDir()
	configPath := filepath.Join(tmpDir, ".pravah", "config.yaml")

	cfg := &Config{
		CurrentProfile: "test",
		Profiles: map[string]Profile{
			"test": {
				APIURL:    "https://test.example.com",
				ProjectID: "test-123",
			},
		},
		path: configPath,
	}

	if err := cfg.Save(); err != nil {
		t.Fatalf("unexpected error saving config: %v", err)
	}

	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		t.Error("expected config file to be created")
	}

	loaded, err := Load(configPath)
	if err != nil {
		t.Fatalf("unexpected error loading saved config: %v", err)
	}

	if loaded.CurrentProfile != "test" {
		t.Errorf("expected profile 'test', got %q", loaded.CurrentProfile)
	}
}

func TestConfig_ListProfiles(t *testing.T) {
	cfg := &Config{
		Profiles: map[string]Profile{
			"default":    {},
			"staging":    {},
			"production": {},
		},
	}

	profiles := cfg.ListProfiles()
	if len(profiles) != 3 {
		t.Errorf("expected 3 profiles, got %d", len(profiles))
	}
}
