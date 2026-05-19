package main

import (
	"os"

	"github.com/Abhishekkumar2021/Pravah/cli/internal/cmd"
)

// Build information (set by GoReleaser)
var (
	version = "dev"
	commit  = "none"
	date    = "unknown"
)

func main() {
	cmd.SetVersion(version, commit, date)
	if err := cmd.Execute(); err != nil {
		os.Exit(1)
	}
}
