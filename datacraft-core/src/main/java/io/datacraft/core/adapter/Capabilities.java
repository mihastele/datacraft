/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.adapter;

import java.util.Objects;
import java.util.Set;

/** Immutable declaration of optional database features; absence means unsupported. */
public record Capabilities(Set<Capability> supported) {
    /** Copies the supplied set and rejects null sets and null entries. */
    public Capabilities {
        supported = Set.copyOf(Objects.requireNonNull(supported, "supported"));
    }

    public static Capabilities none() {
        return new Capabilities(Set.of());
    }

    /** A null capability is a programming error, not an unsupported feature. */
    public boolean supports(Capability capability) {
        return supported.contains(Objects.requireNonNull(capability, "capability"));
    }
}
