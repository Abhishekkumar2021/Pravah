package cmd

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/Abhishekkumar2021/Pravah/cli/internal/config"
	"github.com/Abhishekkumar2021/Pravah/cli/internal/output"
)

var configCmd = &cobra.Command{
	Use:   "config",
	Short: "Manage CLI configuration",
	Long:  "Commands for managing CLI configuration and profiles.",
}

var configGetCmd = &cobra.Command{
	Use:   "get [key]",
	Short: "Get configuration value",
	Long: `Get a configuration value or show all configuration.

Examples:
  # Show all configuration
  pravah config get

  # Get specific value
  pravah config get api-url`,
	Args: cobra.MaximumNArgs(1),
	RunE: runConfigGet,
}

var configSetCmd = &cobra.Command{
	Use:   "set <key> <value>",
	Short: "Set configuration value",
	Long: `Set a configuration value for the current profile.

Examples:
  # Set API URL
  pravah config set api-url https://api.pravah.io

  # Set project ID
  pravah config set project-id 550e8400-e29b-41d4-a716-446655440000`,
	Args: cobra.ExactArgs(2),
	RunE: runConfigSet,
}

var configProfileCmd = &cobra.Command{
	Use:   "profile",
	Short: "Manage configuration profiles",
	Long:  "Commands for managing configuration profiles.",
}

var configProfileListCmd = &cobra.Command{
	Use:   "list",
	Short: "List profiles",
	Long:  "List all configuration profiles.",
	RunE:  runConfigProfileList,
}

var configProfileUseCmd = &cobra.Command{
	Use:   "use <name>",
	Short: "Switch profile",
	Long: `Switch to a different configuration profile.

Examples:
  pravah config profile use production`,
	Args: cobra.ExactArgs(1),
	RunE: runConfigProfileUse,
}

var configProfileCreateCmd = &cobra.Command{
	Use:   "create <name>",
	Short: "Create a new profile",
	Long: `Create a new configuration profile.

Examples:
  # Create a production profile
  pravah config profile create production --api-url https://api.pravah.io`,
	Args: cobra.ExactArgs(1),
	RunE: runConfigProfileCreate,
}

var configProfileDeleteCmd = &cobra.Command{
	Use:   "delete <name>",
	Short: "Delete a profile",
	Long: `Delete a configuration profile.

Examples:
  pravah config profile delete staging`,
	Args: cobra.ExactArgs(1),
	RunE: runConfigProfileDelete,
}

var (
	profileAPIURL    string
	profileProjectID string
)

func init() {
	configCmd.AddCommand(configGetCmd)
	configCmd.AddCommand(configSetCmd)
	configCmd.AddCommand(configProfileCmd)

	configProfileCmd.AddCommand(configProfileListCmd)
	configProfileCmd.AddCommand(configProfileUseCmd)
	configProfileCmd.AddCommand(configProfileCreateCmd)
	configProfileCmd.AddCommand(configProfileDeleteCmd)

	configProfileCreateCmd.Flags().StringVar(&profileAPIURL, "api-url", "", "API URL for the profile")
	configProfileCreateCmd.Flags().StringVar(&profileProjectID, "project-id", "", "project ID for the profile")
}

func runConfigGet(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()

	if len(args) == 0 {
		if out.IsJSON() {
			return out.Print(map[string]interface{}{
				"current-profile": cfg.CurrentProfile,
				"api-url":         cfg.APIURL(),
				"project-id":      cfg.ProjectID(),
			})
		}

		out.Printf("Current Profile: %s\n", output.Bold(cfg.CurrentProfile))
		out.Printf("API URL:         %s\n", cfg.APIURL())
		if cfg.ProjectID() != "" {
			out.Printf("Project ID:      %s\n", cfg.ProjectID())
		}
		return nil
	}

	key := args[0]
	var value string

	switch key {
	case "api-url":
		value = cfg.APIURL()
	case "project-id":
		value = cfg.ProjectID()
	case "current-profile":
		value = cfg.CurrentProfile
	default:
		return fmt.Errorf("unknown configuration key: %s", key)
	}

	if out.IsJSON() {
		return out.Print(map[string]string{key: value})
	}

	out.Println(value)
	return nil
}

func runConfigSet(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()

	key := args[0]
	value := args[1]

	profile := cfg.GetProfile()

	switch key {
	case "api-url":
		profile.APIURL = value
	case "project-id":
		profile.ProjectID = value
	default:
		return fmt.Errorf("unknown configuration key: %s", key)
	}

	cfg.SetProfile(cfg.CurrentProfile, profile)

	if err := cfg.Save(); err != nil {
		return fmt.Errorf("saving configuration: %w", err)
	}

	out.Success("Set %s = %s", key, value)
	return nil
}

func runConfigProfileList(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()

	if out.IsJSON() {
		return out.Print(cfg.Profiles)
	}

	out.Printf("Profiles:\n\n")

	for name, profile := range cfg.Profiles {
		indicator := "  "
		if name == cfg.CurrentProfile {
			indicator = output.Green("* ")
		}

		out.Printf("%s%s\n", indicator, output.Bold(name))
		out.Printf("    API URL:    %s\n", profile.APIURL)
		if profile.ProjectID != "" {
			out.Printf("    Project ID: %s\n", profile.ProjectID)
		}
		out.Printf("\n")
	}

	return nil
}

func runConfigProfileUse(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()

	name := args[0]

	if _, ok := cfg.Profiles[name]; !ok {
		return fmt.Errorf("profile '%s' does not exist", name)
	}

	cfg.SetCurrentProfile(name)

	if err := cfg.Save(); err != nil {
		return fmt.Errorf("saving configuration: %w", err)
	}

	out.Success("Switched to profile '%s'", name)
	return nil
}

func runConfigProfileCreate(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()

	name := args[0]

	if _, ok := cfg.Profiles[name]; ok {
		return fmt.Errorf("profile '%s' already exists", name)
	}

	profile := config.Profile{
		APIURL:    profileAPIURL,
		ProjectID: profileProjectID,
	}

	if profile.APIURL == "" {
		profile.APIURL = "http://localhost:8080"
	}

	cfg.SetProfile(name, profile)

	if err := cfg.Save(); err != nil {
		return fmt.Errorf("saving configuration: %w", err)
	}

	out.Success("Created profile '%s'", name)
	return nil
}

func runConfigProfileDelete(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()

	name := args[0]

	if err := cfg.DeleteProfile(name); err != nil {
		return err
	}

	if err := cfg.Save(); err != nil {
		return fmt.Errorf("saving configuration: %w", err)
	}

	out.Success("Deleted profile '%s'", name)
	return nil
}
