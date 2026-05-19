#!/bin/bash
set -e

REPO="Abhishekkumar2021/Pravah"
INSTALL_DIR="${INSTALL_DIR:-/usr/local/bin}"
BINARY_NAME="pravah"

echo "Installing Pravah CLI..."

OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)

case "$ARCH" in
    x86_64)
        ARCH="amd64"
        ;;
    aarch64|arm64)
        ARCH="arm64"
        ;;
    *)
        echo "Unsupported architecture: $ARCH"
        exit 1
        ;;
esac

echo "Detected OS: $OS, Arch: $ARCH"

if [ -n "$VERSION" ]; then
    TAG="v$VERSION"
else
    TAG=$(curl -sL "https://api.github.com/repos/$REPO/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')
    if [ -z "$TAG" ]; then
        echo "Failed to fetch latest release. Please specify VERSION environment variable."
        exit 1
    fi
fi

echo "Installing version: $TAG"

URL="https://github.com/$REPO/releases/download/$TAG/${BINARY_NAME}_${TAG#v}_${OS}_${ARCH}.tar.gz"

TMP_DIR=$(mktemp -d)
trap "rm -rf $TMP_DIR" EXIT

echo "Downloading from: $URL"
curl -sL "$URL" -o "$TMP_DIR/pravah.tar.gz"

echo "Extracting..."
tar -xzf "$TMP_DIR/pravah.tar.gz" -C "$TMP_DIR"

echo "Installing to $INSTALL_DIR..."
if [ -w "$INSTALL_DIR" ]; then
    mv "$TMP_DIR/$BINARY_NAME" "$INSTALL_DIR/"
else
    sudo mv "$TMP_DIR/$BINARY_NAME" "$INSTALL_DIR/"
fi

chmod +x "$INSTALL_DIR/$BINARY_NAME"

echo ""
echo "Pravah CLI installed successfully!"
echo ""
echo "Run 'pravah --help' to get started."
echo ""
