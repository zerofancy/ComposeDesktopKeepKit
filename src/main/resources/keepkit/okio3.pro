# ProGuard return-type specialization across Okio's Kotlin multifile facades
# can produce VerifyError. Preserve only optimization contracts; shrinking and
# renaming remain enabled. Verified with Okio 3.15.0 / ProGuard 7.7.0.
-keep,allowshrinking,allowobfuscation class okio.Okio__* { *; }
