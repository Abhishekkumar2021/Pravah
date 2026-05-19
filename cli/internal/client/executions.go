package client

import (
	"context"
	"fmt"
	"net/url"
)

// Execution represents a pipeline execution
type Execution struct {
	ID           string   `json:"id"`
	PipelineID   string   `json:"pipelineId"`
	PipelineName string   `json:"pipelineName,omitempty"`
	Status       string   `json:"status"`
	StartedAt    string   `json:"startedAt,omitempty"`
	CompletedAt  string   `json:"completedAt,omitempty"`
	Duration     int64    `json:"duration,omitempty"`
	Jobs         []Job    `json:"jobs,omitempty"`
	Parameters   []Param  `json:"parameters,omitempty"`
	TriggeredBy  string   `json:"triggeredBy,omitempty"`
	RetryOf      string   `json:"retryOf,omitempty"`
	CreatedAt    string   `json:"createdAt"`
}

// Job represents a stage/job within an execution
type Job struct {
	ID          string                 `json:"id"`
	Name        string                 `json:"name"`
	StageName   string                 `json:"stageName"`
	Status      string                 `json:"status"`
	StartedAt   string                 `json:"startedAt,omitempty"`
	CompletedAt string                 `json:"completedAt,omitempty"`
	Duration    int64                  `json:"duration,omitempty"`
	Logs        string                 `json:"logs,omitempty"`
	Output      map[string]interface{} `json:"output,omitempty"`
	Error       string                 `json:"error,omitempty"`
}

// Param represents an execution parameter
type Param struct {
	Name  string `json:"name"`
	Value string `json:"value"`
}

// ExecutionList represents a paginated list of executions
type ExecutionList struct {
	Content       []Execution `json:"content"`
	TotalElements int         `json:"totalElements"`
	TotalPages    int         `json:"totalPages"`
	Number        int         `json:"number"`
	Size          int         `json:"size"`
}

// CreateExecutionRequest represents a request to start an execution
type CreateExecutionRequest struct {
	PipelineID string  `json:"pipelineId"`
	Parameters []Param `json:"parameters,omitempty"`
}

// JobLogs represents logs for a specific job
type JobLogs struct {
	JobID string `json:"jobId"`
	Logs  string `json:"logs"`
}

// ListExecutions retrieves executions with optional filtering
func (c *Client) ListExecutions(ctx context.Context, opts *ListExecutionsOptions) (*ExecutionList, error) {
	path := "/api/v1/executions"
	if opts != nil {
		params := url.Values{}
		if opts.Page > 0 {
			params.Set("page", fmt.Sprintf("%d", opts.Page))
		}
		if opts.Size > 0 {
			params.Set("size", fmt.Sprintf("%d", opts.Size))
		}
		if opts.PipelineID != "" {
			params.Set("pipelineId", opts.PipelineID)
		}
		if opts.Status != "" {
			params.Set("status", opts.Status)
		}
		if len(params) > 0 {
			path += "?" + params.Encode()
		}
	}

	var result ExecutionList
	if err := c.Get(ctx, path, &result); err != nil {
		return nil, fmt.Errorf("listing executions: %w", err)
	}
	return &result, nil
}

// ListExecutionsOptions contains options for listing executions
type ListExecutionsOptions struct {
	Page       int
	Size       int
	PipelineID string
	Status     string
}

// GetExecution retrieves an execution by ID
func (c *Client) GetExecution(ctx context.Context, id string) (*Execution, error) {
	var execution Execution
	if err := c.Get(ctx, fmt.Sprintf("/api/v1/executions/%s", id), &execution); err != nil {
		return nil, fmt.Errorf("getting execution: %w", err)
	}
	return &execution, nil
}

// CreateExecution starts a new execution
func (c *Client) CreateExecution(ctx context.Context, req *CreateExecutionRequest) (*Execution, error) {
	var execution Execution
	if err := c.Post(ctx, "/api/v1/executions", req, &execution); err != nil {
		return nil, fmt.Errorf("creating execution: %w", err)
	}
	return &execution, nil
}

// CancelExecution cancels a running execution
func (c *Client) CancelExecution(ctx context.Context, id string) error {
	if err := c.Post(ctx, fmt.Sprintf("/api/v1/executions/%s/cancel", id), nil, nil); err != nil {
		return fmt.Errorf("canceling execution: %w", err)
	}
	return nil
}

// RetryExecution retries a failed execution
func (c *Client) RetryExecution(ctx context.Context, id string, fromStage string) (*Execution, error) {
	path := fmt.Sprintf("/api/v1/executions/%s/retry", id)
	if fromStage != "" {
		path += "?fromStage=" + url.QueryEscape(fromStage)
	}

	var execution Execution
	if err := c.Post(ctx, path, nil, &execution); err != nil {
		return nil, fmt.Errorf("retrying execution: %w", err)
	}
	return &execution, nil
}

// GetJobLogs retrieves logs for a specific job
func (c *Client) GetJobLogs(ctx context.Context, executionID, jobID string) (*JobLogs, error) {
	var logs JobLogs
	path := fmt.Sprintf("/api/v1/executions/%s/jobs/%s/logs", executionID, jobID)
	if err := c.Get(ctx, path, &logs); err != nil {
		return nil, fmt.Errorf("getting job logs: %w", err)
	}
	return &logs, nil
}

// WatchExecution polls for execution updates
func (c *Client) WatchExecution(ctx context.Context, id string, callback func(*Execution) bool) error {
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
			execution, err := c.GetExecution(ctx, id)
			if err != nil {
				return err
			}

			if !callback(execution) {
				return nil
			}

			switch execution.Status {
			case "COMPLETED", "FAILED", "CANCELLED":
				return nil
			}
		}
	}
}
