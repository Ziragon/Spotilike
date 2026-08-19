package config

import (
	"fmt"
	"os"
	"strings"

	"github.com/ilyakaznacheev/cleanenv"
	"github.com/joho/godotenv"
	"gopkg.in/yaml.v3"
)

type CorsSet struct {
	AllowedOrigins   []string `yaml:"allowed_origins"`
	AllowCredentials bool     `yaml:"allow_credentials"`
}

type DocsRoute struct {
	Name            string `yaml:"name"`
	GatewayPath     string `yaml:"gateway_path"`
	BackendDocsPath string `yaml:"backend_docs_path"`
	TargetURL       string `yaml:"target_url"`
}

type Config struct {
	Env            string      `env:"APP_ENV" env-default:"dev"`
	Port           string      `env:"SERVER_PORT" env-default:"8080"`
	JWTSecret      string      `env:"JWT_SECRET_KEY" env-default:"bG9jYWwtZGV2LXNlY3JldC1rZXktMzItYnl0ZXMtbG9uZyE="`
	UserServiceURL string      `env:"USER_SERVICE_URL" env-default:"http://localhost:8081"`
	DocsRoutes     []DocsRoute `yaml:"docs_routes"`
	Cors           CorsSet     `yaml:"cors"`
}

// expandEnv helps to parse ${VAR:-default} syntax
func expandEnv(s string) string {
	return os.Expand(s, func(key string) string {
		parts := strings.SplitN(key, ":-", 2)
		if val := os.Getenv(parts[0]); val != "" {
			return val
		}
		if len(parts) > 1 {
			return parts[1]
		}
		return ""
	})
}

// Load extracts vars from config.yaml and .env files
func Load() (*Config, error) {
	_ = godotenv.Load(".env", "../.env")

	configPath := os.Getenv("CONFIG_PATH")
	if configPath == "" {
		configPath = "./config/config.yaml"
	}

	cfg := &Config{}

	data, err := os.ReadFile(configPath)
	if err != nil {
		return nil, fmt.Errorf("read config error: %w", err)
	}

	expanded := expandEnv(string(data))

	if err := yaml.Unmarshal([]byte(expanded), cfg); err != nil {
		return nil, fmt.Errorf("yaml parse error: %w", err)
	}

	if err := cleanenv.ReadEnv(cfg); err != nil {
		return nil, fmt.Errorf("read env error: %w", err)
	}

	return cfg, nil
}
