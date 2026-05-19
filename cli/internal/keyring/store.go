package keyring

import (
	"encoding/json"
	"fmt"

	"github.com/zalando/go-keyring"
)

const serviceName = "pravah-cli"

// Credentials holds authentication credentials
type Credentials struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token,omitempty"`
	TokenType    string `json:"token_type"`
	ExpiresAt    int64  `json:"expires_at,omitempty"`
	APIToken     string `json:"api_token,omitempty"`
}

// Store manages secure credential storage
type Store struct {
	profile string
}

// NewStore creates a new credential store for the given profile
func NewStore(profile string) *Store {
	return &Store{profile: profile}
}

// keyName returns the keyring key name for this profile
func (s *Store) keyName() string {
	return fmt.Sprintf("credentials-%s", s.profile)
}

// Save stores credentials securely
func (s *Store) Save(creds *Credentials) error {
	data, err := json.Marshal(creds)
	if err != nil {
		return fmt.Errorf("marshaling credentials: %w", err)
	}

	if err := keyring.Set(serviceName, s.keyName(), string(data)); err != nil {
		return fmt.Errorf("storing credentials: %w", err)
	}

	return nil
}

// Load retrieves stored credentials
func (s *Store) Load() (*Credentials, error) {
	data, err := keyring.Get(serviceName, s.keyName())
	if err != nil {
		if err == keyring.ErrNotFound {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("retrieving credentials: %w", err)
	}

	var creds Credentials
	if err := json.Unmarshal([]byte(data), &creds); err != nil {
		return nil, fmt.Errorf("parsing credentials: %w", err)
	}

	return &creds, nil
}

// Delete removes stored credentials
func (s *Store) Delete() error {
	if err := keyring.Delete(serviceName, s.keyName()); err != nil {
		if err == keyring.ErrNotFound {
			return nil
		}
		return fmt.Errorf("deleting credentials: %w", err)
	}
	return nil
}

// HasCredentials checks if credentials exist
func (s *Store) HasCredentials() bool {
	_, err := s.Load()
	return err == nil
}

// GetToken returns the token for API requests
func (s *Store) GetToken() (string, error) {
	creds, err := s.Load()
	if err != nil {
		return "", err
	}

	if creds.APIToken != "" {
		return creds.APIToken, nil
	}
	return creds.AccessToken, nil
}

// SaveAPIToken stores an API token
func (s *Store) SaveAPIToken(token string) error {
	creds := &Credentials{
		APIToken:  token,
		TokenType: "Bearer",
	}
	return s.Save(creds)
}

// ErrNotFound is returned when credentials are not found
var ErrNotFound = fmt.Errorf("credentials not found")
