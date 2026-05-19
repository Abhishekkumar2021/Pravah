package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

// Client is the Pravah API client
type Client struct {
	baseURL    string
	httpClient *http.Client
	token      string
	projectID  string
}

// ClientOption is a functional option for configuring the client
type ClientOption func(*Client)

// WithToken sets the authentication token
func WithToken(token string) ClientOption {
	return func(c *Client) {
		c.token = token
	}
}

// WithProjectID sets the project ID
func WithProjectID(projectID string) ClientOption {
	return func(c *Client) {
		c.projectID = projectID
	}
}

// WithTimeout sets the HTTP timeout
func WithTimeout(timeout time.Duration) ClientOption {
	return func(c *Client) {
		c.httpClient.Timeout = timeout
	}
}

// NewClient creates a new API client
func NewClient(baseURL string, opts ...ClientOption) *Client {
	c := &Client{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}

	for _, opt := range opts {
		opt(c)
	}

	return c
}

// SetToken updates the authentication token
func (c *Client) SetToken(token string) {
	c.token = token
}

// request performs an HTTP request with retry logic
func (c *Client) request(ctx context.Context, method, path string, body interface{}) (*http.Response, error) {
	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("marshaling request body: %w", err)
		}
		bodyReader = bytes.NewReader(data)
	}

	endpoint, err := url.JoinPath(c.baseURL, path)
	if err != nil {
		return nil, fmt.Errorf("building URL: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, method, endpoint, bodyReader)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	if c.token != "" {
		req.Header.Set("Authorization", "Bearer "+c.token)
	}

	if c.projectID != "" {
		req.Header.Set("X-Project-ID", c.projectID)
	}

	var resp *http.Response
	var lastErr error
	maxRetries := 3
	for attempt := 0; attempt < maxRetries; attempt++ {
		resp, lastErr = c.httpClient.Do(req)
		if lastErr == nil {
			if resp.StatusCode < 500 {
				break
			}
			resp.Body.Close()
		}
		if attempt < maxRetries-1 {
			backoff := time.Duration(1<<attempt) * time.Second
			time.Sleep(backoff)

			if body != nil {
				data, _ := json.Marshal(body)
				bodyReader = bytes.NewReader(data)
				req.Body = io.NopCloser(bodyReader)
			}
		}
	}

	if lastErr != nil {
		return nil, fmt.Errorf("request failed after %d retries: %w", maxRetries, lastErr)
	}

	return resp, nil
}

// Get performs a GET request
func (c *Client) Get(ctx context.Context, path string, result interface{}) error {
	resp, err := c.request(ctx, http.MethodGet, path, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return c.handleResponse(resp, result)
}

// Post performs a POST request
func (c *Client) Post(ctx context.Context, path string, body, result interface{}) error {
	resp, err := c.request(ctx, http.MethodPost, path, body)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return c.handleResponse(resp, result)
}

// Put performs a PUT request
func (c *Client) Put(ctx context.Context, path string, body, result interface{}) error {
	resp, err := c.request(ctx, http.MethodPut, path, body)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return c.handleResponse(resp, result)
}

// Delete performs a DELETE request
func (c *Client) Delete(ctx context.Context, path string) error {
	resp, err := c.request(ctx, http.MethodDelete, path, nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return c.handleResponse(resp, nil)
}

func (c *Client) handleResponse(resp *http.Response, result interface{}) error {
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("reading response: %w", err)
	}

	if resp.StatusCode >= 400 {
		var apiErr APIError
		if json.Unmarshal(body, &apiErr) == nil && apiErr.Message != "" {
			apiErr.StatusCode = resp.StatusCode
			return &apiErr
		}
		return &APIError{
			StatusCode: resp.StatusCode,
			Message:    string(body),
		}
	}

	if result != nil && len(body) > 0 {
		if err := json.Unmarshal(body, result); err != nil {
			return fmt.Errorf("parsing response: %w", err)
		}
	}

	return nil
}

// APIError represents an API error response
type APIError struct {
	StatusCode int    `json:"-"`
	Message    string `json:"message"`
	Code       string `json:"code,omitempty"`
	Details    string `json:"details,omitempty"`
}

func (e *APIError) Error() string {
	if e.Code != "" {
		return fmt.Sprintf("[%d] %s: %s", e.StatusCode, e.Code, e.Message)
	}
	return fmt.Sprintf("[%d] %s", e.StatusCode, e.Message)
}

// IsNotFound returns true if the error is a 404
func (e *APIError) IsNotFound() bool {
	return e.StatusCode == 404
}

// IsUnauthorized returns true if the error is a 401
func (e *APIError) IsUnauthorized() bool {
	return e.StatusCode == 401
}

// IsForbidden returns true if the error is a 403
func (e *APIError) IsForbidden() bool {
	return e.StatusCode == 403
}
