package cmd

import (
	"context"
	"fmt"
	"time"

	"github.com/briandowns/spinner"
	"github.com/spf13/cobra"

	"github.com/Abhishekkumar2021/Pravah/cli/internal/client"
	"github.com/Abhishekkumar2021/Pravah/cli/internal/output"
)

var runCmd = &cobra.Command{
	Use:     "run",
	Aliases: []string{"runs", "exec", "execution"},
	Short:   "Manage executions",
	Long:    "Commands for listing, monitoring, and managing workflow executions.",
}

var runListCmd = &cobra.Command{
	Use:   "list",
	Short: "List executions",
	Long: `List workflow executions with optional filtering.

Examples:
  # List all executions
  pravah run list

  # Filter by pipeline
  pravah run list --pipeline-id 550e8400-e29b-41d4-a716-446655440000

  # Filter by status
  pravah run list --status RUNNING`,
	RunE: runRunList,
}

var runStatusCmd = &cobra.Command{
	Use:   "status <id>",
	Short: "Get execution status",
	Long: `Get detailed status of a specific execution.

Examples:
  # Get execution status
  pravah run status 550e8400-e29b-41d4-a716-446655440000

  # Watch status in real-time
  pravah run status 550e8400... --watch`,
	Args: cobra.ExactArgs(1),
	RunE: runRunStatus,
}

var runLogsCmd = &cobra.Command{
	Use:   "logs <id>",
	Short: "Get execution logs",
	Long: `Get logs from a specific execution.

Examples:
  # Get all logs
  pravah run logs 550e8400-e29b-41d4-a716-446655440000

  # Get logs for specific stage
  pravah run logs 550e8400... --stage extract

  # Follow logs
  pravah run logs 550e8400... --follow`,
	Args: cobra.ExactArgs(1),
	RunE: runRunLogs,
}

var runCancelCmd = &cobra.Command{
	Use:   "cancel <id>",
	Short: "Cancel an execution",
	Long: `Cancel a running execution.

Examples:
  # Cancel execution
  pravah run cancel 550e8400-e29b-41d4-a716-446655440000

  # Cancel without confirmation
  pravah run cancel 550e8400... --force`,
	Args: cobra.ExactArgs(1),
	RunE: runRunCancel,
}

var runRetryCmd = &cobra.Command{
	Use:   "retry <id>",
	Short: "Retry a failed execution",
	Long: `Retry a failed execution, optionally from a specific stage.

Examples:
  # Retry from beginning
  pravah run retry 550e8400-e29b-41d4-a716-446655440000

  # Retry from specific stage
  pravah run retry 550e8400... --from-stage transform`,
	Args: cobra.ExactArgs(1),
	RunE: runRunRetry,
}

var (
	runPipelineID string
	runStatusFlag string
	runStage      string
	runWatch      bool
	runForce      bool
	runFromStage  string
	runPage       int
	runSize       int
)

func init() {
	runCmd.AddCommand(runListCmd)
	runCmd.AddCommand(runStatusCmd)
	runCmd.AddCommand(runLogsCmd)
	runCmd.AddCommand(runCancelCmd)
	runCmd.AddCommand(runRetryCmd)

	runListCmd.Flags().StringVar(&runPipelineID, "pipeline-id", "", "filter by pipeline ID")
	runListCmd.Flags().StringVar(&runStatusFlag, "status", "", "filter by status (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED)")
	runListCmd.Flags().IntVar(&runPage, "page", 0, "page number (0-indexed)")
	runListCmd.Flags().IntVar(&runSize, "size", 20, "page size")

	runStatusCmd.Flags().BoolVar(&runWatch, "watch", false, "watch status updates")

	runLogsCmd.Flags().StringVar(&runStage, "stage", "", "filter by stage name")
	runLogsCmd.Flags().BoolVar(&workflowFollow, "follow", false, "follow log output")

	runCancelCmd.Flags().BoolVar(&runForce, "force", false, "skip confirmation")

	runRetryCmd.Flags().StringVar(&runFromStage, "from-stage", "", "retry from specific stage")
}

func runRunList(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	opts := &client.ListExecutionsOptions{
		Page:       runPage,
		Size:       runSize,
		PipelineID: runPipelineID,
		Status:     runStatusFlag,
	}

	result, err := apiClient.ListExecutions(ctx, opts)
	if err != nil {
		return err
	}

	if out.IsJSON() {
		return out.Print(result)
	}

	if len(result.Content) == 0 {
		out.Info("No executions found")
		return nil
	}

	rows := make([][]string, len(result.Content))
	for i, e := range result.Content {
		duration := formatDuration(e.Duration)
		rows[i] = []string{
			e.ID[:8] + "...",
			truncate(e.PipelineName, 30),
			output.StatusColor(e.Status),
			duration,
			formatTime(e.StartedAt),
		}
	}

	if err := out.Print(output.NewTableData(
		[]string{"ID", "PIPELINE", "STATUS", "DURATION", "STARTED"},
		rows,
	)); err != nil {
		return err
	}

	if result.TotalPages > 1 {
		out.Printf("\nPage %d of %d (total: %d)\n", result.Number+1, result.TotalPages, result.TotalElements)
	}

	return nil
}

func runRunStatus(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	executionID := args[0]

	if runWatch {
		return watchExecutionStatus(apiClient, executionID)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	execution, err := apiClient.GetExecution(ctx, executionID)
	if err != nil {
		return err
	}

	if out.IsJSON() {
		return out.Print(execution)
	}

	printExecutionDetails(out, execution)
	return nil
}

func watchExecutionStatus(apiClient *client.Client, executionID string) error {
	out := GetOutput()

	s := spinner.New(spinner.CharSets[14], 100*time.Millisecond)
	s.Suffix = " Watching execution..."
	s.Start()

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Hour)
	defer cancel()

	var finalExec *client.Execution
	err := apiClient.WatchExecution(ctx, executionID, func(e *client.Execution) bool {
		finalExec = e
		s.Suffix = fmt.Sprintf(" %s [%s]", e.ID[:8], output.StatusColor(e.Status))

		completedJobs := 0
		for _, j := range e.Jobs {
			if j.Status == "COMPLETED" || j.Status == "FAILED" || j.Status == "SKIPPED" {
				completedJobs++
			}
		}

		if len(e.Jobs) > 0 {
			s.Suffix = fmt.Sprintf(" %s [%s] (%d/%d stages)",
				e.ID[:8], output.StatusColor(e.Status), completedJobs, len(e.Jobs))
		}

		time.Sleep(2 * time.Second)
		return true
	})

	s.Stop()

	if finalExec != nil {
		out.Println()
		printExecutionDetails(out, finalExec)
	}

	return err
}

func printExecutionDetails(out *output.Formatter, execution *client.Execution) {
	out.Printf("%s %s\n\n", output.Bold("Execution:"), execution.ID)
	out.Printf("  Pipeline:    %s (%s)\n", execution.PipelineName, execution.PipelineID[:8]+"...")
	out.Printf("  Status:      %s\n", output.StatusColor(execution.Status))
	out.Printf("  Started:     %s\n", formatTime(execution.StartedAt))

	if execution.CompletedAt != "" {
		out.Printf("  Completed:   %s\n", formatTime(execution.CompletedAt))
	}

	if execution.Duration > 0 {
		out.Printf("  Duration:    %s\n", formatDuration(execution.Duration))
	}

	if execution.TriggeredBy != "" {
		out.Printf("  Triggered:   %s\n", execution.TriggeredBy)
	}

	if execution.RetryOf != "" {
		out.Printf("  Retry of:    %s\n", execution.RetryOf)
	}

	if len(execution.Jobs) > 0 {
		out.Printf("\n%s\n", output.Bold("Stages:"))
		for _, job := range execution.Jobs {
			statusIcon := getStatusIcon(job.Status)
			duration := ""
			if job.Duration > 0 {
				duration = fmt.Sprintf(" (%s)", formatDuration(job.Duration))
			}
			out.Printf("  %s %s%s\n", statusIcon, job.StageName, duration)
		}
	}
}

func runRunLogs(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	executionID := args[0]

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	execution, err := apiClient.GetExecution(ctx, executionID)
	if err != nil {
		return err
	}

	if out.IsJSON() {
		logs := make(map[string]string)
		for _, job := range execution.Jobs {
			if runStage != "" && job.StageName != runStage {
				continue
			}
			if job.Logs != "" {
				logs[job.StageName] = job.Logs
			}
		}
		return out.Print(logs)
	}

	for _, job := range execution.Jobs {
		if runStage != "" && job.StageName != runStage {
			continue
		}

		out.Printf("=== %s [%s] ===\n", output.Bold(job.StageName), output.StatusColor(job.Status))

		if job.Logs != "" {
			out.Printf("%s\n", job.Logs)
		} else {
			out.Printf("(no logs)\n")
		}

		if job.Error != "" {
			out.Error("Error: %s\n", job.Error)
		}

		out.Printf("\n")
	}

	return nil
}

func runRunCancel(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	executionID := args[0]

	if !runForce {
		out.Printf("Are you sure you want to cancel execution %s? [y/N]: ", executionID[:8])
		var response string
		fmt.Scanln(&response)
		if response != "y" && response != "Y" {
			out.Info("Cancelled")
			return nil
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := apiClient.CancelExecution(ctx, executionID); err != nil {
		return err
	}

	out.Success("Execution %s cancelled", executionID[:8])
	return nil
}

func runRunRetry(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()
	executionID := args[0]

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	execution, err := apiClient.RetryExecution(ctx, executionID, runFromStage)
	if err != nil {
		return err
	}

	if out.IsJSON() {
		return out.Print(execution)
	}

	if runFromStage != "" {
		out.Success("Retrying execution from stage '%s' (new ID: %s)", runFromStage, execution.ID)
	} else {
		out.Success("Retrying execution (new ID: %s)", execution.ID)
	}

	out.Info("Run 'pravah run status %s' to monitor progress", execution.ID)
	return nil
}

func getStatusIcon(status string) string {
	switch status {
	case "COMPLETED":
		return output.Green("✓")
	case "FAILED":
		return output.Red("✗")
	case "RUNNING":
		return output.Blue("▶")
	case "PENDING", "QUEUED":
		return output.Yellow("○")
	case "CANCELLED", "SKIPPED":
		return output.Yellow("⊘")
	default:
		return "?"
	}
}

func formatDuration(ms int64) string {
	if ms == 0 {
		return "-"
	}

	d := time.Duration(ms) * time.Millisecond

	if d < time.Second {
		return fmt.Sprintf("%dms", ms)
	}
	if d < time.Minute {
		return fmt.Sprintf("%.1fs", d.Seconds())
	}
	if d < time.Hour {
		return fmt.Sprintf("%.1fm", d.Minutes())
	}
	return fmt.Sprintf("%.1fh", d.Hours())
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen-3] + "..."
}
