#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src/main/resources/assets/vulkanmod/shaders"

echo "Compiling SPIR-V shaders for Vulkan 1.1..."

if ! command -v glslangValidator &> /dev/null; then
    echo "glslangValidator not found. Attempting to install..."
    apt-get update -qq && apt-get install -y -qq spirv-tools glslang-tools 2>/dev/null || {
        echo "Cannot install glslangValidator. Please install spirv-tools manually."
        exit 1
    }
fi

cd "$SRC_DIR"

glslangValidator -V -o terrain.vert.spv terrain.vert || { echo "Failed to compile vertex shader"; exit 1; }
echo "Compiled terrain.vert -> terrain.vert.spv"

glslangValidator -V -o terrain.frag.spv terrain.frag || { echo "Failed to compile fragment shader"; exit 1; }
echo "Compiled terrain.frag -> terrain.frag.spv"

echo "All shaders compiled successfully!"
