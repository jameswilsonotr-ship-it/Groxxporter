#!/bin/bash
set -e

# 1. Get version from CHANGELOG.md
VERSION=$(grep -oE '\[v[0-9]+\.[0-9]+\.[0-9]+\]' CHANGELOG.md | head -n 1 | tr -d '[]')
if [ -z "$VERSION" ]; then
  VERSION="v1.3.0"
fi
echo "Detected version: $VERSION"

# 2. Create target info and infofe folders (to support both spellings gracefully)
mkdir -p info infofe

# 3. Define Zip path
ZIP_NAME="grok_extractor_${VERSION}_comprehensive.zip"
ZIP_PATH="info/$ZIP_NAME"
ZIP_PATH_INFOFE="infofe/$ZIP_NAME"
echo "Creating zip at: $ZIP_PATH and $ZIP_PATH_INFOFE"

# 4. Locate compiled APK
APK_PATH=".build-outputs/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
  echo "Warning: APK not found at default $APK_PATH. Searching..."
  FOUND_APK=$(find . -name "*.apk" -not -path "*/.*" | head -n 1)
  if [ -n "$FOUND_APK" ]; then
    APK_PATH="$FOUND_APK"
  fi
fi

if [ ! -f "$APK_PATH" ]; then
  echo "Error: No APK file found. Please run compile_applet first."
  exit 1
fi

echo "Found APK at: $APK_PATH"

# 5. Gather all markdown files in the workspace (excluding hidden or build dirs)
echo "Gathering markdown files..."
MD_FILES=$(find . -name "*.md" -not -path "*/.*" -not -path "*/build/*" -not -path "*/app/build/*" -not -path "*/gradle/*" | sed 's|^\./||')
echo "Markdown files to include:"
echo "$MD_FILES"

# 6. Create temporary workspace to assemble zip
TEMP_DIR=$(mktemp -d)
echo "Using temp directory: $TEMP_DIR"

# Copy markdown files with relative paths
for f in $MD_FILES; do
  mkdir -p "$TEMP_DIR/$(dirname "$f")"
  cp "$f" "$TEMP_DIR/$f"
done

# Copy APK to the root of the zip
cp "$APK_PATH" "$TEMP_DIR/app-debug.apk"

# Create a summary info file inside the zip as well
cat <<EOF > "$TEMP_DIR/release_info.txt"
Grok Export Extractor & Integrity Suite Release Archive
Version: $VERSION
Generated on: $(date)
Included assets:
- app-debug.apk (compiled binary)
- Markdown documents (docs, schemas, readmes, changelogs, roadmaps)
EOF

# Zip the contents using Python for cross-platform robustness
cd "$TEMP_DIR"
python3 -c "
import zipfile, os
with zipfile.ZipFile('$OLDPWD/$ZIP_PATH', 'w', zipfile.ZIP_DEFLATED) as zip_file:
    for root, dirs, files in os.walk('.'):
        for file in files:
            file_path = os.path.join(root, file)
            archive_name = os.path.relpath(file_path, '.')
            zip_file.write(file_path, archive_name)
"
cd "$OLDPWD"

# Copy the zip to the infofe directory as well
cp "$ZIP_PATH" "$ZIP_PATH_INFOFE"

# Cleanup temp
rm -rf "$TEMP_DIR"

echo "========================================================="
echo "Comprehensive ZIP successfully generated:"
echo "-> $ZIP_PATH"
echo "-> $ZIP_PATH_INFOFE"
echo "========================================================="
ls -lh "$ZIP_PATH" "$ZIP_PATH_INFOFE"
