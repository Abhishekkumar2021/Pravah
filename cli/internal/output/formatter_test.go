package output

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"
)

func TestFormatter_PrintJSON(t *testing.T) {
	var buf bytes.Buffer
	f := NewFormatter("json", &buf)

	data := map[string]interface{}{
		"name":   "test",
		"status": "success",
	}

	if err := f.Print(data); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var result map[string]interface{}
	if err := json.Unmarshal(buf.Bytes(), &result); err != nil {
		t.Fatalf("output is not valid JSON: %v", err)
	}

	if result["name"] != "test" {
		t.Errorf("expected name 'test', got %v", result["name"])
	}
}

func TestFormatter_PrintTable(t *testing.T) {
	var buf bytes.Buffer
	f := NewFormatter("table", &buf)

	data := NewTableData(
		[]string{"ID", "NAME", "STATUS"},
		[][]string{
			{"1", "workflow-a", "RUNNING"},
			{"2", "workflow-b", "COMPLETED"},
		},
	)

	if err := f.Print(data); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	output := buf.String()
	if !strings.Contains(output, "ID") {
		t.Error("expected output to contain header 'ID'")
	}
	if !strings.Contains(output, "workflow-a") {
		t.Error("expected output to contain 'workflow-a'")
	}
}

func TestStatusColor(t *testing.T) {
	tests := []struct {
		status   string
		contains string
	}{
		{"RUNNING", "RUNNING"},
		{"COMPLETED", "COMPLETED"},
		{"FAILED", "FAILED"},
		{"PENDING", "PENDING"},
		{"UNKNOWN", "UNKNOWN"},
	}

	for _, tt := range tests {
		t.Run(tt.status, func(t *testing.T) {
			result := StatusColor(tt.status)
			if !strings.Contains(result, tt.contains) {
				t.Errorf("StatusColor(%q) = %q, expected to contain %q", tt.status, result, tt.contains)
			}
		})
	}
}

func TestFormatter_IsTable(t *testing.T) {
	tests := []struct {
		format  string
		isTable bool
	}{
		{"table", true},
		{"json", false},
		{"yaml", false},
	}

	for _, tt := range tests {
		t.Run(tt.format, func(t *testing.T) {
			f := NewFormatter(tt.format, nil)
			if f.IsTable() != tt.isTable {
				t.Errorf("IsTable() for format %q = %v, expected %v", tt.format, f.IsTable(), tt.isTable)
			}
		})
	}
}

func TestFormatter_Messages(t *testing.T) {
	var buf bytes.Buffer
	f := NewFormatter("table", &buf)

	f.Success("operation %s", "completed")
	f.Error("operation %s", "failed")
	f.Info("info %s", "message")
	f.Warning("warning %s", "message")

	output := buf.String()

	if !strings.Contains(output, "completed") {
		t.Error("expected success message")
	}
	if !strings.Contains(output, "failed") {
		t.Error("expected error message")
	}
	if !strings.Contains(output, "info message") {
		t.Error("expected info message")
	}
	if !strings.Contains(output, "warning message") {
		t.Error("expected warning message")
	}
}
