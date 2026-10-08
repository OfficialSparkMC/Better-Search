package com.bettersearch;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Wurst-style search tags for modules.
 * Add extra keywords/synonyms so Better Search can find your module
 * even when the user types something different from the module name.
 *
 * <p>Example:
 * <pre>
 * {@code @SearchTags({"speedy-gonzales", "fast break", "haste"})}
 * public class FastBreak extends Module { ... }
 * </pre>
 *
 * @author OfficialSparkMC
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SearchTags {
    String[] value();
}
