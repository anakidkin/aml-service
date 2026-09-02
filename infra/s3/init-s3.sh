#!/bin/sh
echo "=== Initializing LocalStack S3 Bucket & Lifecycle Rules ==="

curl -s -X PUT "http://localhost:4566/aml-audit-logs"

curl -s -X PUT "http://localhost:4566/aml-audit-logs?lifecycle" \
  -H "Content-Type: application/xml" \
  --data-binary '<LifecycleConfiguration><Rule><ID>MoveToGlacierAfter30Days</ID><Status>Enabled</Status><Filter><Prefix></Prefix></Filter><Transition><Days>30</Days><StorageClass>GLACIER</StorageClass></Transition></Rule></LifecycleConfiguration>'

echo ""
echo "=== S3 Bucket 'aml-audit-logs' created with Glacier lifecycle policy ==="
