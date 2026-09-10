package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    At at();
    String method();
}
