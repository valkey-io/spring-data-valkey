#!/bin/bash
set -e

# Maven Central Direct Portal API Deployment Script for spring-boot-starter-data-valkey
# Based on valkey-glide's approach

VERSION="0.2.0"
GROUP_PATH="io/valkey/springframework/boot"
ARTIFACT_ID="spring-boot-starter-data-valkey"
MODULE_DIR="spring-boot-starter-data-valkey"

echo "==========================================="
echo "Maven Central Deployment Script"
echo "Module: ${ARTIFACT_ID}"
echo "Version: ${VERSION}"
echo "==========================================="

# Step 1: Clean and build with signing
echo ""
echo "Step 1: Building artifacts with GPG signing..."
export GPG_KEYNAME=$(cat pgp_key_id)
export GPG_PASSPHRASE=$(cat pgp_key_pass)

mvn clean install -DskipTests -pl ${MODULE_DIR} -am

# Check if artifacts exist
if [ ! -f "${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.jar" ]; then
    echo "ERROR: Main jar not found!"
    exit 1
fi

echo "✓ Build successful - artifacts created"

# Step 2: Generate checksums
echo ""
echo "Step 2: Generating checksums..."
cd ${MODULE_DIR}/target
for file in ${ARTIFACT_ID}-${VERSION}*.jar ${ARTIFACT_ID}-${VERSION}.pom; do
    if [ -f "$file" ]; then
        md5sum $file | cut -d ' ' -f 1 > $file.md5
        sha1sum $file | cut -d ' ' -f 1 > $file.sha1
        echo "  ✓ Generated checksums for $file"
    fi
done
cd ../..

echo "✓ Checksums generated"

# Step 3: Create bundle directory structure
echo ""
echo "Step 3: Creating bundle directory structure..."
rm -rf bundle
mkdir -p bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}

# Copy all artifacts to bundle
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.jar bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.jar.asc bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.pom bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.pom.asc bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}-sources.jar bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}-sources.jar.asc bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}-javadoc.jar bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}-javadoc.jar.asc bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/

# Copy checksums
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.jar.md5 bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.jar.sha1 bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.pom.md5 bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}.pom.sha1 bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}-sources.jar.md5 bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}-sources.jar.sha1 bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}-javadoc.jar.md5 bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/
cp ${MODULE_DIR}/target/${ARTIFACT_ID}-${VERSION}-javadoc.jar.sha1 bundle/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/

echo "✓ Bundle directory created"

# Step 4: Create zip bundle
echo ""
echo "Step 4: Creating zip bundle..."
cd bundle
zip -r ../bundle-spring-boot-starter.zip .
cd ..

echo "✓ Bundle zip created: bundle-spring-boot-starter.zip"

# Step 5: Upload to Central Portal
echo ""
echo "Step 5: Uploading to Maven Central Portal..."

CENTRAL_USERNAME=$(cat central_user_name)
CENTRAL_PASSWORD=$(cat central_user_pass)

DEPLOYMENT_RESPONSE=$(curl --request POST \
  -u "${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}" \
  --form bundle=@bundle-spring-boot-starter.zip \
  https://central.sonatype.com/api/v1/publisher/upload)

DEPLOYMENT_ID=$(echo "$DEPLOYMENT_RESPONSE" | tail -n 1)

echo "✓ Uploaded to Maven Central"
echo "Deployment ID: $DEPLOYMENT_ID"

# Step 6: Check deployment status
echo ""
echo "Step 6: Checking deployment status..."
echo "Waiting for validation..."

for i in {1..20}; do
    sleep 5
    
    STATUS_RESPONSE=$(curl --request POST \
      -u "${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}" \
      "https://central.sonatype.com/api/v1/publisher/status?id=${DEPLOYMENT_ID}")
    
    DEPLOYMENT_STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.deploymentState')
    
    echo "  Status check $i: $DEPLOYMENT_STATUS"
    
    if [ "$DEPLOYMENT_STATUS" = "VALIDATED" ]; then
        echo ""
        echo "✓ Deployment validated successfully!"
        echo ""
        echo "==========================================="
        echo "DEPLOYMENT READY - ${ARTIFACT_ID}"
        echo "==========================================="
        echo "Deployment ID: $DEPLOYMENT_ID"
        echo ""
        echo "Next steps:"
        echo "1. To publish, run:"
        echo "   curl --request POST -u \"${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}\" \\"
        echo "     \"https://central.sonatype.com/api/v1/publisher/deployment/${DEPLOYMENT_ID}\""
        echo ""
        exit 0
    elif [ "$DEPLOYMENT_STATUS" = "FAILED" ]; then
        echo ""
        echo "ERROR: Deployment validation failed!"
        echo "$STATUS_RESPONSE" | jq '.'
        exit 1
    fi
done

echo ""
echo "Deployment validation taking longer than expected."
echo "Check status manually at: https://central.sonatype.com/publishing/deployments"
echo "Deployment ID: $DEPLOYMENT_ID"
