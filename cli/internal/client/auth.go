package client

import (
	"context"
	"fmt"
)

// LoginRequest represents login credentials
type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

// LoginResponse represents the login response
type LoginResponse struct {
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken,omitempty"`
	TokenType    string `json:"tokenType"`
	ExpiresIn    int64  `json:"expiresIn"`
}

// UserInfo represents the current user information
type UserInfo struct {
	ID        string `json:"id"`
	Email     string `json:"email"`
	Name      string `json:"name"`
	TenantID  string `json:"tenantId"`
	Roles     []Role `json:"roles"`
	CreatedAt string `json:"createdAt"`
}

// Role represents a user role
type Role struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// Login authenticates with email and password
func (c *Client) Login(ctx context.Context, email, password string) (*LoginResponse, error) {
	req := LoginRequest{
		Email:    email,
		Password: password,
	}

	var resp LoginResponse
	if err := c.Post(ctx, "/api/v1/auth/login", req, &resp); err != nil {
		return nil, fmt.Errorf("login failed: %w", err)
	}

	c.token = resp.AccessToken
	return &resp, nil
}

// Logout invalidates the current token
func (c *Client) Logout(ctx context.Context) error {
	if c.token == "" {
		return nil
	}

	err := c.Post(ctx, "/api/v1/auth/logout", nil, nil)
	if err != nil {
		apiErr, ok := err.(*APIError)
		if !ok || !apiErr.IsUnauthorized() {
			return fmt.Errorf("logout failed: %w", err)
		}
	}

	c.token = ""
	return nil
}

// GetCurrentUser retrieves the current user's information
func (c *Client) GetCurrentUser(ctx context.Context) (*UserInfo, error) {
	var user UserInfo
	if err := c.Get(ctx, "/api/v1/auth/me", &user); err != nil {
		return nil, fmt.Errorf("getting user info: %w", err)
	}
	return &user, nil
}

// RefreshToken refreshes the access token using a refresh token
func (c *Client) RefreshToken(ctx context.Context, refreshToken string) (*LoginResponse, error) {
	req := map[string]string{
		"refreshToken": refreshToken,
	}

	var resp LoginResponse
	if err := c.Post(ctx, "/api/v1/auth/refresh", req, &resp); err != nil {
		return nil, fmt.Errorf("token refresh failed: %w", err)
	}

	c.token = resp.AccessToken
	return &resp, nil
}

// ValidateToken validates the current token
func (c *Client) ValidateToken(ctx context.Context) error {
	_, err := c.GetCurrentUser(ctx)
	return err
}
