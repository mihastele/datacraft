/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.platform;

import java.util.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsCredentialStoreTest {
    @Test void realNativeUnicodeWriteReadReplacementAndDeleteAreScopedToRandomId() throws Exception {
        var store = new WindowsCredentialStore(); var id = UUID.randomUUID();
        char[] password = (UUID.randomUUID() + "ü🙂").toCharArray();
        char[] replacement = UUID.randomUUID().toString().toCharArray();
        try {
            assertTrue(store.available()); assertTrue(store.read(id).isEmpty());
            store.write(id, password);
            var returned = store.read(id).orElseThrow();
            try { assertTrue(Arrays.equals(password, returned), "Native credential roundtrip mismatch"); }
            finally { Arrays.fill(returned, '\0'); }
            store.write(id, replacement);
            returned = new WindowsCredentialStore().read(id).orElseThrow();
            try { assertTrue(Arrays.equals(replacement, returned), "Replacement roundtrip mismatch"); }
            finally { Arrays.fill(returned, '\0'); }
            store.delete(id); assertTrue(store.read(id).isEmpty()); store.delete(id);
        } finally { store.delete(id); Arrays.fill(password, '\0'); Arrays.fill(replacement, '\0'); }
    }
    @Test void enforcesNativeBlobLimitWithoutCreatingACredential() throws Exception {
        var store = new WindowsCredentialStore(); var id = UUID.randomUUID();
        try {
            assertThrows(IOException.class, () -> store.write(id, new char[1281]));
            assertTrue(store.read(id).isEmpty());
        } finally { store.delete(id); }
    }
}
