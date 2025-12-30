#!/bin/bash
#
# Setup script to install Git hooks
# Run this once per developer workstation
#

HOOKS_DIR=".githooks"
GIT_HOOKS_DIR=".git/hooks"

echo " Setting up Git hooks for client-hub-backend..."

# Check if we're in a git repository
if [ ! -d ".git" ]; then
    echo "❌ Error: Not in a git repository"
    exit 1
fi

# Method 1: Configure Git to use .githooks directory (RECOMMENDED)
echo " Configuring Git to use $HOOKS_DIR directory..."
git config core.hooksPath "$HOOKS_DIR"

# Make hooks executable
if [ -f "$HOOKS_DIR/pre-commit" ]; then
    chmod +x "$HOOKS_DIR/pre-commit"
    echo "✅ Made pre-commit hook executable"
fi

echo ""
echo "✅ Git hooks setup complete!"
echo ""
echo "Installed hooks:"
echo "  - pre-commit: Prevents committing sensitive files"
echo ""
echo "To test the hook, try:"
echo "  echo 'test' > test.env"
echo "  git add test.env"
echo "  git commit -m 'test' # Should be blocked"
echo ""
