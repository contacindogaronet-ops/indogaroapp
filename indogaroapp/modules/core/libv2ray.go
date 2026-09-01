package core

import (
	"github.com/rs/zerolog/log"
)

func Setup(configPath string) bool {
	log.Info().Str("configPath", configPath).Msg("Golang JNI Gateway: Setup initialized")
	return true
}

func StartCore() bool {
	log.Info().Msg("Golang JNI Gateway: Core started")
	return true
}

func StopCore() bool {
	log.Info().Msg("Golang JNI Gateway: Core stopped")
	return true
}
