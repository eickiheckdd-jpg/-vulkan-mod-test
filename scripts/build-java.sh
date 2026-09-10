#!/bin/bash
# build-java.sh - Compiles VulkanMod using javac directly
# This is a fallback build script when fabric-loom cannot resolve dependencies

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
SRC_DIR="$PROJECT_DIR/src/main/java"
RES_DIR="$PROJECT_DIR/src/main/resources"
OUT_DIR="$PROJECT_DIR/build/classes"
LIB_DIR="$PROJECT_DIR/lib"
STUBS_DIR="$PROJECT_DIR/build/stubs"

echo "=== VulkanMod Build Script ==="
echo "Project: $PROJECT_DIR"
echo "Source: $SRC_DIR"
echo "Output: $OUT_DIR"

# Create output directories
mkdir -p "$OUT_DIR"
mkdir -p "$STUBS_DIR"

# Create stub classes for external dependencies
mkdir -p "$STUBS_DIR/net/fabricmc/api"
mkdir -p "$STUBS_DIR/net/fabricmc/loader/api"
mkdir -p "$STUBS_DIR/org/slf4j"
mkdir -p "$STUBS_DIR/net/minecraft/client"
mkdir -p "$STUBS_DIR/org/spongepowered/asm/mixin"
mkdir -p "$STUBS_DIR/org/spongepowered/asm/mixin/injection"

cat > "$STUBS_DIR/net/fabricmc/api/ClientModInitializer.java" << 'JAVAEOF'
package net.fabricmc.api;
public interface ClientModInitializer {
    void onInitializeClient();
}
JAVAEOF

cat > "$STUBS_DIR/net/fabricmc/loader/api/FabricLoader.java" << 'JAVAEOF'
package net.fabricmc.loader.api;
public class FabricLoader {
    public static FabricLoader getInstance() { return null; }
    public java.nio.file.Path getConfigDir() { return null; }
}
JAVAEOF

cat > "$STUBS_DIR/org/slf4j/Logger.java" << 'JAVAEOF'
package org.slf4j;
public interface Logger {
    void info(String msg);
    void info(String format, Object... args);
    void warn(String msg);
    void warn(String format, Object... args);
    void error(String msg);
    void error(String format, Object... args);
}
JAVAEOF

cat > "$STUBS_DIR/org/slf4j/LoggerFactory.java" << 'JAVAEOF'
package org.slf4j;
public class LoggerFactory {
    public static Logger getLogger(String name) { return null; }
}
JAVAEOF

cat > "$STUBS_DIR/net/minecraft/client/Minecraft.java" << 'JAVAEOF'
package net.minecraft.client;
public class Minecraft {
    public static Minecraft getInstance() { return null; }
    public Window getWindow() { return null; }
}
JAVAEOF

cat > "$STUBS_DIR/net/minecraft/client/Window.java" << 'JAVAEOF'
package net.minecraft.client;
public class Window {
    public long getHandle() { return 0; }
    public void update() {}
}
JAVAEOF

cat > "$STUBS_DIR/org/spongepowered/asm/mixin/Mixin.java" << 'JAVAEOF'
package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mixin {
    String[] value() default {};
    String[] targets() default {};
}
JAVAEOF

cat > "$STUBS_DIR/org/spongepowered/asm/mixin/Inject.java" << 'JAVAEOF'
package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    String method() default "";
    At at() default @At("HEAD");
}
JAVAEOF

cat > "$STUBS_DIR/org/spongepowered/asm/mixin/At.java" << 'JAVAEOF'
package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface At {
    String value() default "HEAD";
}
JAVAEOF

cat > "$STUBS_DIR/org/spongepowered/asm/mixin/injection/CallbackInfo.java" << 'JAVAEOF'
package org.spongepowered.asm.mixin.injection;
public class CallbackInfo {
    public CallbackInfo(String s) {}
}
JAVAEOF

# Compile stubs first
echo "Compiling stub classes..."
javac --release 21 -d "$STUBS_DIR" $(find "$STUBS_DIR" -name "*.java")

# Compile main sources
echo "Compiling main sources..."
CLASSES=$(find "$SRC_DIR" -name "*.java")
CP="$STUBS_DIR"

# Try to find LWJGL jars
if [ -f "$LIB_DIR/lwjgl-vulkan.jar" ]; then
    CP="$CP:$LIB_DIR/lwjgl-vulkan.jar"
fi
if [ -f "$LIB_DIR/lwjgl-glfw.jar" ]; then
    CP="$CP:$LIB_DIR/lwjgl-glfw.jar"
fi
if [ -f "$LIB_DIR/lwjgl.jar" ]; then
    CP="$CP:$LIB_DIR/lwjgl.jar"
fi

javac --release 21 -d "$OUT_DIR" -cp "$CP" $CLASSES 2>&1 || true

echo ""
echo "=== Build Summary ==="
echo "Output directory: $OUT_DIR"
echo "Classes compiled: $(find "$OUT_DIR" -name "*.class" | wc -l)"
echo ""
echo "To create a JAR manually:"
echo "  cd $OUT_DIR && jar cvf $PROJECT_DIR/build/vulkanmod.jar ."
