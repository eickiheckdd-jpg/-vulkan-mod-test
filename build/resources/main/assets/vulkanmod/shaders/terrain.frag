#version 450 core

layout(location = 0) in vec3 fragPosition;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(0.5, 0.5, 0.5, 1.0);
}
