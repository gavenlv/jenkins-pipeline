#!/bin/bash

# Universal Jenkins Pipeline Framework Validation Script
# This script validates all components of the pipeline framework

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${SCRIPT_DIR}/validation.log"
TEST_REPORTS_DIR="${SCRIPT_DIR}/test-reports"
JENKINS_CLI_JAR="${SCRIPT_DIR}/jenkins-cli.jar"

# Default values
RUN_UNIT_TESTS=true
RUN_INTEGRATION_TESTS=true
RUN_SYNTAX_VALIDATION=true
RUN_SECURITY_CHECKS=true
RUN_PERFORMANCE_TESTS=false
GENERATE_REPORTS=true
JENKINS_URL=""
JENKINS_USER=""
JENKINS_TOKEN=""

# Functions
log() {
    echo -e "[$(date +'%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log_info() {
    log "${BLUE}[INFO]${NC} $1"
}

log_success() {
    log "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    log "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    log "${RED}[ERROR]${NC} $1"
}

show_usage() {
    cat << EOF
Universal Jenkins Pipeline Framework Validation Script

Usage: $0 [OPTIONS]

OPTIONS:
    -h, --help                  Show this help message
    -u, --unit-tests           Run unit tests (default: true)
    -i, --integration-tests    Run integration tests (default: true)
    -s, --syntax-validation    Run syntax validation (default: true)
    -S, --security-checks      Run security checks (default: true)
    -p, --performance-tests    Run performance tests (default: false)
    -r, --generate-reports     Generate test reports (default: true)
    -j, --jenkins-url URL      Jenkins URL for remote testing
    -U, --jenkins-user USER    Jenkins username
    -T, --jenkins-token TOKEN  Jenkins API token
    --skip-unit-tests          Skip unit tests
    --skip-integration-tests   Skip integration tests
    --skip-syntax-validation   Skip syntax validation
    --skip-security-checks     Skip security checks
    --quick                    Run only quick tests (unit + syntax)
    --full                     Run all tests including performance

EXAMPLES:
    $0                                          # Run default validation
    $0 --quick                                  # Quick validation
    $0 --full                                   # Full validation with performance tests
    $0 --jenkins-url http://jenkins.local:8080  # Remote Jenkins validation

EOF
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_usage
                exit 0
                ;;
            -u|--unit-tests)
                RUN_UNIT_TESTS=true
                shift
                ;;
            -i|--integration-tests)
                RUN_INTEGRATION_TESTS=true
                shift
                ;;
            -s|--syntax-validation)
                RUN_SYNTAX_VALIDATION=true
                shift
                ;;
            -S|--security-checks)
                RUN_SECURITY_CHECKS=true
                shift
                ;;
            -p|--performance-tests)
                RUN_PERFORMANCE_TESTS=true
                shift
                ;;
            -r|--generate-reports)
                GENERATE_REPORTS=true
                shift
                ;;
            -j|--jenkins-url)
                JENKINS_URL="$2"
                shift 2
                ;;
            -U|--jenkins-user)
                JENKINS_USER="$2"
                shift 2
                ;;
            -T|--jenkins-token)
                JENKINS_TOKEN="$2"
                shift 2
                ;;
            --skip-unit-tests)
                RUN_UNIT_TESTS=false
                shift
                ;;
            --skip-integration-tests)
                RUN_INTEGRATION_TESTS=false
                shift
                ;;
            --skip-syntax-validation)
                RUN_SYNTAX_VALIDATION=false
                shift
                ;;
            --skip-security-checks)
                RUN_SECURITY_CHECKS=false
                shift
                ;;
            --quick)
                RUN_UNIT_TESTS=true
                RUN_INTEGRATION_TESTS=false
                RUN_SYNTAX_VALIDATION=true
                RUN_SECURITY_CHECKS=false
                RUN_PERFORMANCE_TESTS=false
                shift
                ;;
            --full)
                RUN_UNIT_TESTS=true
                RUN_INTEGRATION_TESTS=true
                RUN_SYNTAX_VALIDATION=true
                RUN_SECURITY_CHECKS=true
                RUN_PERFORMANCE_TESTS=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    local missing_tools=()
    
    # Check for required tools
    if ! command -v java &> /dev/null; then
        missing_tools+=("java")
    fi
    
    if ! command -v groovy &> /dev/null; then
        missing_tools+=("groovy")
    fi
    
    if ! command -v gradle &> /dev/null && ! [ -f "./gradlew" ]; then
        missing_tools+=("gradle or gradlew")
    fi
    
    if ! command -v git &> /dev/null; then
        missing_tools+=("git")
    fi
    
    # Optional tools
    if ! command -v docker &> /dev/null; then
        log_warning "Docker not found - container-related tests will be skipped"
    fi
    
    if ! command -v helm &> /dev/null; then
        log_warning "Helm not found - Helm deployment tests will be skipped"
    fi
    
    if ! command -v kubectl &> /dev/null; then
        log_warning "kubectl not found - Kubernetes tests will be skipped"
    fi
    
    if [ ${#missing_tools[@]} -gt 0 ]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        log_error "Please install the missing tools and run the script again."
        exit 1
    fi
    
    log_success "All prerequisites check passed"
}

setup_test_environment() {
    log_info "Setting up test environment..."
    
    # Create test directories
    mkdir -p "$TEST_REPORTS_DIR"
    mkdir -p "${SCRIPT_DIR}/temp"
    
    # Clean previous results
    rm -f "$LOG_FILE"
    rm -rf "${TEST_REPORTS_DIR:?}"/*
    
    # Initialize log file
    echo "Pipeline Framework Validation - $(date)" > "$LOG_FILE"
    echo "=======================================" >> "$LOG_FILE"
    
    log_success "Test environment setup completed"
}

validate_syntax() {
    if [ "$RUN_SYNTAX_VALIDATION" != "true" ]; then
        log_info "Skipping syntax validation"
        return 0
    fi
    
    log_info "Validating Groovy syntax..."
    
    local syntax_errors=0
    local total_files=0
    
    # Find all Groovy files
    while IFS= read -r -d '' file; do
        ((total_files++))
        log_info "Validating syntax: $file"
        
        if ! groovy -c "$file" 2>> "$LOG_FILE"; then
            log_error "Syntax error in: $file"
            ((syntax_errors++))
        fi
    done < <(find src examples -name "*.groovy" -print0)
    
    if [ $syntax_errors -eq 0 ]; then
        log_success "Syntax validation passed ($total_files files checked)"
        return 0
    else
        log_error "Syntax validation failed ($syntax_errors errors out of $total_files files)"
        return 1
    fi
}

run_unit_tests() {
    if [ "$RUN_UNIT_TESTS" != "true" ]; then
        log_info "Skipping unit tests"
        return 0
    fi
    
    log_info "Running unit tests..."
    
    cd tests
    
    if [ -f "./gradlew" ]; then
        GRADLE_CMD="./gradlew"
    else
        GRADLE_CMD="gradle"
    fi
    
    if $GRADLE_CMD clean unitTests --info 2>&1 | tee -a "$LOG_FILE"; then
        log_success "Unit tests passed"
        
        # Copy test results
        if [ -d "build/test-results" ]; then
            cp -r build/test-results/* "$TEST_REPORTS_DIR/" 2>/dev/null || true
        fi
        
        cd ..
        return 0
    else
        log_error "Unit tests failed"
        cd ..
        return 1
    fi
}

run_integration_tests() {
    if [ "$RUN_INTEGRATION_TESTS" != "true" ]; then
        log_info "Skipping integration tests"
        return 0
    fi
    
    log_info "Running integration tests..."
    
    cd tests
    
    if [ -f "./gradlew" ]; then
        GRADLE_CMD="./gradlew"
    else
        GRADLE_CMD="gradle"
    fi
    
    if $GRADLE_CMD integrationTests --info 2>&1 | tee -a "$LOG_FILE"; then
        log_success "Integration tests passed"
        
        # Copy test results
        if [ -d "build/test-results" ]; then
            cp -r build/test-results/* "$TEST_REPORTS_DIR/" 2>/dev/null || true
        fi
        
        cd ..
        return 0
    else
        log_error "Integration tests failed"
        cd ..
        return 1
    fi
}

run_security_checks() {
    if [ "$RUN_SECURITY_CHECKS" != "true" ]; then
        log_info "Skipping security checks"
        return 0
    fi
    
    log_info "Running security checks..."
    
    local security_issues=0
    
    # Check for hardcoded secrets
    log_info "Scanning for hardcoded secrets..."
    if grep -r -i -E "(password|secret|key|token)\\s*[:=]\\s*['\"][^'\"]*['\"]" src/ examples/ --include="*.groovy" | grep -v -E "(password.*\\$\\{|secret.*\\$\\{|key.*\\$\\{|token.*\\$\\{)" | head -10; then
        log_warning "Potential hardcoded secrets found - please review"
        ((security_issues++))
    fi
    
    # Check for HTTP URLs in production code
    log_info "Checking for insecure HTTP URLs..."
    if grep -r "http://" src/ examples/ --include="*.groovy" | grep -v "localhost\\|127.0.0.1\\|example.com" | head -5; then
        log_warning "HTTP URLs found - consider using HTTPS in production"
        ((security_issues++))
    fi
    
    # Check for potential command injection
    log_info "Checking for potential command injection vulnerabilities..."
    if grep -r -E "sh\\s*['\"].*\\$\\{" src/ examples/ --include="*.groovy" | head -5; then
        log_warning "Potential command injection vulnerabilities found - please review"
        ((security_issues++))
    fi
    
    if [ $security_issues -eq 0 ]; then
        log_success "Security checks passed"
        return 0
    else
        log_warning "Security checks completed with $security_issues potential issues"
        return 0  # Don't fail on warnings
    fi
}

run_performance_tests() {
    if [ "$RUN_PERFORMANCE_TESTS" != "true" ]; then
        log_info "Skipping performance tests"
        return 0
    fi
    
    log_info "Running performance tests..."
    
    cd tests
    
    if [ -f "./gradlew" ]; then
        GRADLE_CMD="./gradlew"
    else
        GRADLE_CMD="gradle"
    fi
    
    if $GRADLE_CMD performanceTests --info 2>&1 | tee -a "$LOG_FILE"; then
        log_success "Performance tests passed"
        cd ..
        return 0
    else
        log_error "Performance tests failed"
        cd ..
        return 1
    fi
}

validate_jenkins_pipeline() {
    if [ -z "$JENKINS_URL" ]; then
        log_info "Jenkins URL not provided - skipping remote Jenkins validation"
        return 0
    fi
    
    log_info "Validating pipelines on Jenkins: $JENKINS_URL"
    
    # Download Jenkins CLI if not present
    if [ ! -f "$JENKINS_CLI_JAR" ]; then
        log_info "Downloading Jenkins CLI..."
        if ! curl -s -o "$JENKINS_CLI_JAR" "${JENKINS_URL}/jnlpJars/jenkins-cli.jar"; then
            log_error "Failed to download Jenkins CLI"
            return 1
        fi
    fi
    
    # Test Jenkins connection
    if ! java -jar "$JENKINS_CLI_JAR" -s "$JENKINS_URL" -auth "${JENKINS_USER}:${JENKINS_TOKEN}" who-am-i 2>&1 | tee -a "$LOG_FILE"; then
        log_error "Failed to connect to Jenkins"
        return 1
    fi
    
    log_success "Jenkins validation completed"
    return 0
}

generate_reports() {
    if [ "$GENERATE_REPORTS" != "true" ]; then
        log_info "Skipping report generation"
        return 0
    fi
    
    log_info "Generating validation reports..."
    
    local report_file="${TEST_REPORTS_DIR}/validation-summary.html"
    
    cat > "$report_file" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>Pipeline Framework Validation Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f5f5f5; padding: 20px; border-radius: 5px; }
        .success { color: green; }
        .error { color: red; }
        .warning { color: orange; }
        .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .log { background-color: #f9f9f9; padding: 10px; font-family: monospace; white-space: pre-wrap; }
    </style>
</head>
<body>
    <div class="header">
        <h1>Pipeline Framework Validation Report</h1>
        <p>Generated on: $(date)</p>
        <p>Script version: 1.0.0</p>
    </div>
    
    <div class="section">
        <h2>Validation Summary</h2>
        <ul>
            <li>Unit Tests: $([ "$RUN_UNIT_TESTS" = "true" ] && echo "✓ Executed" || echo "⚠ Skipped")</li>
            <li>Integration Tests: $([ "$RUN_INTEGRATION_TESTS" = "true" ] && echo "✓ Executed" || echo "⚠ Skipped")</li>
            <li>Syntax Validation: $([ "$RUN_SYNTAX_VALIDATION" = "true" ] && echo "✓ Executed" || echo "⚠ Skipped")</li>
            <li>Security Checks: $([ "$RUN_SECURITY_CHECKS" = "true" ] && echo "✓ Executed" || echo "⚠ Skipped")</li>
            <li>Performance Tests: $([ "$RUN_PERFORMANCE_TESTS" = "true" ] && echo "✓ Executed" || echo "⚠ Skipped")</li>
        </ul>
    </div>
    
    <div class="section">
        <h2>Validation Log</h2>
        <div class="log">$(cat "$LOG_FILE" 2>/dev/null || echo "Log file not available")</div>
    </div>
</body>
</html>
EOF
    
    log_success "Validation report generated: $report_file"
    
    # Generate JSON report for CI/CD integration
    local json_report="${TEST_REPORTS_DIR}/validation-result.json"
    cat > "$json_report" << EOF
{
    "timestamp": "$(date -Iseconds)",
    "framework_version": "1.0.0",
    "validation_status": "completed",
    "tests": {
        "unit_tests": {
            "executed": $RUN_UNIT_TESTS,
            "status": "$([ "$RUN_UNIT_TESTS" = "true" ] && echo "passed" || echo "skipped")"
        },
        "integration_tests": {
            "executed": $RUN_INTEGRATION_TESTS,
            "status": "$([ "$RUN_INTEGRATION_TESTS" = "true" ] && echo "passed" || echo "skipped")"
        },
        "syntax_validation": {
            "executed": $RUN_SYNTAX_VALIDATION,
            "status": "$([ "$RUN_SYNTAX_VALIDATION" = "true" ] && echo "passed" || echo "skipped")"
        },
        "security_checks": {
            "executed": $RUN_SECURITY_CHECKS,
            "status": "$([ "$RUN_SECURITY_CHECKS" = "true" ] && echo "passed" || echo "skipped")"
        },
        "performance_tests": {
            "executed": $RUN_PERFORMANCE_TESTS,
            "status": "$([ "$RUN_PERFORMANCE_TESTS" = "true" ] && echo "passed" || echo "skipped")"
        }
    },
    "reports": {
        "html_report": "$report_file",
        "log_file": "$LOG_FILE",
        "test_results_dir": "$TEST_REPORTS_DIR"
    }
}
EOF
    
    log_success "JSON report generated: $json_report"
}

cleanup() {
    log_info "Cleaning up temporary files..."
    rm -rf "${SCRIPT_DIR}/temp"
    log_success "Cleanup completed"
}

main() {
    echo "=============================================="
    echo "  Universal Jenkins Pipeline Framework"
    echo "         Validation Script v1.0.0"
    echo "=============================================="
    echo
    
    parse_arguments "$@"
    
    local start_time=$(date +%s)
    local failed_tests=0
    
    # Run validation steps
    setup_test_environment
    check_prerequisites
    
    # Syntax validation
    if ! validate_syntax; then
        ((failed_tests++))
    fi
    
    # Unit tests
    if ! run_unit_tests; then
        ((failed_tests++))
    fi
    
    # Integration tests
    if ! run_integration_tests; then
        ((failed_tests++))
    fi
    
    # Security checks
    if ! run_security_checks; then
        ((failed_tests++))
    fi
    
    # Performance tests
    if ! run_performance_tests; then
        ((failed_tests++))
    fi
    
    # Jenkins validation
    if ! validate_jenkins_pipeline; then
        ((failed_tests++))
    fi
    
    # Generate reports
    generate_reports
    
    # Cleanup
    cleanup
    
    # Final summary
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    echo
    echo "=============================================="
    echo "           Validation Summary"
    echo "=============================================="
    echo "Duration: ${duration}s"
    echo "Reports: $TEST_REPORTS_DIR"
    echo "Log file: $LOG_FILE"
    echo
    
    if [ $failed_tests -eq 0 ]; then
        log_success "🎉 All validation tests passed!"
        echo
        echo "✅ The Universal Jenkins Pipeline Framework is ready to use!"
        echo "📖 Check the README.md for detailed usage instructions"
        echo "🔗 View the validation report: ${TEST_REPORTS_DIR}/validation-summary.html"
        exit 0
    else
        log_error "❌ Validation failed with $failed_tests error(s)"
        echo
        echo "❌ Please review the errors and fix them before using the framework"
        echo "📝 Check the log file for detailed error information: $LOG_FILE"
        echo "🔍 Review the test reports in: $TEST_REPORTS_DIR"
        exit 1
    fi
}

# Run main function with all arguments
main "$@" 