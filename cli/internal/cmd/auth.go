package cmd

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"strings"
	"syscall"
	"time"

	"github.com/spf13/cobra"
	"golang.org/x/term"

	"github.com/Abhishekkumar2021/Pravah/cli/internal/client"
	"github.com/Abhishekkumar2021/Pravah/cli/internal/keyring"
	"github.com/Abhishekkumar2021/Pravah/cli/internal/output"
)

var authCmd = &cobra.Command{
	Use:   "auth",
	Short: "Authentication commands",
	Long:  "Commands for logging in, logging out, and managing authentication.",
}

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Log in to Pravah",
	Long: `Log in to Pravah using email/password or an API token.

Examples:
  # Interactive login
  pravah login

  # Login with API token
  pravah login --token <api-token>`,
	RunE: runLogin,
}

var logoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Log out of Pravah",
	Long:  "Log out and remove stored credentials.",
	RunE:  runLogout,
}

var statusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show authentication status",
	Long:  "Display the current authentication status and user information.",
	RunE:  runAuthStatus,
}

var (
	loginToken string
)

func init() {
	authCmd.AddCommand(loginCmd)
	authCmd.AddCommand(logoutCmd)
	authCmd.AddCommand(statusCmd)

	loginCmd.Flags().StringVar(&loginToken, "token", "", "API token for authentication")
}

func runLogin(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()
	store := keyring.NewStore(cfg.CurrentProfile)

	if loginToken != "" {
		if err := store.SaveAPIToken(loginToken); err != nil {
			return fmt.Errorf("saving API token: %w", err)
		}

		apiClient := client.NewClient(cfg.APIURL(), client.WithToken(loginToken))
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()

		user, err := apiClient.GetCurrentUser(ctx)
		if err != nil {
			store.Delete()
			return fmt.Errorf("validating token: %w", err)
		}

		out.Success("Logged in as %s", user.Email)
		return nil
	}

	reader := bufio.NewReader(os.Stdin)

	fmt.Print("Email: ")
	email, err := reader.ReadString('\n')
	if err != nil {
		return fmt.Errorf("reading email: %w", err)
	}
	email = strings.TrimSpace(email)

	fmt.Print("Password: ")
	passwordBytes, err := term.ReadPassword(int(syscall.Stdin))
	if err != nil {
		return fmt.Errorf("reading password: %w", err)
	}
	fmt.Println()
	password := string(passwordBytes)

	apiClient := client.NewClient(cfg.APIURL())
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	resp, err := apiClient.Login(ctx, email, password)
	if err != nil {
		return fmt.Errorf("login failed: %w", err)
	}

	creds := &keyring.Credentials{
		AccessToken:  resp.AccessToken,
		RefreshToken: resp.RefreshToken,
		TokenType:    resp.TokenType,
		ExpiresAt:    time.Now().Add(time.Duration(resp.ExpiresIn) * time.Second).Unix(),
	}

	if err := store.Save(creds); err != nil {
		return fmt.Errorf("saving credentials: %w", err)
	}

	user, err := apiClient.GetCurrentUser(ctx)
	if err != nil {
		out.Warning("Logged in but could not fetch user info: %v", err)
		out.Success("Logged in successfully")
		return nil
	}

	out.Success("Logged in as %s (%s)", user.Name, user.Email)
	return nil
}

func runLogout(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()
	store := keyring.NewStore(cfg.CurrentProfile)

	token, err := store.GetToken()
	if err == nil && token != "" {
		apiClient := client.NewClient(cfg.APIURL(), client.WithToken(token))
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		if err := apiClient.Logout(ctx); err != nil {
			out.Warning("Server logout failed: %v", err)
		}
	}

	if err := store.Delete(); err != nil {
		return fmt.Errorf("removing credentials: %w", err)
	}

	out.Success("Logged out successfully")
	return nil
}

func runAuthStatus(cmd *cobra.Command, args []string) error {
	cfg := GetConfig()
	out := GetOutput()
	store := keyring.NewStore(cfg.CurrentProfile)

	token, err := store.GetToken()
	if err != nil || token == "" {
		out.Info("Not logged in")
		out.Printf("  Profile: %s\n", cfg.CurrentProfile)
		out.Printf("  API URL: %s\n", cfg.APIURL())
		return nil
	}

	apiClient := client.NewClient(cfg.APIURL(), client.WithToken(token))
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	user, err := apiClient.GetCurrentUser(ctx)
	if err != nil {
		out.Error("Token invalid or expired")
		out.Info("Run 'pravah login' to authenticate again")
		return nil
	}

	if out.IsJSON() {
		return out.Print(map[string]interface{}{
			"authenticated": true,
			"profile":       cfg.CurrentProfile,
			"apiUrl":        cfg.APIURL(),
			"user":          user,
		})
	}

	out.Success("Logged in")
	out.Printf("\n")
	out.Printf("  Profile:   %s\n", cfg.CurrentProfile)
	out.Printf("  API URL:   %s\n", cfg.APIURL())
	out.Printf("\n")
	out.Printf("  User:      %s\n", user.Name)
	out.Printf("  Email:     %s\n", user.Email)
	out.Printf("  Tenant:    %s\n", user.TenantID)

	if len(user.Roles) > 0 {
		roleNames := make([]string, len(user.Roles))
		for i, r := range user.Roles {
			roleNames[i] = r.Name
		}
		out.Printf("  Roles:     %s\n", output.Cyan(strings.Join(roleNames, ", ")))
	}

	return nil
}
