package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface At {
    String value();
}
