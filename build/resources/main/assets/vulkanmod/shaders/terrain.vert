#version 450 core

layout(location = 0) in vec3 inPosition;

layout(push_constant) uniform PushConstants {
    mat4 mvpMatrix;
} push;

layout(location = 0) out vec3 fragPosition;

void main() {
    gl_Position = push.mvpMatrix * vec4(inPosition, 1.0);
    fragPosition = inPosition;
}
