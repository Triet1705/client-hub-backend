#!/bin/bash
#
# Test script for pre-commit hook validation
# Run this to verify hook is working correctly
#

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW} Testing Pre-Commit Hook Functionality${NC}"
echo "=========================================="
echo ""

PASSED=0
FAILED=0

# Helper function to run tests
run_test() {
    local test_name=$1
    local expected=$2  # "BLOCK" or "PASS"
    local file=$3
    local content=$4
    
    echo -e "${YELLOW}Test: $test_name${NC}"
    
    # Create test file
    echo "$content" > "$file"
    git add "$file" 2>/dev/null
    
    # Try to commit
    git commit -m "test: $test_name" --no-verify 2>/dev/null >/dev/null
    COMMIT_STATUS=$?
    
    # Remove test file
    git reset HEAD "$file" 2>/dev/null >/dev/null
    rm -f "$file"
    
    # Now test with hook
    echo "$content" > "$file"
    git add "$file" 2>/dev/null
    
    # Run hook manually
    .githooks/pre-commit 2>&1 | head -5
    HOOK_STATUS=$?
    
    # Cleanup
    git reset HEAD "$file" 2>/dev/null >/dev/null
    rm -f "$file"
    
    # Validate
    if [ "$expected" = "BLOCK" ]; then
        if [ $HOOK_STATUS -ne 0 ]; then
            echo -e "${GREEN}✅ PASS - Correctly blocked${NC}"
            PASSED=$((PASSED + 1))
        else
            echo -e "${RED}❌ FAIL - Should have blocked${NC}"
            FAILED=$((FAILED + 1))
        fi
    else
        if [ $HOOK_STATUS -eq 0 ]; then
            echo -e "${GREEN}✅ PASS - Correctly allowed${NC}"
            PASSED=$((PASSED + 1))
        else
            echo -e "${RED}❌ FAIL - Should have allowed${NC}"
            FAILED=$((FAILED + 1))
        fi
    fi
    echo ""
}

# ============================================================================
# TEST SUITE
# ============================================================================

echo "Category 1: File Extension Blocking"
echo "------------------------------------"
run_test "Block .env file" "BLOCK" "test.env" "ADMIN_KEY=secret123"
run_test "Block .key file" "BLOCK" "private.key" "-----BEGIN PRIVATE KEY-----"
run_test "Block .pem file" "BLOCK" "cert.pem" "certificate content"
run_test "Block application-prod.properties" "BLOCK" "application-prod.properties" "db.password=secret"

echo "Category 2: Ethereum Private Key Detection"
echo "-------------------------------------------"
run_test "Block Ethereum private key" "BLOCK" "config.properties" "PRIVATE_KEY=0xabcd1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab"
run_test "Allow dummy Ethereum address" "PASS" "test.java" "address = 0x0000000000000000000000000000000000000000000000000000000000000000"
run_test "Allow placeholder" "PASS" "application.properties" "private_key=\${ADMIN_WALLET_KEY}"

echo "Category 3: PEM Key Detection"
echo "------------------------------"
run_test "Block PEM header in file" "BLOCK" "key-content.txt" "-----BEGIN PRIVATE KEY-----\nMIICXAIBAAKBgQC..."

echo "Category 4: Generic Patterns (should warn but may pass)"
echo "-------------------------------------------------------"
# Note: These tests may require manual interaction, so we'll just verify hook runs
echo "password=\"secret123\"" > test-warning.properties
git add test-warning.properties 2>/dev/null
echo "Running hook on generic secret (may prompt for input)..."
timeout 2 .githooks/pre-commit 2>&1 | head -5 || echo "(Timeout - interactive prompt expected)"
git reset HEAD test-warning.properties 2>/dev/null >/dev/null
rm -f test-warning.properties
echo ""

# ============================================================================
# SUMMARY
# ============================================================================

echo "=========================================="
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN} All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Please review the hook configuration.${NC}"
    exit 1
fi
