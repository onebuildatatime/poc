# OpenShift Elasticsearch POC

A production-ready Spring Boot application demonstrating Elasticsearch integration on OpenShift without Docker-in-Docker, Testcontainers, Knative, or Helm.

## What This Project Does

This POC proves:
- ✅ Building Docker images directly in OpenShift (no Docker daemon required)
- ✅ Deploying Elasticsearch as a temporary or persistent service
- ✅ Running Spring Boot with Elasticsearch backend
- ✅ Black-box API testing of the application
- ✅ Automatic cleanup and resource management
- ✅ Both local (CRC) and cloud-based CI/CD execution

**Two Deployment Modes:**
- **POC** (Development): Minimal resources, no security, ephemeral storage
- **Secure** (Office/Production): Full authentication, persistent storage, high availability

```text
OpenShift Project Architecture
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
├── Elasticsearch (8.15.0)
│   ├── MessageService: POST/GET messages
│   ├── DataService: Bulk data ingestion
│   └── Service on port 9200 (HTTP or HTTPS)
├── Spring Boot Application
│   ├── MessageController: /messages endpoints
│   ├── DataController: /data endpoints
│   └── Service on port 8080
└── API Test Job (curl)
    ├── Tests POST /messages
    ├── Tests GET /messages/search
    ├── Tests POST /data/seed
    └── Tests GET /data/count
```

---

## Quick Start

### For Local Testing (POC)

**Prerequisites:**
- OpenShift Local (CRC) running, or access to a test cluster
- `oc` CLI installed and in PATH
- 8GB+ RAM available

**1-minute setup:**

```bash
# Start OpenShift (if using CRC)
crc start
eval "$(crc oc-env)"

# Login
oc login

# Verify access
oc whoami

# Run the POC
./run-poc.sh
```

**That's it!** The POC will:
1. Create a test project
2. Deploy Elasticsearch
3. Build and deploy Spring Boot
4. Run API tests
5. Clean up automatically

### For Office/Production Deployment

See **[OFFICE_ENV_SETUP.md](OFFICE_ENV_SETUP.md)** for complete security and storage configuration.

**Quick version:**

```bash
# 1. Create credentials
oc create secret generic elasticsearch-credentials \
  --from-literal=username=elastic \
  --from-literal=password=$(openssl rand -base64 32)

# 2. Deploy secure stack
oc apply -f openshift/elasticsearch-secure.yaml
oc apply -f openshift/app-secure.yaml

# 3. Build application
oc new-build --name=poc-app --binary --strategy=docker
oc start-build poc-app --from-dir=. --follow --wait
```

---

## Prerequisites

| Requirement | Version | Purpose |
|-------------|---------|---------|
| OpenShift | 4.10+ | Container orchestration |
| oc CLI | 4.10+ | Cluster access |
| Java | 17+ | Build/develop locally |
| Maven | 3.8+ | Build management |
| Docker | 20.10+ | Local image building (optional) |

### Setup Steps

**For CRC (Local Development):**

```bash
# One-time setup
crc setup
crc start

# Every session
eval "$(crc oc-env)"
oc login -u kubeadmin -p $(crc console --credentials | grep password | awk '{print $NF}')

# Verify
oc whoami  # Should return: system:serviceaccount:openshift-infra:crc
```

**For Remote Cluster:**

```bash
# Get token from cluster admin
oc login https://api.your-cluster.com:6443 --token=<token>

# Verify
oc whoami
oc cluster-info
```

---

## Running Locally

### Option 1: Simple Run (Default)

```bash
chmod +x run-poc.sh
./run-poc.sh
```

Default behavior:
- Creates project: `test-poc`
- Keeps Elasticsearch for 5 minutes for testing
- Cleans up on exit
- Prints all logs to terminal

### Option 2: Keep Infrastructure

```bash
KEEP_PROJECT=true ./run-poc.sh
```

Useful for:
- Manual testing
- Integration debugging
- Running the test again without rebuilding

Infrastructure remains:
- ✅ Elasticsearch
- ✅ Spring Boot deployment
- ✅ Docker build artifacts
- ❌ API test job (always cleaned up)

### Option 3: Existing Project

```bash
PROJECT=my-project ./run-poc.sh
```

Use when:
- Your account can't create projects
- Testing in a pre-configured namespace
- Multiple team members share the same project

### Option 4: Custom Configuration

```bash
PROJECT=my-project \
KEEP_PROJECT=true \
ARTIFACT_DIR=/tmp/logs \
./run-poc.sh
```

Environment variables:
- `PROJECT`: Project name (default: `test-poc`)
- `KEEP_PROJECT`: Keep infrastructure after test (default: `false`)
- `ARTIFACT_DIR`: Save logs and diagnostics here (default: none)

---

## Testing the Application

### Local Port Forwarding

```bash
# Forward Spring Boot
oc port-forward svc/poc-app 8080:8080 &

# Forward Elasticsearch (if needed)
oc port-forward svc/elasticsearch 9200:9200 &
```

### API Endpoints

**Message Service:**
```bash
# Create a message
curl -X POST http://localhost:8080/messages \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello from OpenShift"}'

# Search messages
curl "http://localhost:8080/messages/search?q=OpenShift"
```

**Data Service:**
```bash
# Seed bulk data
curl -X POST http://localhost:8080/data/seed \
  -H "Content-Type: application/json" \
  -d '{"items":["Item1","Item2","Item3"]}'

# Get data count
curl http://localhost:8080/data/count
```

**Health Check:**
```bash
curl http://localhost:8080/actuator/health
```

---

## CI/CD Integration

### GitHub Actions (Automatic on Push)

**What triggers it:**
- ✅ Push to `main` branch
- ✅ Push to `feature/*` branches
- ✅ Pull requests to `main`
- ✅ Manual trigger via Actions tab

**Setup:**

1. Go to **Settings → Secrets and variables → Actions**
2. Create repository secrets:
   ```
   OPENSHIFT_TOKEN = <service-account-token>
   ```
3. Create repository variables:
   ```
   OPENSHIFT_SERVER = https://api.your-cluster.com:6443
   OPENSHIFT_PROJECT = existing-project-name
   OPENSHIFT_INSECURE_SKIP_TLS_VERIFY = true  (optional)
   ```

**View results:**
- Go to **Actions** tab → Click workflow run → View logs
- Artifacts saved for 7 days in "Artifacts" section

### GitLab CI (Automatic on Push)

**What triggers it:**
- ✅ Push to any branch
- ✅ Push to merge requests
- ✅ Manual trigger via pipeline

**Setup:**

1. Go to **Settings → CI/CD → Variables**
2. Create protected variables:
   ```
   OPENSHIFT_SERVER = https://api.your-cluster.com:6443
   OPENSHIFT_TOKEN = <service-account-token>
   OPENSHIFT_PROJECT = existing-project-name
   OPENSHIFT_LOGIN_ARGS = --insecure-skip-tls-verify=true  (optional)
   ```

**View results:**
- Go to **CI/CD → Pipelines** → Click pipeline → View logs
- Artifacts saved for 7 days

---

## Application Architecture

### Spring Boot Services

**MessageService** (`src/main/java/com/example/poc/MessageService.java`)
- Creates messages in Elasticsearch index `messages`
- Searches messages by query
- Uses Elasticsearch REST API

**DataService** (`src/main/java/com/example/poc/DataService.java`)
- Bulk ingestion of data items
- Returns count of indexed items
- Supports custom item lists or defaults

**Controllers** (`MessageController.java`, `DataController.java`)
- REST endpoints for message operations
- Input validation
- Error handling

### Elasticsearch Configuration

**POC Version** (`openshift/elasticsearch.yaml`)
- Single node (no cluster)
- No authentication
- Ephemeral storage
- 512MB-2GB heap

**Secure Version** (`openshift/elasticsearch-secure.yaml`)
- Single node (extensible to cluster)
- Username/password authentication
- 50GB persistent storage
- 4GB-8GB heap
- HTTPS/TLS ready
- Prometheus monitoring support

---

## File Structure

```
openshift-elasticsearch-poc/
├── README.md                           # This file
├── OFFICE_ENV_SETUP.md                # Production deployment guide
├── run-poc.sh                         # Local orchestration script
├── Dockerfile                         # Spring Boot image build
├── pom.xml                            # Maven configuration
│
├── openshift/
│   ├── elasticsearch.yaml             # POC Elasticsearch config
│   ├── elasticsearch-secure.yaml      # Production Elasticsearch config
│   ├── app.yaml                       # POC Spring Boot config
│   ├── app-secure.yaml                # Production Spring Boot config
│   └── api-test-job.yaml              # API test container
│
├── src/main/java/com/example/poc/
│   ├── Application.java               # Spring Boot entry point
│   ├── MessageController.java         # Message REST endpoints
│   ├── MessageService.java            # Message business logic
│   ├── DataController.java            # Data REST endpoints
│   └── DataService.java               # Data ingestion logic
│
├── src/main/resources/
│   └── application.properties         # Configuration
│
├── src/test/java/
│   └── ElasticsearchIT.java          # Integration tests
│
└── .github/workflows/
    └── openshift-poc.yml              # GitHub Actions pipeline
```

---

## Environment Variables

### Application Configuration

| Variable | Default | Example | Purpose |
|----------|---------|---------|---------|
| `ELASTICSEARCH_URL` | `http://localhost:9200` | `https://elasticsearch:9200` | Elasticsearch endpoint |
| `ELASTICSEARCH_USERNAME` | (empty) | `elastic` | Auth username |
| `ELASTICSEARCH_PASSWORD` | (empty) | `SecurePass123!` | Auth password |

### POC Script Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `PROJECT` | `test-poc` | OpenShift project name |
| `KEEP_PROJECT` | `false` | Keep resources after test |
| `ARTIFACT_DIR` | (none) | Directory for logs |

---

## Troubleshooting

### Pod Won't Start

```bash
# Check pod status
oc get pods -l app=poc-elasticsearch
oc get pods -l app=poc-app

# Describe the pod for events
oc describe pod -l app=poc-elasticsearch

# Check logs
oc logs deployment/poc-elasticsearch --tail=50
```

### Connection Refused

```bash
# Verify service is running
oc get svc elasticsearch

# Test from another pod
oc run -it test --image=curlimages/curl --restart=Never -- \
  curl http://elasticsearch:9200/

# Port forward and test locally
oc port-forward svc/elasticsearch 9200:9200 &
curl http://localhost:9200/
```

### Elasticsearch Takes Too Long to Start

```bash
# Check resource requests
oc describe node

# Check pod resource usage
oc top pod -l app=poc-elasticsearch

# Increase timeout in run-poc.sh
# Default is 180s, try 240s or 300s
oc rollout status deployment/poc-elasticsearch --timeout=300s
```

### Spring Boot Can't Connect to Elasticsearch

```bash
# Check Spring Boot logs
oc logs deployment/poc-app

# Verify Elasticsearch is running and healthy
oc exec deployment/poc-elasticsearch -- \
  curl -s http://localhost:9200/_cluster/health | jq .

# Check network policies (if enabled)
oc get networkpolicies
```

### Test Job Fails

```bash
# Check test job logs
oc logs job/api-test

# Verify both services are running
oc get svc

# Test manually
oc port-forward svc/poc-app 8080:8080 &
curl http://localhost:8080/messages/search?q=test
```

---

## Performance Tips

### For Large Datasets

**Increase Elasticsearch heap:**
```yaml
# In elasticsearch-secure.yaml
- name: ES_JAVA_OPTS
  value: "-Xms4g -Xmx4g"  # Adjust as needed

# Increase memory limits
resources:
  limits:
    memory: 16Gi
```

**Adjust Spring Boot resources:**
```yaml
# In app-secure.yaml
resources:
  limits:
    memory: 4Gi
```

### For Faster Startup

```bash
# Pre-warm resources
oc apply -f openshift/elasticsearch-secure.yaml
# Wait for it to be ready before deploying app
oc wait --for=condition=ready pod -l app=poc-elasticsearch --timeout=300s
```

---

## Security Considerations

### POC Version ⚠️
- **NO authentication** - Anyone can access
- **NO TLS** - Data in plaintext
- **NO persistence** - Data lost on restart
- **For testing only** - Do not use in production

### Secure Version ✅
- **Username/password** - Elasticsearch authentication
- **HTTPS/TLS** - Encrypted connections
- **Persistent storage** - 50GB PVC
- **Secret management** - Credentials in OpenShift Secrets
- **Production ready** - Can be hardened further

For production, also:
1. Use proper CA-signed certificates (not self-signed)
2. Enable network policies to restrict traffic
3. Set resource quotas per namespace
4. Enable audit logging
5. Implement backup/snapshot strategy
6. Use RBAC to limit pod permissions

See [OFFICE_ENV_SETUP.md](OFFICE_ENV_SETUP.md) for complete production checklist.

---

## Contributing

This POC is designed to be:
- **Simple**: ~500 lines of code, easy to understand
- **Portable**: Run anywhere with OpenShift
- **Extensible**: Add more services as needed

To extend:
1. Add new service in `src/main/java/com/example/poc/`
2. Add new controller for REST endpoints
3. Update `openshift/*.yaml` with new deployments
4. Update tests in `openshift/api-test-job.yaml`

---

## Support & Resources

### Documentation
- [OpenShift Documentation](https://docs.openshift.com)
- [Elasticsearch Documentation](https://www.elastic.co/guide/index.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

### Troubleshooting Guides
- See `OFFICE_ENV_SETUP.md` for production setup
- Run `./run-poc.sh` and check `artifacts/` for diagnostics

### Test the POC
The POC includes:
- ✅ Unit tests (Spring Boot)
- ✅ Integration tests (Elasticsearch connectivity)
- ✅ Black-box API tests (End-to-end validation)

Run tests locally:
```bash
mvn clean test      # Unit tests only
mvn clean package   # Full test suite
```

---

## License

This POC is provided as-is for demonstration and educational purposes.

---

**Last Updated**: 2026-08-24  
**Elasticsearch Version**: 8.15.0  
**Java Version**: 17+ (Spring Boot 3.x)  
**OpenShift Version**: 4.10+
