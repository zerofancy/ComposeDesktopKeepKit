# Adapted from DataStore preferences-core 1.1.7 consumer rules (Apache-2.0).
# The shaded Protobuf runtime resolves generated message fields from schema strings.
# Preserve field names and access while allowing unrelated classes to shrink/rename.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
