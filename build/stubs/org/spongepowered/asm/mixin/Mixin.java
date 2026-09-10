package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mixin {
    String[] value() default {};
    String[] targets() default {};
}
