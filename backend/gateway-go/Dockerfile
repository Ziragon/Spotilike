# Build stage
FROM golang:1.26-alpine AS builder

WORKDIR /build

COPY go.mod go.sum ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -ldflags="-s -w" -o /build/gateway ./cmd/gateway

# Runtime stage
FROM alpine:3.20
RUN apk add --no-cache ca-certificates && \
    addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=builder /build/gateway .
COPY config/config.yaml ./config/config.yaml
ENV CONFIG_PATH=/app/config/config.yaml

USER app

EXPOSE 8080

ENTRYPOINT ["./gateway"]
