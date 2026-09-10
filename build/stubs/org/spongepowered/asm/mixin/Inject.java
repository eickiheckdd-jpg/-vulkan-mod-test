package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    String method() default "";
    At at() default @At("HEAD");
}
