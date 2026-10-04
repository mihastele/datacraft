/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

/** Plain launcher supports the non-JPMS JavaFX application distribution. */
public final class Launcher {
    private Launcher() { }
    public static void main(String[] arguments) {
        javafx.application.Application.launch(DataCraftApp.class, arguments);
    }
}
