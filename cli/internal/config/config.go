package config

import (
	"fmt"
	"os"
	"path/filepath"

	"gopkg.in/yaml.v3"
)

// Profile represents a named configuration profile
type Profile struct {
	APIURL    string `yaml:"api-url"`
	ProjectID string `yaml:"project-id"`
}

// Config represents the CLI configuration
type Config struct {
	CurrentProfile string             `yaml:"current-profile"`
	Profiles       map[string]Profile `yaml:"profiles"`
	path           string
}

// DefaultConfigPath returns the default config file path
func DefaultConfigPath() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return ".pravah/config.yaml"
	}
	return filepath.Join(home, ".pravah", "config.yaml")
}

// Load loads or creates the configuration
func Load(path string) (*Config, error) {
	if path == "" {
		path = DefaultConfigPath()
	}

	cfg := &Config{
		CurrentProfile: "default",
		Profiles: map[string]Profile{
			"default": {
				APIURL: "http://localhost:8080",
			},
		},
		path: path,
	}

	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return cfg, nil
		}
		return nil, fmt.Errorf("reading config: %w", err)
	}

	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("parsing config: %w", err)
	}
	cfg.path = path

	return cfg, nil
}

// Save saves the configuration to disk
func (c *Config) Save() error {
	dir := filepath.Dir(c.path)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return fmt.Errorf("creating config directory: %w", err)
	}

	data, err := yaml.Marshal(c)
	if err != nil {
		return fmt.Errorf("marshaling config: %w", err)
	}

	if err := os.WriteFile(c.path, data, 0600); err != nil {
		return fmt.Errorf("writing config: %w", err)
	}

	return nil
}

// GetProfile returns the current profile settings
func (c *Config) GetProfile() Profile {
	profile, ok := c.Profiles[c.CurrentProfile]
	if !ok {
		return Profile{APIURL: "http://localhost:8080"}
	}
	return profile
}

// SetCurrentProfile sets the active profile
func (c *Config) SetCurrentProfile(name string) {
	c.CurrentProfile = name
}

// SetProfile creates or updates a profile
func (c *Config) SetProfile(name string, profile Profile) {
	if c.Profiles == nil {
		c.Profiles = make(map[string]Profile)
	}
	c.Profiles[name] = profile
}

// DeleteProfile removes a profile
func (c *Config) DeleteProfile(name string) error {
	if name == "default" {
		return fmt.Errorf("cannot delete default profile")
	}
	if c.CurrentProfile == name {
		c.CurrentProfile = "default"
	}
	delete(c.Profiles, name)
	return nil
}

// ListProfiles returns all profile names
func (c *Config) ListProfiles() []string {
	names := make([]string, 0, len(c.Profiles))
	for name := range c.Profiles {
		names = append(names, name)
	}
	return names
}

// APIURL returns the API URL for the current profile
func (c *Config) APIURL() string {
	return c.GetProfile().APIURL
}

// ProjectID returns the project ID for the current profile
func (c *Config) ProjectID() string {
	return c.GetProfile().ProjectID
}
