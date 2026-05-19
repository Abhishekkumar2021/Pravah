package client

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestClient_Get(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Errorf("expected GET, got %s", r.Method)
		}
		if r.URL.Path != "/api/v1/test" {
			t.Errorf("expected /api/v1/test, got %s", r.URL.Path)
		}

		if auth := r.Header.Get("Authorization"); auth != "Bearer test-token" {
			t.Errorf("expected 'Bearer test-token', got %q", auth)
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{"message": "success"})
	}))
	defer server.Close()

	client := NewClient(server.URL, WithToken("test-token"))

	var result map[string]string
	err := client.Get(context.Background(), "/api/v1/test", &result)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result["message"] != "success" {
		t.Errorf("expected message 'success', got %q", result["message"])
	}
}

func TestClient_Post(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}

		var body map[string]string
		json.NewDecoder(r.Body).Decode(&body)
		if body["name"] != "test" {
			t.Errorf("expected name 'test', got %q", body["name"])
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{"id": "123", "name": body["name"]})
	}))
	defer server.Close()

	client := NewClient(server.URL, WithToken("test-token"))

	var result map[string]string
	err := client.Post(context.Background(), "/api/v1/test", map[string]string{"name": "test"}, &result)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result["id"] != "123" {
		t.Errorf("expected id '123', got %q", result["id"])
	}
}

func TestClient_ErrorHandling(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]string{
			"message": "validation failed",
			"code":    "VALIDATION_ERROR",
		})
	}))
	defer server.Close()

	client := NewClient(server.URL)

	var result map[string]string
	err := client.Get(context.Background(), "/api/v1/test", &result)
	if err == nil {
		t.Fatal("expected error")
	}

	apiErr, ok := err.(*APIError)
	if !ok {
		t.Fatalf("expected APIError, got %T", err)
	}

	if apiErr.StatusCode != 400 {
		t.Errorf("expected status 400, got %d", apiErr.StatusCode)
	}

	if apiErr.Message != "validation failed" {
		t.Errorf("expected message 'validation failed', got %q", apiErr.Message)
	}
}

func TestClient_Retry(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts < 3 {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	}))
	defer server.Close()

	client := NewClient(server.URL, WithTimeout(5*time.Second))

	var result map[string]string
	err := client.Get(context.Background(), "/api/v1/test", &result)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if attempts != 3 {
		t.Errorf("expected 3 attempts, got %d", attempts)
	}
}

func TestAPIError_Methods(t *testing.T) {
	tests := []struct {
		name         string
		err          *APIError
		isNotFound   bool
		isUnauth     bool
		isForbidden  bool
		errorContain string
	}{
		{
			name:        "not found",
			err:         &APIError{StatusCode: 404, Message: "not found"},
			isNotFound:  true,
			isUnauth:    false,
			isForbidden: false,
		},
		{
			name:        "unauthorized",
			err:         &APIError{StatusCode: 401, Message: "unauthorized"},
			isNotFound:  false,
			isUnauth:    true,
			isForbidden: false,
		},
		{
			name:         "forbidden with code",
			err:          &APIError{StatusCode: 403, Message: "access denied", Code: "FORBIDDEN"},
			isNotFound:   false,
			isUnauth:     false,
			isForbidden:  true,
			errorContain: "FORBIDDEN",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.err.IsNotFound() != tt.isNotFound {
				t.Errorf("IsNotFound() = %v, expected %v", tt.err.IsNotFound(), tt.isNotFound)
			}
			if tt.err.IsUnauthorized() != tt.isUnauth {
				t.Errorf("IsUnauthorized() = %v, expected %v", tt.err.IsUnauthorized(), tt.isUnauth)
			}
			if tt.err.IsForbidden() != tt.isForbidden {
				t.Errorf("IsForbidden() = %v, expected %v", tt.err.IsForbidden(), tt.isForbidden)
			}
		})
	}
}

func TestClient_ProjectIDHeader(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		projectID := r.Header.Get("X-Project-ID")
		if projectID != "proj-123" {
			t.Errorf("expected project ID 'proj-123', got %q", projectID)
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	client := NewClient(server.URL, WithToken("token"), WithProjectID("proj-123"))
	client.Get(context.Background(), "/api/v1/test", nil)
}
