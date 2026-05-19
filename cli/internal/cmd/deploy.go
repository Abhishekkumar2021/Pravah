package cmd

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"gopkg.in/yaml.v3"

	"github.com/Abhishekkumar2021/Pravah/cli/internal/client"
	"github.com/Abhishekkumar2021/Pravah/cli/internal/output"
)

var deployCmd = &cobra.Command{
	Use:   "deploy",
	Short: "Deploy workflows",
	Long: `Deploy workflow definitions to Pravah.

This command will create new workflows or update existing ones based on
the workflow name. Use --dry-run to preview changes without applying them.

Examples:
  # Deploy a single file
  pravah deploy -f workflow.yaml

  # Deploy all YAML files in a directory
  pravah deploy -f workflows/ --recursive

  # Preview changes without applying
  pravah deploy -f workflow.yaml --dry-run`,
	RunE: runDeploy,
}

var diffCmd = &cobra.Command{
	Use:   "diff",
	Short: "Show workflow differences",
	Long: `Compare local workflow definitions with deployed versions.

Examples:
  # Show diff for a single file
  pravah diff -f workflow.yaml

  # Show diff for all files in a directory
  pravah diff -f workflows/ --recursive`,
	RunE: runDiff,
}

var (
	deployFile      string
	deployRecursive bool
	deployDryRun    bool
)

func init() {
	rootCmd.AddCommand(diffCmd)

	deployCmd.Flags().StringVarP(&deployFile, "file", "f", "", "workflow file or directory (required)")
	deployCmd.Flags().BoolVar(&deployRecursive, "recursive", false, "recursively process directories")
	deployCmd.Flags().BoolVar(&deployDryRun, "dry-run", false, "preview changes without applying")
	deployCmd.MarkFlagRequired("file")

	diffCmd.Flags().StringVarP(&deployFile, "file", "f", "", "workflow file or directory (required)")
	diffCmd.Flags().BoolVar(&deployRecursive, "recursive", false, "recursively process directories")
	diffCmd.MarkFlagRequired("file")
}

// DeployResult represents the result of a deployment
type DeployResult struct {
	File      string `json:"file"`
	Name      string `json:"name"`
	ID        string `json:"id,omitempty"`
	Action    string `json:"action"`
	Status    string `json:"status"`
	Error     string `json:"error,omitempty"`
	IsNew     bool   `json:"isNew"`
	HasChange bool   `json:"hasChange"`
}

func runDeploy(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()

	files, err := collectWorkflowFiles(deployFile, deployRecursive)
	if err != nil {
		return err
	}

	if len(files) == 0 {
		out.Warning("No workflow files found")
		os.Exit(ExitNoChanges)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()

	existingPipelines, err := loadExistingPipelines(ctx, apiClient)
	if err != nil {
		return fmt.Errorf("loading existing pipelines: %w", err)
	}

	results := make([]DeployResult, 0, len(files))
	hasErrors := false
	hasChanges := false

	for _, file := range files {
		result := deployWorkflowFile(ctx, apiClient, file, existingPipelines, deployDryRun)
		results = append(results, result)

		if result.Status == "error" {
			hasErrors = true
		}
		if result.HasChange {
			hasChanges = true
		}
	}

	if out.IsJSON() {
		return out.Print(results)
	}

	out.Printf("\n%s\n\n", output.Bold("Deployment Summary"))

	for _, r := range results {
		icon := output.Green("✓")
		if r.Status == "error" {
			icon = output.Red("✗")
		} else if r.Status == "skipped" {
			icon = output.Yellow("○")
		}

		action := r.Action
		if deployDryRun {
			action = "would " + action
		}

		out.Printf("%s %s (%s) - %s", icon, r.Name, r.File, action)
		if r.Error != "" {
			out.Printf(" - %s", output.Red(r.Error))
		}
		out.Printf("\n")
	}

	created := 0
	updated := 0
	unchanged := 0
	errors := 0

	for _, r := range results {
		switch r.Action {
		case "create", "would create":
			created++
		case "update", "would update":
			updated++
		case "unchanged":
			unchanged++
		}
		if r.Status == "error" {
			errors++
		}
	}

	out.Printf("\n")
	if deployDryRun {
		out.Info("Dry run: %d to create, %d to update, %d unchanged",
			created, updated, unchanged)
	} else {
		out.Info("%d created, %d updated, %d unchanged, %d errors",
			created, updated, unchanged, errors)
	}

	if hasErrors {
		os.Exit(ExitError)
	}
	if !hasChanges {
		os.Exit(ExitNoChanges)
	}

	return nil
}

func runDiff(cmd *cobra.Command, args []string) error {
	apiClient, err := getClient()
	if err != nil {
		return err
	}

	out := GetOutput()

	files, err := collectWorkflowFiles(deployFile, deployRecursive)
	if err != nil {
		return err
	}

	if len(files) == 0 {
		out.Warning("No workflow files found")
		return nil
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()

	existingPipelines, err := loadExistingPipelines(ctx, apiClient)
	if err != nil {
		return fmt.Errorf("loading existing pipelines: %w", err)
	}

	for _, file := range files {
		definition, err := loadWorkflowFile(file)
		if err != nil {
			out.Error("Error loading %s: %v", file, err)
			continue
		}

		name, _ := definition["name"].(string)
		if name == "" {
			out.Error("Workflow in %s has no name", file)
			continue
		}

		existing, found := existingPipelines[name]

		out.Printf("\n%s %s\n", output.Bold("File:"), file)
		out.Printf("%s %s\n", output.Bold("Name:"), name)

		if !found {
			out.Printf("%s %s\n", output.Bold("Status:"), output.Green("NEW"))
			out.Printf("This workflow will be created.\n")
			continue
		}

		out.Printf("%s %s (version %d)\n", output.Bold("Status:"), output.Blue("EXISTS"), existing.Version)
		out.Printf("%s %s\n", output.Bold("ID:"), existing.ID)

		localYAML, _ := yaml.Marshal(definition)
		remoteYAML, _ := yaml.Marshal(existing.Definition)

		if string(localYAML) == string(remoteYAML) {
			out.Printf("%s\n", output.Green("No changes detected"))
		} else {
			out.Printf("%s\n", output.Yellow("Changes detected (definition differs)"))
		}
	}

	return nil
}

func collectWorkflowFiles(path string, recursive bool) ([]string, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, fmt.Errorf("accessing path: %w", err)
	}

	if !info.IsDir() {
		return []string{path}, nil
	}

	var files []string
	walkFn := func(p string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		if info.IsDir() {
			if !recursive && p != path {
				return filepath.SkipDir
			}
			return nil
		}

		ext := strings.ToLower(filepath.Ext(p))
		if ext == ".yaml" || ext == ".yml" {
			files = append(files, p)
		}

		return nil
	}

	if err := filepath.Walk(path, walkFn); err != nil {
		return nil, fmt.Errorf("scanning directory: %w", err)
	}

	return files, nil
}

func loadExistingPipelines(ctx context.Context, apiClient *client.Client) (map[string]*client.Pipeline, error) {
	result := make(map[string]*client.Pipeline)

	page := 0
	for {
		list, err := apiClient.ListPipelines(ctx, &client.ListPipelinesOptions{
			Page: page,
			Size: 100,
		})
		if err != nil {
			return nil, err
		}

		for i := range list.Content {
			p := &list.Content[i]
			result[p.Name] = p
		}

		if page >= list.TotalPages-1 {
			break
		}
		page++
	}

	return result, nil
}

func deployWorkflowFile(ctx context.Context, apiClient *client.Client, file string, existing map[string]*client.Pipeline, dryRun bool) DeployResult {
	result := DeployResult{
		File:   filepath.Base(file),
		Status: "success",
	}

	definition, err := loadWorkflowFile(file)
	if err != nil {
		result.Status = "error"
		result.Error = err.Error()
		result.Action = "failed to load"
		return result
	}

	name, _ := definition["name"].(string)
	if name == "" {
		result.Status = "error"
		result.Error = "workflow has no name"
		result.Action = "failed to load"
		return result
	}
	result.Name = name

	description, _ := definition["description"].(string)

	existingPipeline, found := existing[name]

	if !found {
		result.IsNew = true
		result.HasChange = true
		result.Action = "create"

		if dryRun {
			result.Action = "would create"
			return result
		}

		req := &client.CreatePipelineRequest{
			Name:        name,
			Description: description,
			Definition:  definition,
		}

		pipeline, err := apiClient.CreatePipeline(ctx, req)
		if err != nil {
			result.Status = "error"
			result.Error = err.Error()
			result.Action = "create failed"
			return result
		}

		result.ID = pipeline.ID
		return result
	}

	result.ID = existingPipeline.ID

	localYAML, _ := yaml.Marshal(definition)
	remoteYAML, _ := yaml.Marshal(existingPipeline.Definition)

	if string(localYAML) == string(remoteYAML) {
		result.Action = "unchanged"
		result.Status = "skipped"
		return result
	}

	result.HasChange = true
	result.Action = "update"

	if dryRun {
		result.Action = "would update"
		return result
	}

	req := &client.UpdatePipelineRequest{
		Name:        name,
		Description: description,
		Definition:  definition,
	}

	_, err = apiClient.UpdatePipeline(ctx, existingPipeline.ID, req)
	if err != nil {
		result.Status = "error"
		result.Error = err.Error()
		result.Action = "update failed"
		return result
	}

	return result
}
