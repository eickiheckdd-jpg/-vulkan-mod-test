#!/bin/bash
# Build script for VulkanMod - compiles the mod using javac directly
# This is used when Gradle/fabric-loom cannot resolve dependencies

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/main/java"
RES_DIR="$PROJECT_DIR/src/main/resources"
OUT_DIR="$PROJECT_DIR/build/classes"
LIB_DIR="$PROJECT_DIR/lib"

mkdir -p "$OUT_DIR"

# Find all Java source files
SOURCES=$(find "$SRC_DIR" -name "*.java")

echo "Compiling VulkanMod sources..."
echo "Source directory: $SRC_DIR"
echo "Output directory: $OUT_DIR"

# Compile with javac
javac --release 21 -d "$OUT_DIR" -cp "$LIB_DIR/*" $SOURCES 2>&1

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Classes output to: $OUT_DIR"
else
    echo "Compilation failed!"
    exit 1
fi
