#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT="${PROJECT:-test-poc}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEEP_PROJECT="${KEEP_PROJECT:-false}"
ARTIFACT_DIR="${ARTIFACT_DIR:-}"
PROJECT_CREATED=false

collect_artifacts() {
    if [[ -z "$ARTIFACT_DIR" ]]; then
        return
    fi

    mkdir -p "$ARTIFACT_DIR"
    echo "Collecting OpenShift diagnostics in '$ARTIFACT_DIR'..."
    oc get all -n "$PROJECT" -o wide \
        >"$ARTIFACT_DIR/resources.txt" 2>&1 || true
    oc get events -n "$PROJECT" --sort-by=.lastTimestamp \
        >"$ARTIFACT_DIR/events.txt" 2>&1 || true
    oc describe deployment/poc-elasticsearch deployment/poc-app job/api-test \
        -n "$PROJECT" >"$ARTIFACT_DIR/descriptions.txt" 2>&1 || true
    oc logs deployment/poc-elasticsearch -n "$PROJECT" --all-containers=true \
        >"$ARTIFACT_DIR/elasticsearch.log" 2>&1 || true
    oc logs deployment/poc-app -n "$PROJECT" --all-containers=true \
        >"$ARTIFACT_DIR/application.log" 2>&1 || true
    oc logs job/api-test -n "$PROJECT" --all-containers=true \
        >"$ARTIFACT_DIR/api-test.log" 2>&1 || true
    oc logs buildconfig/poc-app -n "$PROJECT" \
        >"$ARTIFACT_DIR/build.log" 2>&1 || true
}

cleanup() {
    if [[ "$KEEP_PROJECT" == "true" ]]; then
        echo "Cleaning up temporary API test job from project '$PROJECT'..."
        oc delete job/api-test -n "$PROJECT" --ignore-not-found=true
        echo "KEEP_PROJECT=true, leaving Elasticsearch, the application, and build resources in place."
        return
    fi

    if [[ "$PROJECT_CREATED" == "true" ]]; then
        echo "Cleaning up project '$PROJECT'..."
        oc delete project "$PROJECT" --ignore-not-found=true
        return
    fi

    echo "Cleaning up POC resources from existing project '$PROJECT'..."
    oc delete -f "$SCRIPT_DIR/openshift/api-test-job.yaml" \
        -f "$SCRIPT_DIR/openshift/app.yaml" \
        -f "$SCRIPT_DIR/openshift/elasticsearch.yaml" \
        -n "$PROJECT" \
        --ignore-not-found=true
    oc delete buildconfig/poc-app imagestream/poc-app \
        -n "$PROJECT" --ignore-not-found=true
}

diagnostics() {
    local exit_code=$?

    if [[ $exit_code -eq 0 ]]; then
        return
    fi

    echo ""
    echo "POC failed. Collecting diagnostics from project '$PROJECT'..."
    oc project "$PROJECT" >/dev/null 2>&1 || return "$exit_code"
    collect_artifacts

    echo ""
    echo "Pods:"
    oc get pods -o wide || true

    echo ""
    echo "Events:"
    oc get events --sort-by=.lastTimestamp || true

    echo ""
    echo "Elasticsearch logs:"
    oc logs deployment/poc-elasticsearch --all-containers=true --tail=120 || true

    echo ""
    echo "Spring Boot logs:"
    oc logs deployment/poc-app --all-containers=true --tail=120 || true

    echo ""
    echo "API test job logs:"
    oc logs job/api-test --all-containers=true || true

    echo ""
    echo "Application build logs:"
    oc logs buildconfig/poc-app --tail=120 || true

    return "$exit_code"
}

trap diagnostics ERR
trap cleanup EXIT

if ! command -v oc >/dev/null 2>&1; then
    echo "The 'oc' CLI was not found on PATH."
    echo "Start OpenShift Local, then run: eval \"\$(crc oc-env)\""
    exit 1
fi

echo "Creating test project '$PROJECT'..."
if oc get project "$PROJECT" >/dev/null 2>&1; then
    oc project "$PROJECT"
else
    oc new-project "$PROJECT"
    PROJECT_CREATED=true
fi

echo "Starting Elasticsearch..."
oc apply -f "$SCRIPT_DIR/openshift/elasticsearch.yaml"

echo "Waiting for Elasticsearch rollout..."
oc rollout status deployment/poc-elasticsearch --timeout=180s

echo "Waiting for Elasticsearch readiness..."
oc wait --for=condition=ready pod -l app=poc-elasticsearch --timeout=180s

echo "Building the Spring Boot application image..."
oc delete buildconfig/poc-app imagestream/poc-app --ignore-not-found=true
oc new-build --name=poc-app --binary --strategy=docker
oc start-build poc-app --from-dir="$SCRIPT_DIR" --follow --wait

echo "Starting Spring Boot..."
APP_IMAGE="image-registry.openshift-image-registry.svc:5000/$PROJECT/poc-app:latest"
sed "s|image: poc-app:latest|image: $APP_IMAGE|" \
    "$SCRIPT_DIR/openshift/app.yaml" | oc apply -f -

echo "Waiting for Spring Boot rollout..."
oc rollout status deployment/poc-app --timeout=240s

echo "Starting API test container..."
oc delete job api-test --ignore-not-found=true
oc apply -f "$SCRIPT_DIR/openshift/api-test-job.yaml"

echo "Waiting for tests..."
oc wait --for=condition=complete job/api-test --timeout=180s

echo "Test output:"
oc logs job/api-test

collect_artifacts

echo ""
echo "POC PASSED!"
