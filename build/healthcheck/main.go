package main

import (
	"fmt"
	"net/http"
	"os"
	"time"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "Error: URL argument is missing.")
		os.Exit(1)
	}

	client := http.Client{
		Transport: &http.Transport{
			DisableKeepAlives: true,
		},
		Timeout: 8 * time.Second, // Timeout to prevent hanging
	}

	resp, err := client.Head(os.Args[1])
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: failed to perform HEAD request to %s: %v\n", os.Args[1], err)
		os.Exit(1)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		fmt.Fprintf(os.Stderr, "Error: unexpected status code %d for URL %s\n", resp.StatusCode, os.Args[1])
		os.Exit(1)
	}
}
