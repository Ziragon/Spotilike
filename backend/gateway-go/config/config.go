package config

import (
	"fmt"

	"github.com/caarlos0/env/v11"
	"github.com/joho/godotenv"
)

type Config struct {
	Env       string `env:"APP_ENV" envDefault:"dev"`
	Port      string `env:"SERVER_PORT" envDefault:"8080"`
	JWTSecret string `env:"JWT_SECRET_KEY" envDefault:"must-be-overridden"`
}

func Load() (*Config, error) {
	_ = godotenv.Load(".env", "../.env")

	cfg := &Config{}
	if err := env.Parse(cfg); err != nil {
		return nil, fmt.Errorf("config parse error: %w", err)
	}

	return cfg, nil
}
