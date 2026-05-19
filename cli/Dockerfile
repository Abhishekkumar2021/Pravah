FROM alpine:3.19

RUN apk add --no-cache ca-certificates tzdata

COPY pravah /usr/local/bin/pravah

RUN chmod +x /usr/local/bin/pravah

ENTRYPOINT ["/usr/local/bin/pravah"]
