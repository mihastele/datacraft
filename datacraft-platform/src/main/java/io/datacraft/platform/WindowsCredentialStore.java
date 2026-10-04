/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.platform;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import io.datacraft.core.persistence.CredentialStore;

/** Native Windows generic credentials, persisted only for the current local user. */
public final class WindowsCredentialStore implements CredentialStore {
    private static final int GENERIC = 1, LOCAL_MACHINE = 2, NOT_FOUND = 1168, MAX_BYTES = 2560;
    private final Api api = Native.load("Advapi32", Api.class);
    public interface Api extends StdCallLibrary {
        boolean CredWriteW(Credential credential, int flags);
        boolean CredReadW(WString target, int type, int flags, PointerByReference result);
        boolean CredDeleteW(WString target, int type, int flags);
        void CredFree(Pointer credential);
    }
    @Structure.FieldOrder({"low", "high"})
    public static class FileTime extends Structure { public int low, high; }
    @Structure.FieldOrder({"flags", "type", "target", "comment", "lastWritten", "blobSize", "blob", "persist", "attributeCount", "attributes", "alias", "username"})
    public static final class Credential extends Structure {
        public int flags, type;
        public WString target, comment;
        public FileTime lastWritten = new FileTime();
        public int blobSize;
        public Pointer blob;
        public int persist, attributeCount;
        public Pointer attributes;
        public WString alias, username;
        public Credential() { }
        public Credential(Pointer pointer) { super(pointer); read(); }
    }
    private static WString target(UUID id) { return new WString("DataCraft/connection/" + id); }
    @Override public boolean available() { return true; }
    @Override public Optional<char[]> read(UUID id) throws IOException {
        var pointer = new PointerByReference();
        if (!api.CredReadW(target(id), GENERIC, 0, pointer)) {
            if (Native.getLastError() == NOT_FOUND) return Optional.empty();
            throw unavailable();
        }
        Credential value = null;
        try {
            value = new Credential(pointer.getValue());
            if (value.blobSize < 0 || value.blobSize > MAX_BYTES || value.blobSize % 2 != 0)
                throw unavailable();
            var chars = new char[value.blobSize / 2];
            for (int i = 0; i < chars.length; i++) chars[i] = (char) value.blob.getShort(i * 2L);
            return Optional.of(chars);
        } finally {
            if (value != null && value.blob != null && value.blobSize >= 0 && value.blobSize <= MAX_BYTES)
                value.blob.clear(value.blobSize);
            api.CredFree(pointer.getValue());
        }
    }
    @Override public void write(UUID id, char[] password) throws IOException {
        if (password.length == 0 || password.length > MAX_BYTES / 2) throw new IOException("Password length is not supported by Windows Credential Manager.");
        try (var memory = new Memory(password.length * 2L)) {
            try {
                for (int i = 0; i < password.length; i++) memory.setShort(i * 2L, (short) password[i]);
                var value = new Credential(); value.type = GENERIC; value.target = target(id);
                value.blobSize = password.length * 2; value.blob = memory; value.persist = LOCAL_MACHINE;
                value.username = new WString("DataCraft");
                if (!api.CredWriteW(value, 0)) throw unavailable();
            } finally { memory.clear(); }
        }
    }
    @Override public void delete(UUID id) throws IOException {
        if (!api.CredDeleteW(target(id), GENERIC, 0) && Native.getLastError() != NOT_FOUND) throw unavailable();
    }
    private static IOException unavailable() { return new IOException("Windows Credential Manager could not complete the operation."); }
}
