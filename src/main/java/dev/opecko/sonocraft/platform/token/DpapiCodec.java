package dev.opecko.sonocraft.platform.token;

import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.charset.StandardCharsets;

/** Windows DPAPI with user scope: the file can only be decrypted by the same user. */
public final class DpapiCodec implements FileTokenStore.Codec {
    @Override
    public byte[] encode(String token) throws TokenStoreException {
        try {
            return Crypt32Util.cryptProtectData(token.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException | LinkageError e) {
            throw new TokenStoreException("DPAPI encryption failed", e);
        }
    }

    @Override
    public String decode(byte[] data) throws TokenStoreException {
        try {
            return new String(Crypt32Util.cryptUnprotectData(data), StandardCharsets.UTF_8).trim();
        } catch (RuntimeException | LinkageError e) {
            throw new TokenStoreException("DPAPI decryption failed", e);
        }
    }
}
