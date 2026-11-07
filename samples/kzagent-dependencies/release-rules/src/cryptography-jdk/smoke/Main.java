package smoke;

import dev.whyoleg.cryptography.algorithms.SHA256;
import dev.whyoleg.cryptography.providers.jdk.JdkCryptographyProvider;
import java.security.MessageDigest;
import java.util.Arrays;

public final class Main {
    public static void main(String[] args) throws Exception {
        Probe.renamed();
        var provider = new JdkCryptographyProvider(null);
        byte[] input = {1, 2, 3};
        var digest = provider.getOrNull(SHA256.INSTANCE).hasher().hashBlocking(input);
        Probe.require(Arrays.equals(digest, MessageDigest.getInstance("SHA-256").digest(input)),
                "JDK crypto provider produced an incorrect SHA-256 hash");
    }
}
