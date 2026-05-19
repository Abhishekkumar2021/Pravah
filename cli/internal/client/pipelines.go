package client

import (
	"context"
	"fmt"
	"net/url"
)

// Pipeline represents a workflow/pipeline
type Pipeline struct {
	ID          string                 `json:"id"`
	Name        string                 `json:"name"`
	Description string                 `json:"description,omitempty"`
	Version     int                    `json:"version"`
	Status      string                 `json:"status"`
	Definition  map[string]interface{} `json:"definition,omitempty"`
	CreatedAt   string                 `json:"createdAt"`
	UpdatedAt   string                 `json:"updatedAt"`
}

// PipelineList represents a paginated list of pipelines
type PipelineList struct {
	Content       []Pipeline `json:"content"`
	TotalElements int        `json:"totalElements"`
	TotalPages    int        `json:"totalPages"`
	Number        int        `json:"number"`
	Size          int        `json:"size"`
}

// CreatePipelineRequest represents a request to create a pipeline
type CreatePipelineRequest struct {
	Name        string                 `json:"name"`
	Description string                 `json:"description,omitempty"`
	Definition  map[string]interface{} `json:"definition"`
}

// UpdatePipelineRequest represents a request to update a pipeline
type UpdatePipelineRequest struct {
	Name        string                 `json:"name,omitempty"`
	Description string                 `json:"description,omitempty"`
	Definition  map[string]interface{} `json:"definition,omitempty"`
}

// ValidatePipelineRequest represents a request to validate a pipeline definition
type ValidatePipelineRequest struct {
	Definition map[string]interface{} `json:"definition"`
}

// ValidationResult represents the result of pipeline validation
type ValidationResult struct {
	Valid    bool     `json:"valid"`
	Errors   []string `json:"errors,omitempty"`
	Warnings []string `json:"warnings,omitempty"`
}

// ListPipelines retrieves pipelines with optional filtering
func (c *Client) ListPipelines(ctx context.Context, opts *ListPipelinesOptions) (*PipelineList, error) {
	path := "/api/v1/pipelines"
	if opts != nil {
		params := url.Values{}
		if opts.Page > 0 {
			params.Set("page", fmt.Sprintf("%d", opts.Page))
		}
		if opts.Size > 0 {
			params.Set("size", fmt.Sprintf("%d", opts.Size))
		}
		if opts.Status != "" {
			params.Set("status", opts.Status)
		}
		if opts.Search != "" {
			params.Set("search", opts.Search)
		}
		if len(params) > 0 {
			path += "?" + params.Encode()
		}
	}

	var result PipelineList
	if err := c.Get(ctx, path, &result); err != nil {
		return nil, fmt.Errorf("listing pipelines: %w", err)
	}
	return &result, nil
}

// ListPipelinesOptions contains options for listing pipelines
type ListPipelinesOptions struct {
	Page   int
	Size   int
	Status string
	Search string
}

// GetPipeline retrieves a pipeline by ID
func (c *Client) GetPipeline(ctx context.Context, id string) (*Pipeline, error) {
	var pipeline Pipeline
	if err := c.Get(ctx, fmt.Sprintf("/api/v1/pipelines/%s", id), &pipeline); err != nil {
		return nil, fmt.Errorf("getting pipeline: %w", err)
	}
	return &pipeline, nil
}

// CreatePipeline creates a new pipeline
func (c *Client) CreatePipeline(ctx context.Context, req *CreatePipelineRequest) (*Pipeline, error) {
	var pipeline Pipeline
	if err := c.Post(ctx, "/api/v1/pipelines", req, &pipeline); err != nil {
		return nil, fmt.Errorf("creating pipeline: %w", err)
	}
	return &pipeline, nil
}

// UpdatePipeline updates an existing pipeline
func (c *Client) UpdatePipeline(ctx context.Context, id string, req *UpdatePipelineRequest) (*Pipeline, error) {
	var pipeline Pipeline
	if err := c.Put(ctx, fmt.Sprintf("/api/v1/pipelines/%s", id), req, &pipeline); err != nil {
		return nil, fmt.Errorf("updating pipeline: %w", err)
	}
	return &pipeline, nil
}

// DeletePipeline deletes a pipeline
func (c *Client) DeletePipeline(ctx context.Context, id string) error {
	if err := c.Delete(ctx, fmt.Sprintf("/api/v1/pipelines/%s", id)); err != nil {
		return fmt.Errorf("deleting pipeline: %w", err)
	}
	return nil
}

// ValidatePipeline validates a pipeline definition
func (c *Client) ValidatePipeline(ctx context.Context, definition map[string]interface{}) (*ValidationResult, error) {
	req := ValidatePipelineRequest{Definition: definition}
	var result ValidationResult
	if err := c.Post(ctx, "/api/v1/pipelines/validate", req, &result); err != nil {
		return nil, fmt.Errorf("validating pipeline: %w", err)
	}
	return &result, nil
}
