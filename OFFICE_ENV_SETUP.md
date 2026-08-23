# Office Environment Setup

This guide explains how to use the secure versions of the POC in your office/production OpenShift environment.

## What Changed?

### Security Enhancements
- ✅ Elasticsearch authentication enabled (username/password)
- ✅ HTTPS/TLS support for Elasticsearch connections
- ✅ Spring Boot uses Basic Auth to connect securely
- ✅ Credentials stored in OpenShift Secrets

### Storage & Resources
- ✅ Persistent storage for Elasticsearch data (50GB PVC)
- ✅ Increased resource allocations:
  - Elasticsearch: 4Gi-8Gi (was 512Mi-2Gi)
  - Spring Boot: 512Mi-1Gi (was 256Mi-768Mi)
- ✅ Enhanced health checks (liveness + readiness + startup probes)
- ✅ Optional Prometheus monitoring support

## Files

| File | Purpose |
|------|---------|
| `openshift/elasticsearch-secure.yaml` | Elasticsearch with security & persistent storage |
| `openshift/app-secure.yaml` | Spring Boot with credential support |
| `src/main/java/com/example/poc/MessageService.java` | Updated with Basic Auth |
| `src/main/java/com/example/poc/DataService.java` | Updated with Basic Auth |
| `src/main/resources/application.properties` | Updated with credential configuration |

## Quick Start for Office Environment

### Step 1: Create Elasticsearch Credentials Secret

```bash
# Create the secure configuration
oc create secret generic elasticsearch-credentials \
  --from-literal=username=elastic \
  --from-literal=password=$(openssl rand -base64 32) \
  -n your-project
```

### Step 2: Create Certificates (Optional but Recommended)

For production, use proper certificates. For testing with self-signed:

```bash
# Generate self-signed certificate
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /tmp/server.key \
  -out /tmp/server.crt \
  -subj "/C=US/ST=State/L=City/O=Company/CN=elasticsearch"

# Create certificate secret
oc create secret tls elasticsearch-certs \
  --cert=/tmp/server.crt \
  --key=/tmp/server.key \
  -n your-project
```

### Step 3: Deploy Elasticsearch (Secure)

```bash
# Apply the secure configuration
oc apply -f openshift/elasticsearch-secure.yaml

# Wait for Elasticsearch to be ready
oc rollout status deployment/poc-elasticsearch --timeout=300s

# Verify it's healthy
oc logs deployment/poc-elasticsearch | tail -20
```

### Step 4: Build & Deploy Spring Boot

```bash
# Build the application image
oc new-build --name=poc-app --binary --strategy=docker 2>/dev/null || true
oc start-build poc-app --from-dir=. --follow --wait

# Get the internal image URL
IMAGE_URL="image-registry.openshift-image-registry.svc:5000/$(oc project -q)/poc-app:latest"

# Deploy with secure configuration
sed "s|image: poc-app:latest|image: $IMAGE_URL|" openshift/app-secure.yaml | oc apply -f -

# Wait for Spring Boot to be ready
oc rollout status deployment/poc-app --timeout=300s
```

### Step 5: Test the Application

```bash
# Test with authentication
ELASTIC_PASSWORD=$(oc get secret elasticsearch-credentials -o jsonpath='{.data.password}' | base64 -d)

# Get the Spring Boot pod
POD=$(oc get pods -l app=poc-app -o jsonpath='{.items[0].metadata.name}')

# Forward port to test locally
oc port-forward $POD 8080:8080 &

# Test creating a message
curl -X POST http://localhost:8080/messages \
  -H "Content-Type: application/json" \
  -d '{"message":"Test from office"}'

# Test searching
curl "http://localhost:8080/messages/search?q=office"

# Test data seeding
curl -X POST http://localhost:8080/data/seed \
  -H "Content-Type: application/json" \
  -d '{"items":["Office Item 1","Office Item 2"]}'

# Test data count
curl http://localhost:8080/data/count
```

## Important Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ELASTICSEARCH_URL` | `https://elasticsearch:9200` | Elasticsearch endpoint |
| `ELASTICSEARCH_USERNAME` | `elastic` | Elasticsearch username |
| `ELASTICSEARCH_PASSWORD` | (from secret) | Elasticsearch password |
| `ELASTICSEARCH_VERIFY_SSL` | `false` | SSL verification (use `true` with proper CA) |

### Storage Configuration

By default, the PVC uses `standard` storage class. Update if needed:

```bash
# List available storage classes
oc get storageclass

# Edit elasticsearch-secure.yaml and change storageClassName
```

## Monitoring (Optional)

The secure configurations include ServiceMonitor resources for Prometheus:

```bash
# Install Prometheus Operator (if not already installed)
oc apply -f https://github.com/prometheus-operator/prometheus-operator/releases/download/v0.68.0/bundle.yaml

# ServiceMonitors will be auto-discovered
oc get servicemonitor
```

## Backup & Recovery

### Manual Backup

```bash
# Create Elasticsearch snapshot
oc exec deployment/poc-elasticsearch -- \
  curl -k -u elastic:$ELASTIC_PASSWORD https://localhost:9200/_snapshot/backup/snapshot-001 \
  -X PUT -H "Content-Type: application/json" \
  -d '{"type":"fs","settings":{"location":"/backup"}}'
```

### Restore from Backup

```bash
# List available snapshots
oc exec deployment/poc-elasticsearch -- \
  curl -k -u elastic:$ELASTIC_PASSWORD https://localhost:9200/_snapshot/backup/_all

# Restore a snapshot
oc exec deployment/poc-elasticsearch -- \
  curl -k -u elastic:$ELASTIC_PASSWORD https://localhost:9200/_snapshot/backup/snapshot-001/_restore \
  -X POST
```

## Troubleshooting

### Check Elasticsearch is running

```bash
# Pod status
oc get pods -l app=poc-elasticsearch

# Pod logs
oc logs deployment/poc-elasticsearch

# Describe pod
oc describe pod -l app=poc-elasticsearch
```

### Test Elasticsearch connectivity

```bash
# Get credentials
USERNAME=$(oc get secret elasticsearch-credentials -o jsonpath='{.data.username}' | base64 -d)
PASSWORD=$(oc get secret elasticsearch-credentials -o jsonpath='{.data.password}' | base64 -d)

# Port forward
oc port-forward svc/elasticsearch 9200:9200 &

# Test with credentials
curl -k -u $USERNAME:$PASSWORD https://localhost:9200/

# Check cluster status
curl -k -u $USERNAME:$PASSWORD https://localhost:9200/_cluster/health
```

### Spring Boot connection issues

```bash
# Check Spring Boot logs
oc logs deployment/poc-app

# Check environment variables
oc set env deployment/poc-app --list

# Verify secret is mounted
oc describe pod -l app=poc-app | grep -A 5 "Environment:"
```

## Security Best Practices

1. **Change Default Passwords**: Update the Elasticsearch password in the credentials secret
2. **Use Proper Certificates**: Replace self-signed certs with certificates from your CA
3. **Network Policies**: Restrict traffic between pods and namespaces
4. **RBAC**: Limit ServiceAccount permissions
5. **Audit Logging**: Enable Elasticsearch audit logs
6. **Regular Backups**: Implement automated snapshot strategy
7. **Update Images**: Keep Elasticsearch and Java image versions current

## Upgrading from POC to Secure Configuration

If you're running the original POC and want to migrate to the secure version:

```bash
# Backup existing data
oc exec deployment/poc-elasticsearch -- \
  curl http://localhost:9200/_all/_search > /tmp/backup.json

# Delete old deployment
oc delete deployment poc-elasticsearch

# Deploy secure version
oc apply -f openshift/elasticsearch-secure.yaml

# Wait for new pod
oc rollout status deployment/poc-elasticsearch

# Restore data (adjust indices as needed)
# Use Elasticsearch migration tools for large datasets
```

## Support

For issues or questions:
1. Check the logs: `oc logs deployment/poc-elasticsearch`
2. Review troubleshooting section above
3. Check OpenShift documentation for your cluster version
4. Verify network connectivity between pods

---

**Last Updated**: 2026-08-24  
**Elasticsearch Version**: 8.15.0  
**Java Version**: 17+ (with Spring Boot 3.x)
