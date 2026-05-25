package output

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/fatih/color"
	"github.com/olekukonko/tablewriter"
	"gopkg.in/yaml.v3"
)

// Format represents an output format type
type Format string

const (
	FormatTable Format = "table"
	FormatJSON  Format = "json"
	FormatYAML  Format = "yaml"
)

// Formatter handles output formatting
type Formatter struct {
	format Format
	writer io.Writer
}

// NewFormatter creates a new output formatter
func NewFormatter(format string, writer io.Writer) *Formatter {
	if writer == nil {
		writer = os.Stdout
	}
	return &Formatter{
		format: Format(strings.ToLower(format)),
		writer: writer,
	}
}

// Print outputs data in the configured format
func (f *Formatter) Print(data interface{}) error {
	switch f.format {
	case FormatJSON:
		return f.printJSON(data)
	case FormatYAML:
		return f.printYAML(data)
	default:
		return f.printTable(data)
	}
}

func (f *Formatter) printJSON(data interface{}) error {
	enc := json.NewEncoder(f.writer)
	enc.SetIndent("", "  ")
	return enc.Encode(data)
}

func (f *Formatter) printYAML(data interface{}) error {
	enc := yaml.NewEncoder(f.writer)
	return enc.Encode(data)
}

func (f *Formatter) printTable(data interface{}) error {
	switch v := data.(type) {
	case [][]string:
		return f.renderTable(nil, v)
	case TableData:
		return f.renderTable(v.Headers, v.Rows)
	default:
		return f.printJSON(data)
	}
}

func (f *Formatter) renderTable(headers []string, rows [][]string) error {
	table := tablewriter.NewWriter(f.writer)
	if headers != nil {
		table.SetHeader(headers)
	}
	table.SetBorder(false)
	table.SetHeaderLine(false)
	table.SetColumnSeparator("  ")
	table.SetNoWhiteSpace(true)
	table.SetTablePadding("  ")
	table.AppendBulk(rows)
	table.Render()
	return nil
}

// TableData represents structured table data
type TableData struct {
	Headers []string
	Rows    [][]string
}

// NewTableData creates table data from headers and rows
func NewTableData(headers []string, rows [][]string) TableData {
	return TableData{Headers: headers, Rows: rows}
}

// Status colors
var (
	Green  = color.New(color.FgGreen).SprintFunc()
	Yellow = color.New(color.FgYellow).SprintFunc()
	Red    = color.New(color.FgRed).SprintFunc()
	Blue   = color.New(color.FgBlue).SprintFunc()
	Cyan   = color.New(color.FgCyan).SprintFunc()
	Bold   = color.New(color.Bold).SprintFunc()
)

// StatusColor returns a colored status string
func StatusColor(status string) string {
	switch strings.ToUpper(status) {
	case "RUNNING", "ACTIVE", "ENABLED":
		return Blue(status)
	case "COMPLETED", "SUCCESS", "SUCCEEDED", "PASSED":
		return Green(status)
	case "FAILED", "ERROR", "CANCELLED", "CANCELED":
		return Red(status)
	case "PENDING", "QUEUED", "WAITING":
		return Yellow(status)
	default:
		return status
	}
}

// Success prints a success message
func (f *Formatter) Success(format string, args ...interface{}) {
	fmt.Fprintf(f.writer, Green("✓ ")+format+"\n", args...)
}

// Error prints an error message
func (f *Formatter) Error(format string, args ...interface{}) {
	fmt.Fprintf(f.writer, Red("✗ ")+format+"\n", args...)
}

// Info prints an info message
func (f *Formatter) Info(format string, args ...interface{}) {
	fmt.Fprintf(f.writer, Blue("ℹ ")+format+"\n", args...)
}

// Warning prints a warning message
func (f *Formatter) Warning(format string, args ...interface{}) {
	fmt.Fprintf(f.writer, Yellow("⚠ ")+format+"\n", args...)
}

// Printf prints a formatted message
func (f *Formatter) Printf(format string, args ...interface{}) {
	fmt.Fprintf(f.writer, format, args...)
}

// Println prints a line
func (f *Formatter) Println(args ...interface{}) {
	fmt.Fprintln(f.writer, args...)
}

// IsTable returns true if output format is table
func (f *Formatter) IsTable() bool {
	return f.format == FormatTable
}

// IsJSON returns true if output format is JSON
func (f *Formatter) IsJSON() bool {
	return f.format == FormatJSON
}
