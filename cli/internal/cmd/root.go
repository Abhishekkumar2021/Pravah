package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"

	"github.com/Abhishekkumar2021/Pravah/cli/internal/config"
	"github.com/Abhishekkumar2021/Pravah/cli/internal/output"
)

// Exit codes for CI integration
const (
	ExitSuccess      = 0
	ExitError        = 1
	ExitNoChanges    = 2
	ExitAuthRequired = 3
	ExitValidation   = 4
)

var (
	cfgFile       string
	outputFormat  string
	profile       string
	buildVersion  string
	buildCommit   string
	buildDate     string
	globalConfig  *config.Config
	globalOutput  *output.Formatter
)

// SetVersion sets build information from main
func SetVersion(version, commit, date string) {
	buildVersion = version
	buildCommit = commit
	buildDate = date
}

// rootCmd represents the base command when called without any subcommands
var rootCmd = &cobra.Command{
	Use:   "pravah",
	Short: "Pravah CLI - Data Pipeline Orchestration",
	Long: `Pravah CLI is a command-line tool for managing data pipelines,
workflows, and executions in the Pravah platform.

Examples:
  # Login to Pravah
  pravah login

  # List workflows
  pravah workflow list

  # Run a workflow
  pravah workflow run <workflow-id>

  # Deploy workflows from files
  pravah deploy -f workflows/`,
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		if cmd.Name() == "version" || cmd.Name() == "help" {
			return nil
		}

		var err error
		globalConfig, err = config.Load(cfgFile)
		if err != nil {
			return fmt.Errorf("failed to load config: %w", err)
		}

		if profile != "" {
			globalConfig.SetCurrentProfile(profile)
		}

		globalOutput = output.NewFormatter(outputFormat, os.Stdout)
		return nil
	},
}

// Execute adds all child commands to the root command and sets flags appropriately.
func Execute() error {
	return rootCmd.Execute()
}

func init() {
	cobra.OnInitialize(initConfig)

	rootCmd.PersistentFlags().StringVar(&cfgFile, "config", "", "config file (default is $HOME/.pravah/config.yaml)")
	rootCmd.PersistentFlags().StringVarP(&outputFormat, "output", "o", "table", "output format (table, json, yaml)")
	rootCmd.PersistentFlags().StringVarP(&profile, "profile", "p", "", "configuration profile to use")

	rootCmd.AddCommand(versionCmd)
	rootCmd.AddCommand(authCmd)
	rootCmd.AddCommand(workflowCmd)
	rootCmd.AddCommand(runCmd)
	rootCmd.AddCommand(deployCmd)
	rootCmd.AddCommand(configCmd)
}

func initConfig() {
	if cfgFile != "" {
		viper.SetConfigFile(cfgFile)
	} else {
		home, err := os.UserHomeDir()
		cobra.CheckErr(err)

		viper.AddConfigPath(home + "/.pravah")
		viper.SetConfigType("yaml")
		viper.SetConfigName("config")
	}

	viper.AutomaticEnv()
	viper.ReadInConfig()
}

// versionCmd represents the version command
var versionCmd = &cobra.Command{
	Use:   "version",
	Short: "Print the version number",
	Long:  "Print the version number, build commit, and build date.",
	Run: func(cmd *cobra.Command, args []string) {
		fmt.Printf("pravah version %s\n", buildVersion)
		fmt.Printf("  commit: %s\n", buildCommit)
		fmt.Printf("  built:  %s\n", buildDate)
	},
}

// GetConfig returns the global config (for use by subcommands)
func GetConfig() *config.Config {
	return globalConfig
}

// GetOutput returns the global output formatter
func GetOutput() *output.Formatter {
	return globalOutput
}
