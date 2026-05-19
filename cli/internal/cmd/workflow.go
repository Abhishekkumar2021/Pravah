package cmd

import (
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/briandowns/spinner"
	"github.com/spf13/cobra"
	"gopkg.in/yaml.v3"

	"github.com/Abhishekkumar2021/Pravah/cli/internal/client"
	"github.com/Abhishekkumar2021/Pravah/cli/internal/keyring"
	"github.com/Abhishekkumar2021/Pravah/cli/internal/output"
)

var workflowCmd = &cobra.Command{
	Use:     "workflow",
	Aliases: []string{"wf", "workflows"},
	Short:   "Manage workflows",
	Long:    "Commands for listing, creating, running, and managing workflows.",
}

var workflowListCmd = &cobra.Command{
	Use:   "list",
	Short: "List workflows",
	Long: `List all workflows in the current project.

Examples:
  # List all workflows
  pravah workflow list

  # Filter by status
  pravah workflow list --status ACTIVE

  # Search by name
  pravah workflow list --search "etl"`,
	RunE: runWorkflowList,
}

var workflowGetCmd = &cobra.Command{
	Use:   "get <id>",
	Short: "Get workflow details",
	Long: `Get detailed information about a specific workflow.

Examples:
  # Get workflow by ID
  pravah workflow get 550e8400-e29b-41d4-a716-446655440000

  # Output as YAML
  pravah workflow get 550e8400... -o yaml`,
	Args: cobra.ExactArgs(1),
	RunE: runWorkflowGet,
}

var workflowCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a workflow",
	Long: `Create a new workflow from a YAML file.

Examples:
  # Create from file
  pravah workflow create -f workflow.yaml`,
	RunE: runWorkflowCreate,
}

var workflowRunCmd = &cobra.Command{
	Use:   "run <id>",
	Short: "Run a workflow",
	Long: `Start a new execution of a workflow.

Examples:
  # Run workflow
  pravah workflow run 550e8400-e29b-41d4-a716-446655440000

  # With parameters
  pravah workflow run 550e8400... --param env=prod --param date=2024-01-01

  # Wait for completion
  pravah workflow run 550e8400... --wait`,
	Args: cobra.ExactArgs(1),
	RunE: runWorkflowRun,
}

var workflowLogsCmd = &cobra.Command{
	Use:   "logs <workflow-id>",
	Short: "Get workflow execution logs",
	Long: `Get logs from the most recent execution of a workflow.

Examples:
  # Get latest run logs
  pravah workflow logs 550e8400-e29b-41d4-a716-446655440000

  # Follow logs
  pravah workflow logs 550e8400... --follow`,
	Args: cobra.ExactArgs(1),
	RunE: runWorkflowLogs,
}

var workflowValidateCmd = &cobra.Command{
	Use:   "validate",
	Short: "Validate a workflow definition",
	Long: `Validate a workflow definition file without creating it.

Examples:
  # Validate file
  pravah workflow validate -f workflow.yaml`,
	RunE: runWorkflowValidate,
}

var (
	workflowFile   string
	workflowStatus string
	workflowSearch string
	workflowParams []string
	workflowWait   bool
	workflowFollow bool
	workflowPage   int
	workflowSize   int
)

func init() {
	workflowCmd.AddCommand(workflowListCmd)
	workflowCmd.AddCommand(workflowGetCmd)
	workflowCmd.AddCommand(workflowCreateCmd)
	workflowCmd.AddCommand(workflowRunCmd)
	workflowCmd.AddCommand(workflowLogsCmd)
	workflowCmd.AddCommand(workflowValidateCmd)

	workflowListCmd.Flags().StringVar(&workflowStatus, "status", "", "filter by status")
	workflowListCmd.Flags().StringVar(&workflowSearch, "search", "", "search by name")
	workflowListCmd.Flags().IntVar(&workflowPage, "page", 0, "page number (0-indexed)")
	workflowListCmd.Flags().IntVar(&workflowSize, "size", 20, "page size")

	workflowCreateCmd.Flags().StringVarP(&workflowFile, "file", "f", "", "workflow definition file (required)")
	cobra.CheckErr(workflowCreateCmd.MarkFlagRequired("file"))

	workflowRunCmd.Flags().StringArrayVar(&workflowParams, "param", nil, "parameter in key=value format")
	workflowRunCmd.Flags().BoolVar(&workflowWait, "wait", false, "wait for execution to complete")

	workflowLogsCmd.Flags().BoolVar(&workflowFollow, "follow", false, "follow log output")

	workflowValidateCmd.Flags().StringVarP(&workflowFile, "file", "f", "", "workflow definition file (required)")
	cobra.CheckErr(workflowValidateCmd.MarkFlagRequired("file"))
}

func getClient() (*client.Client, error) {
	cfg := GetConfig()
	store := keyring.NewStore(cfg.CurrentProfile)

	token, err := store.GetToken()
	if err != nil {
		return nil, fmt.Errorf("not logged in, run 'pravah login' first")
	}

	return client.NewClient(cfg.APIURL(), client.WithToken(token), client.WithProjectID(cfg.ProjectID())), nil
}

func runWorkflowList(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	opts := &client.ListPipelinesOptions{
		Page:   workflowPage,
		Size:   workflowSize,
		Status: workflowStatus,
		Search: workflowSearch,
	}

	result, err := apiClient.ListPipelines(ctx, opts)
	if err != nil {
		return err
	}

	if out.IsJSON() {
		return out.Print(result)
	}

	if len(result.Content) == 0 {
		out.Info("No workflows found")
		return nil
	}

	rows := make([][]string, len(result.Content))
	for i, p := range result.Content {
		rows[i] = []string{
			p.ID,
			p.Name,
			output.StatusColor(p.Status),
			fmt.Sprintf("v%d", p.Version),
			formatTime(p.UpdatedAt),
		}
	}

	if err := out.Print(output.NewTableData(
		[]string{"ID", "NAME", "STATUS", "VERSION", "UPDATED"},
		rows,
	)); err != nil {
		return err
	}

	if result.TotalPages > 1 {
		out.Printf("\nPage %d of %d (total: %d)\n", result.Number+1, result.TotalPages, result.TotalElements)
	}

	return nil
}

func runWorkflowGet(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	pipeline, err := apiClient.GetPipeline(ctx, args[0])
	if err != nil {
		return err
	}

	if out.IsJSON() || !out.IsTable() {
		return out.Print(pipeline)
	}

	out.Printf("%s %s\n\n", output.Bold("Workflow:"), pipeline.Name)
	out.Printf("  ID:          %s\n", pipeline.ID)
	out.Printf("  Status:      %s\n", output.StatusColor(pipeline.Status))
	out.Printf("  Version:     v%d\n", pipeline.Version)
	if pipeline.Description != "" {
		out.Printf("  Description: %s\n", pipeline.Description)
	}
	out.Printf("  Created:     %s\n", formatTime(pipeline.CreatedAt))
	out.Printf("  Updated:     %s\n", formatTime(pipeline.UpdatedAt))

	return nil
}

func runWorkflowCreate(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()

	definition, err := loadWorkflowFile(workflowFile)
	if err != nil {
		return err
	}

	name, _ := definition["name"].(string)
	description, _ := definition["description"].(string)

	req := &client.CreatePipelineRequest{
		Name:        name,
		Description: description,
		Definition:  definition,
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	pipeline, err := apiClient.CreatePipeline(ctx, req)
	if err != nil {
		return err
	}

	if out.IsJSON() {
		return out.Print(pipeline)
	}

	out.Success("Created workflow %s (ID: %s)", pipeline.Name, pipeline.ID)
	return nil
}

func runWorkflowRun(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	workflowID := args[0]

	params := make([]client.Param, 0)
	for _, p := range workflowParams {
		parts := strings.SplitN(p, "=", 2)
		if len(parts) != 2 {
			return fmt.Errorf("invalid parameter format: %s (expected key=value)", p)
		}
		params = append(params, client.Param{Name: parts[0], Value: parts[1]})
	}

	req := &client.CreateExecutionRequest{
		PipelineID: workflowID,
		Parameters: params,
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	execution, err := apiClient.CreateExecution(ctx, req)
	if err != nil {
		return err
	}

	if !workflowWait {
		if out.IsJSON() {
			return out.Print(execution)
		}
		out.Success("Started execution %s", execution.ID)
		out.Info("Run 'pravah run status %s' to check progress", execution.ID)
		return nil
	}

	s := spinner.New(spinner.CharSets[14], 100*time.Millisecond)
	s.Suffix = fmt.Sprintf(" Running %s...", execution.ID[:8])
	s.Start()

	watchCtx, watchCancel := context.WithTimeout(context.Background(), 2*time.Hour)
	defer watchCancel()

	err = apiClient.WatchExecution(watchCtx, execution.ID, func(e *client.Execution) bool {
		s.Suffix = fmt.Sprintf(" %s - %s", e.ID[:8], output.StatusColor(e.Status))
		time.Sleep(2 * time.Second)
		return true
	})

	s.Stop()

	final, _ := apiClient.GetExecution(ctx, execution.ID)
	if final != nil {
		execution = final
	}

	if out.IsJSON() {
		return out.Print(execution)
	}

	switch execution.Status {
	case "COMPLETED":
		out.Success("Execution completed successfully")
	case "FAILED":
		out.Error("Execution failed")
		return fmt.Errorf("execution failed")
	case "CANCELLED":
		out.Warning("Execution was cancelled")
	default:
		out.Info("Execution status: %s", output.StatusColor(execution.Status))
	}

	return err
}

func runWorkflowLogs(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	execList, err := apiClient.ListExecutions(ctx, &client.ListExecutionsOptions{
		PipelineID: args[0],
		Size:       1,
	})
	if err != nil {
		return err
	}

	if len(execList.Content) == 0 {
		out.Info("No executions found for this workflow")
		return nil
	}

	execution := execList.Content[0]
	out.Printf("Execution: %s (%s)\n\n", execution.ID, output.StatusColor(execution.Status))

	for _, job := range execution.Jobs {
		out.Printf("--- %s [%s] ---\n", output.Bold(job.StageName), output.StatusColor(job.Status))

		if job.Logs != "" {
			out.Printf("%s\n", job.Logs)
		}

		if job.Error != "" {
			out.Error("Error: %s", job.Error)
		}
		out.Printf("\n")
	}

	return nil
}

func runWorkflowValidate(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()

	definition, err := loadWorkflowFile(workflowFile)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	result, err := apiClient.ValidatePipeline(ctx, definition)
	if err != nil {
		return err
	}

	if out.IsJSON() {
		return out.Print(result)
	}

	if result.Valid {
		out.Success("Workflow definition is valid")
	} else {
		out.Error("Workflow definition is invalid")
	}

	for _, e := range result.Errors {
		out.Error("  %s", e)
	}

	for _, w := range result.Warnings {
		out.Warning("  %s", w)
	}

	if !result.Valid {
		os.Exit(ExitValidation)
	}

	return nil
}

func loadWorkflowFile(path string) (map[string]interface{}, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("reading file: %w", err)
	}

	var definition map[string]interface{}
	if err := yaml.Unmarshal(data, &definition); err != nil {
		return nil, fmt.Errorf("parsing YAML: %w", err)
	}

	return definition, nil
}

func formatTime(ts string) string {
	t, err := time.Parse(time.RFC3339, ts)
	if err != nil {
		return ts
	}
	return t.Local().Format("2006-01-02 15:04")
}
